const {
  onDocumentWritten,
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

const ADMIN_UID = "QmYpTqwNGcXDem7R0nmdsDuxjxB3";
const NEARBY_CAMP_KM = 10;

// ---------- helpers ----------

async function push(uids, data, ttlMs = 60 * 60 * 1000) {
  const unique = [...new Set(uids)].filter(Boolean);
  if (!unique.length) return;

  const snaps = await db.getAll(...unique.map((u) => db.doc(`FcmTokens/${u}`)));
  const targets = snaps
    .filter((s) => s.exists && s.get("token"))
    .map((s) => ({ ref: s.ref, token: s.get("token") }));

  for (let i = 0; i < targets.length; i += 500) {
    const chunk = targets.slice(i, i + 500);
    const res = await getMessaging().sendEach(
      chunk.map((t) => ({
        token: t.token,
        data, // data-only → onMessageReceived always runs
        android: { priority: "high", ttl: ttlMs },
      }))
    );
    await Promise.all(
      res.responses.map((r, idx) =>
        !r.success && r.error?.code === "messaging/registration-token-not-registered"
          ? chunk[idx].ref.delete()
          : null
      )
    );
  }
}

function distanceKm(lat1, lon1, lat2, lon2) {
  const rad = (d) => (d * Math.PI) / 180;
  const a =
    Math.sin(rad(lat2 - lat1) / 2) ** 2 +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lon2 - lon1) / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// ---------- 1, 2, 3: BloodRequests ----------

exports.onBloodRequestWrite = onDocumentWritten("BloodRequests/{docId}", async (event) => {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.exists ? event.data.after.data() : null;
  if (!after) return;

  // 1. Request sent or resent → donor (10 s alert sound)
  if (
    after.status === "pending" &&
    (!before || before.createdAt?.toMillis() !== after.createdAt?.toMillis())
  ) {
    return push(
      [after.toUserId],
      {
        type: "blood_request",
        title: "Blood request",
        body: `${after.fromUserName || "A patient"} needs ${after.bloodGroup} blood`,
      },
      10 * 60 * 1000
    );
  }

  // 2. Donor accepted / declined → patient
  if (
    before?.status === "pending" &&
    (after.status === "accepted" || after.status === "declined")
  ) {
    const accepted = after.status === "accepted";
    return push([after.fromUserId], {
      type: "request_response",
      title: accepted ? "Request accepted" : "Request declined",
      body: accepted
        ? `${after.toUserName || "A donor"} accepted your request. You can now call them.`
        : `${after.toUserName || "A donor"} can't donate right now. Try another donor.`,
    });
  }

  // 3. Patient marked donated → donor
  if (before && !before.donated && after.donated) {
    return push([after.toUserId], {
      type: "donation_confirmed",
      title: "Donation recorded",
      body: `Your donation for ${after.fromUserName || "a patient"} was added to your history. Thank you!`,
    });
  }
});

// ---------- 4, 5: camp applications ----------

// 5. Donor registers for a camp → org
exports.onCampApplicationCreated = onDocumentCreated(
  "BloodCamps/{campId}/applications/{appId}",
  async (event) => {
    const app = event.data.data();
    const camp = (await db.doc(`BloodCamps/${event.params.campId}`).get()).data();
    if (!camp?.orgId) return;
    await push([camp.orgId], {
      type: "camp_application",
      title: "New camp registration",
      body: `${app.name || "A donor"} (${app.bloodGroup || "—"}) registered for ${camp.campName || "your camp"}`,
    });
  }
);

// 4. Org marks donated → donor
exports.onCampApplicationUpdated = onDocumentUpdated(
  "BloodCamps/{campId}/applications/{appId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (before.donated || !after.donated || !after.donorId) return;

    const camp = (await db.doc(`BloodCamps/${event.params.campId}`).get()).data();
    await push([after.donorId], {
      type: "donation_confirmed",
      title: "Donation confirmed",
      body: `Your donation at ${camp?.campName || "the camp"} was recorded. Thank you!`,
    });
  }
);

// ---------- 6: org registration → admin ----------

exports.onCampCreated = onDocumentCreated("BloodCamps/{campId}", async (event) => {
  const camp = event.data.data();
  if (camp.verificationStatus !== "pending") return;
  await push([ADMIN_UID], {
    type: "org_registration",
    title: "New organization registration",
    body: `${camp.ngoName || "An organization"} is waiting for review`,
  });
});

// ---------- 8: camp verified → donors within 10 km ----------

exports.onCampVerified = onDocumentUpdated("BloodCamps/{campId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.verificationStatus === "verified" || after.verificationStatus !== "verified") return;
  if (!after.latitude || !after.longitude) return;
  if (after.endTimeMillis && after.endTimeMillis < Date.now()) return;

  const donors = await db.collection("Users").where("profileCompleted", "==", true).get();
  const uids = [];
  donors.forEach((d) => {
    const u = d.data();
    if (d.id === after.orgId) return;
    if (typeof u.latitude !== "number" || typeof u.longitude !== "number") return;
    if (distanceKm(after.latitude, after.longitude, u.latitude, u.longitude) <= NEARBY_CAMP_KM) {
      uids.push(d.id);
    }
  });

  await push(uids, {
    type: "nearby_camp",
    title: "Blood camp near you",
    body: `${after.campName || "A blood camp"} by ${after.ngoName || "an organization"} on ${after.date || "soon"}`,
  });
});

// ---------- 7: admin reviewed org → org ----------

exports.onOrgReviewed = onDocumentUpdated("Users/{uid}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.verificationStatus === after.verificationStatus) return;

  if (after.verificationStatus === "verified") {
    await push([event.params.uid], {
      type: "org_review",
      title: "Registration reviewed",
      body: "Your organization was approved. Your camps are now visible to donors.",
    });
  } else if (after.verificationStatus === "rejected") {
    await push([event.params.uid], {
      type: "org_review",
      title: "Registration not approved",
      body: after.rejectionReason ? `Reason: ${after.rejectionReason}` : "Open the app for details.",
    });
  }
});

// ---------- 9: donation reminder (daily) ----------

exports.donationReminders = onSchedule(
  { schedule: "every day 10:00", timeZone: "Asia/Kolkata" },
  async () => {
    // Donors whose last donation was ~3 months ago (became eligible in the last 24 h)
    const end = new Date();
    end.setMonth(end.getMonth() - 3);
    const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);

    const snap = await db
      .collection("Users")
      .where("lastDonationDate", ">", Timestamp.fromDate(start))
      .where("lastDonationDate", "<=", Timestamp.fromDate(end))
      .get();

    await push(
      snap.docs.map((d) => d.id),
      {
        type: "donation_reminder",
        title: "You can donate again",
        body: "It's been 3 months since your last donation. Someone nearby may need you.",
      }
    );
  }
);
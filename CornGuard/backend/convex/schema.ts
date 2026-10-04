import { authTables } from "@convex-dev/auth/server";
import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

/**
 * CornGuard data model — the Convex equivalent of the former Firestore collections
 * (users, farms, diagnosisRecordsCloud, communityPosts + comments/votes subcollections,
 * deviceTokens, notifications, diseaseReferenceCloud). Every row carries Convex's
 * `_creationTime` (epoch ms), which replaces Firestore's `created_at`.
 */
export const moderationStatus = v.union(
  v.literal("visible"),
  v.literal("hidden"),
  v.literal("removed"),
);
export const verificationStatus = v.union(
  v.literal("unverified"),
  v.literal("verified"),
  v.literal("rejected"),
);
export const areaField = v.union(
  v.literal("barangay"),
  v.literal("municipality"),
  v.literal("province"),
);

export default defineSchema({
  ...authTables,

  // Extends Convex Auth's users table (same base fields + indexes) with the CornGuard profile.
  // A user without `name` has signed up but not yet created their profile.
  users: defineTable({
    name: v.optional(v.string()),
    image: v.optional(v.string()),
    email: v.optional(v.string()),
    emailVerificationTime: v.optional(v.number()),
    phone: v.optional(v.string()),
    phoneVerificationTime: v.optional(v.number()),
    isAnonymous: v.optional(v.boolean()),

    role: v.optional(v.union(v.literal("farmer"), v.literal("admin"))),
    accountStatus: v.optional(v.union(v.literal("active"), v.literal("suspended"))),
    barangay: v.optional(v.string()),
    municipality: v.optional(v.string()),
    province: v.optional(v.string()),
    mobileNumber: v.optional(v.string()),
  })
    .index("email", ["email"])
    .index("phone", ["phone"]),

  farms: defineTable({
    ownerId: v.id("users"),
    name: v.string(),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
    latitude: v.optional(v.number()),
    longitude: v.optional(v.number()),
  }).index("by_owner", ["ownerId"]),

  // Opt-in cloud copies of on-device scans.
  diagnosisRecords: defineTable({
    userId: v.id("users"),
    localId: v.string(),
    farmId: v.optional(v.id("farms")),
    diseaseCode: v.string(),
    confidence: v.number(),
    imageId: v.optional(v.id("_storage")),
    capturedAt: v.number(),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
    latitude: v.optional(v.number()),
    longitude: v.optional(v.number()),
    modelVersion: v.string(),
    verificationStatus,
    verifiedBy: v.optional(v.id("users")),
    verifiedAt: v.optional(v.number()),
    source: v.string(),
  })
    .index("by_user", ["userId"])
    .index("by_user_local", ["userId", "localId"])
    .index("by_status", ["verificationStatus"])
    .index("by_area_disease_status_time", [
      "barangay",
      "municipality",
      "diseaseCode",
      "verificationStatus",
      "capturedAt",
    ]),

  // Outbreak severity per barangay + disease over a rolling window (barangayStats.ts), ported
  // from caps 3. Keyed by barangay AND municipality: barangay names repeat across towns.
  barangayDiseaseStats: defineTable({
    barangay: v.string(),
    municipality: v.string(),
    diseaseCode: v.string(),
    windowStart: v.number(),
    windowEnd: v.number(),
    weightedScore: v.number(),
    distinctFarms: v.number(),
    rawReportCount: v.number(),
    severityTier: v.union(v.literal("mild"), v.literal("moderate"), v.literal("severe")),
    isActiveOutbreak: v.boolean(),
    outbreakDeclaredAt: v.optional(v.number()),
    lastUpdated: v.number(),
  }).index("by_area_disease", ["barangay", "municipality", "diseaseCode"]),

  // Outbreak alerts awaiting / after admin review (outbreakAlerts.ts). A barangay turning severe
  // creates a "pending" row; farmers are only notified once an admin approves it. Several diseases
  // turning severe in the same barangay share one pending row, so approval sends one combined alert.
  outbreaks: defineTable({
    barangay: v.string(),
    municipality: v.string(),
    diseaseCodes: v.array(v.string()),
    status: v.union(v.literal("pending"), v.literal("approved"), v.literal("dismissed")),
    source: v.union(v.literal("automatic"), v.literal("manual")),
    declaredAt: v.number(),
    message: v.optional(v.string()),   // custom text for a manual alert
    reviewedBy: v.optional(v.id("users")),
    reviewedAt: v.optional(v.number()),
    note: v.optional(v.string()),
    recipientsNotified: v.optional(v.number()),
  })
    .index("by_status", ["status"])
    .index("by_area_status", ["barangay", "municipality", "status"]),

  communityPosts: defineTable({
    userId: v.id("users"),
    linkedDiagnosisRecordId: v.optional(v.string()),
    title: v.string(),
    body: v.string(),
    diseaseTag: v.string(),
    imageId: v.optional(v.id("_storage")),
    barangay: v.string(),
    municipality: v.string(),
    province: v.string(),
    verificationStatus,
    verifiedBy: v.optional(v.id("users")),
    verifiedAt: v.optional(v.number()),
    moderationStatus,
    upvoteCount: v.number(),
  })
    .index("by_moderation", ["moderationStatus"])
    .index("by_moderation_barangay", ["moderationStatus", "barangay"])
    .index("by_moderation_municipality", ["moderationStatus", "municipality"])
    .index("by_moderation_province", ["moderationStatus", "province"])
    .index("by_user", ["userId"]),

  comments: defineTable({
    postId: v.id("communityPosts"),
    userId: v.id("users"),
    body: v.string(),
    moderationStatus,
  }).index("by_post", ["postId"]),

  postVotes: defineTable({
    postId: v.id("communityPosts"),
    userId: v.id("users"),
  })
    .index("by_post", ["postId"])
    .index("by_post_user", ["postId", "userId"]),

  deviceTokens: defineTable({
    userId: v.id("users"),
    deviceId: v.string(),
    fcmToken: v.string(),
    active: v.boolean(),
  })
    .index("by_user", ["userId"])
    .index("by_user_device", ["userId", "deviceId"]),

  // Written only by server code; delivered by push.sendNotification.
  notifications: defineTable({
    recipientUserId: v.optional(v.id("users")),
    areaScope: v.optional(v.string()),
    type: v.string(),
    title: v.string(),
    message: v.string(),
    relatedPostId: v.optional(v.string()),
    relatedRecordId: v.optional(v.string()),
    diseaseCode: v.optional(v.string()),
    deliveryStatus: v.union(v.literal("pending"), v.literal("sent"), v.literal("failed")),
    readAt: v.optional(v.number()),
  }).index("by_recipient", ["recipientUserId"]),

  // Admin-published reference content (the app itself reads its bundled Room copy offline).
  diseaseReferences: defineTable({
    diseaseCode: v.string(),
    displayName: v.string(),
    symptoms: v.string(),
    treatmentSteps: v.string(),
    preventionSteps: v.string(),
    causes: v.string(),
    duration: v.string(),
    sourceReference: v.string(),
    contentVersion: v.string(),
    publishedAt: v.number(),
  }).index("by_code", ["diseaseCode"]),
});

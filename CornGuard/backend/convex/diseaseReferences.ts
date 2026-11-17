import { ConvexError, v } from "convex/values";
import { Doc } from "./_generated/dataModel";
import { QueryCtx, internalMutation, mutation, query } from "./_generated/server";
import { requireAdmin } from "./lib/access";
import { guideReviewStatus } from "./schema";

/**
 * Treatment guides: one per class the model can detect, managed in the admin website
 * (admin-web Treatment Guides). The app subscribes to `list` and copies it into its Room cache,
 * which is what every screen reads — so guides stay readable offline and an admin's save reaches
 * open apps within seconds. Until a device has synced once it shows the bundled snapshot
 * (android/app/src/main/assets/treatment/guides-snapshot.json, refreshed at each release with
 * `node scripts/guides-snapshot.mjs export`).
 */

/** The classes the model outputs (android assets/labels.json). A guide for any other code would never show. */
export const GUIDE_CODES = ["northern_leaf_blight", "common_rust", "gray_leaf_spot", "healthy"] as const;

const MAX_FIELD_CHARS = 20_000;
const MAX_GUIDES = 50; // bounds `list`; GUIDE_CODES keeps the real count at 4

/** The guide's text. Required ones must be non-empty; the rest may be "" (the app hides empty sections). */
const content = v.object({
  displayName: v.string(),
  symptoms: v.string(),
  causes: v.string(),
  treatmentSteps: v.string(),
  duration: v.string(),
  preventionSteps: v.string(),
  sourceReference: v.string(),
});
type Content = typeof content.type;
const CONTENT_KEYS = Object.keys(content.fields) as (keyof Content)[];
const REQUIRED: (keyof Content)[] = ["displayName", "treatmentSteps"];

const review = v.object({ status: guideReviewStatus, reviewedBy: v.string() });
type Review = typeof review.type;

function cleanContent(input: Content): Content {
  const out = {} as Content;
  for (const key of CONTENT_KEYS) {
    // Windows line endings from pasted text would show as stray characters in the app.
    const value = input[key].replace(/\r\n?/g, "\n").trim();
    if (value.length > MAX_FIELD_CHARS) throw new ConvexError(`${key} is too long (max ${MAX_FIELD_CHARS} characters).`);
    out[key] = value;
  }
  for (const key of REQUIRED) if (!out[key]) throw new ConvexError(`${key} is required.`);
  return out;
}

function cleanReview(input: Review, now: number) {
  const reviewedBy = input.reviewedBy.trim();
  if (input.status === "expert_verified" && !reviewedBy) {
    throw new ConvexError("Expert verified needs the expert's name.");
  }
  return input.status === "unreviewed"
    ? { reviewStatus: input.status, reviewedBy: "", reviewedAt: 0 }
    : { reviewStatus: input.status, reviewedBy, reviewedAt: now };
}

function assertGuideCode(code: string) {
  if (!(GUIDE_CODES as readonly string[]).includes(code)) {
    throw new ConvexError("Guides can only be made for the diseases the model detects.");
  }
}

/** e.g. 2026-11-17-r3: the date of the change and a running revision number. */
function nextVersion(previous: string | null, now: number) {
  const n = Number(previous?.match(/-r(\d+)$/)?.[1] ?? 0) + 1;
  return `${new Date(now).toISOString().slice(0, 10)}-r${n}`;
}

const toClient = (g: Doc<"diseaseReferences">) => ({
  diseaseCode: g.diseaseCode,
  displayName: g.displayName,
  symptoms: g.symptoms,
  causes: g.causes,
  treatmentSteps: g.treatmentSteps,
  duration: g.duration,
  preventionSteps: g.preventionSteps,
  sourceReference: g.sourceReference,
  contentVersion: g.contentVersion,
  updatedAt: g.publishedAt,
  reviewStatus: g.reviewStatus,
  reviewedBy: g.reviewedBy,
  reviewedAt: g.reviewedAt,
});

/** Every guide, in the app's DiseaseReferenceEntity shape. Public: signed-out farmers scan too. */
export const list = query({
  args: {},
  handler: async (ctx) => {
    const rows = await ctx.db.query("diseaseReferences").take(MAX_GUIDES);
    return rows.map(toClient);
  },
});

/** The admin website's view: `list` plus who last changed each guide. */
export const adminList = query({
  args: {},
  handler: async (ctx) => {
    await requireAdmin(ctx);
    const rows = await ctx.db.query("diseaseReferences").take(MAX_GUIDES);
    return Promise.all(rows.map(async (g) => {
      const editor = g.updatedBy ? await ctx.db.get(g.updatedBy) : null;
      return { ...toClient(g), updatedByName: editor ? editor.name ?? editor.email ?? null : null };
    }));
  },
});

async function findGuide(ctx: QueryCtx, diseaseCode: string): Promise<Doc<"diseaseReferences"> | null> {
  return ctx.db
    .query("diseaseReferences")
    .withIndex("by_code", (q) => q.eq("diseaseCode", diseaseCode))
    .unique();
}

export const create = mutation({
  args: { diseaseCode: v.string(), content, review },
  handler: async (ctx, args) => {
    const admin = await requireAdmin(ctx);
    assertGuideCode(args.diseaseCode);
    if (await findGuide(ctx, args.diseaseCode)) throw new ConvexError("This disease already has a guide; edit it instead.");
    const now = Date.now();
    await ctx.db.insert("diseaseReferences", {
      diseaseCode: args.diseaseCode,
      ...cleanContent(args.content),
      ...cleanReview(args.review, now),
      contentVersion: nextVersion(null, now),
      publishedAt: now,
      updatedBy: admin._id,
    });
    return null;
  },
});

/**
 * Replaces a guide's text. When the text changed and no review is passed, the review resets to
 * Unreviewed: the app's label must never claim a review of text nobody has checked.
 */
export const update = mutation({
  args: { diseaseCode: v.string(), content, review: v.optional(review) },
  handler: async (ctx, args) => {
    const admin = await requireAdmin(ctx);
    const guide = await findGuide(ctx, args.diseaseCode);
    if (!guide) throw new ConvexError("This guide no longer exists.");
    const next = cleanContent(args.content);
    const changed = CONTENT_KEYS.some((key) => next[key] !== guide[key]);
    const now = Date.now();
    // Re-saving the same review keeps its original date (and an untouched guide isn't rewritten).
    const sameReview = !args.review ||
      (args.review.status === guide.reviewStatus && args.review.reviewedBy.trim() === guide.reviewedBy);
    if (!changed && sameReview) return null;
    const reviewFields = args.review && !(sameReview && !changed)
      ? cleanReview(args.review, now)
      : changed
        ? { reviewStatus: "unreviewed" as const, reviewedBy: "", reviewedAt: 0 }
        : {};
    await ctx.db.patch(guide._id, {
      ...next,
      ...reviewFields,
      contentVersion: changed ? nextVersion(guide.contentVersion, now) : guide.contentVersion,
      publishedAt: now,
      updatedBy: admin._id,
    });
    return null;
  },
});

/** Deletes a guide; apps drop it on their next sync and show "no treatment information" for that disease. */
export const remove = mutation({
  args: { diseaseCode: v.string() },
  handler: async (ctx, { diseaseCode }) => {
    await requireAdmin(ctx);
    const guide = await findGuide(ctx, diseaseCode);
    if (guide) await ctx.db.delete(guide._id);
    return null;
  },
});

/**
 * CLI only (scripts/guides-snapshot.mjs import): loads guides from the app's bundled snapshot,
 * keeping their version and review. Skips diseases that already have a guide unless `overwrite`.
 */
export const importSnapshot = internalMutation({
  args: {
    overwrite: v.boolean(),
    guides: v.array(content.extend({
      diseaseCode: v.string(),
      contentVersion: v.string(),
      updatedAt: v.number(),
      reviewStatus: guideReviewStatus,
      reviewedBy: v.string(),
      reviewedAt: v.number(),
    })),
  },
  handler: async (ctx, { overwrite, guides }) => {
    const log: string[] = [];
    for (const g of guides) {
      assertGuideCode(g.diseaseCode);
      const { diseaseCode, contentVersion, updatedAt, reviewStatus, reviewedBy, reviewedAt, ...text } = g;
      const doc = {
        // A snapshot entry with no date (0) gets the import time, so the admin site doesn't show "—".
        diseaseCode, ...cleanContent(text), contentVersion, publishedAt: updatedAt || Date.now(), reviewStatus, reviewedBy, reviewedAt,
      };
      const existing = await findGuide(ctx, diseaseCode);
      if (existing && !overwrite) { log.push(`skipped  ${diseaseCode} (already has a guide)`); continue; }
      if (existing) await ctx.db.replace(existing._id, doc);
      else await ctx.db.insert("diseaseReferences", doc);
      log.push(`${existing ? "replaced" : "added   "} ${diseaseCode}`);
    }
    return log;
  },
});

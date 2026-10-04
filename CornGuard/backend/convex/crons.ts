import { cronJobs } from "convex/server";
import { internal } from "./_generated/api";

const crons = cronJobs();

// 18:00 UTC = 2am Philippine time. Reports older than the 14-day window drop out of the
// heatmap's severity tiers even if nothing new is reported.
crons.daily("decay barangay disease stats", { hourUTC: 18, minuteUTC: 0 }, internal.barangayStats.recomputeAll);

export default crons;

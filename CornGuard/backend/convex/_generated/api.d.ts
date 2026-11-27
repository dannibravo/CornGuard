/* eslint-disable */
/**
 * Generated `api` utility.
 *
 * THIS CODE IS AUTOMATICALLY GENERATED.
 *
 * To regenerate, run `npx convex dev`.
 * @module
 */

import type * as admin from "../admin.js";
import type * as auth from "../auth.js";
import type * as barangayNeighbors from "../barangayNeighbors.js";
import type * as barangayStats from "../barangayStats.js";
import type * as crons from "../crons.js";
import type * as devAccounts from "../devAccounts.js";
import type * as devices from "../devices.js";
import type * as diagnosisRecords from "../diagnosisRecords.js";
import type * as diseaseReferences from "../diseaseReferences.js";
import type * as http from "../http.js";
import type * as lib_access from "../lib/access.js";
import type * as lib_barangayCentroids from "../lib/barangayCentroids.js";
import type * as lib_mapPrivacy from "../lib/mapPrivacy.js";
import type * as lib_outbreakRules from "../lib/outbreakRules.js";
import type * as lib_verificationLog from "../lib/verificationLog.js";
import type * as lib_weeklyReports from "../lib/weeklyReports.js";
import type * as notifications from "../notifications.js";
import type * as outbreakAlerts from "../outbreakAlerts.js";
import type * as posts from "../posts.js";
import type * as push from "../push.js";
import type * as pushData from "../pushData.js";
import type * as reports from "../reports.js";
import type * as storage from "../storage.js";
import type * as users from "../users.js";

import type {
  ApiFromModules,
  FilterApi,
  FunctionReference,
} from "convex/server";

declare const fullApi: ApiFromModules<{
  admin: typeof admin;
  auth: typeof auth;
  barangayNeighbors: typeof barangayNeighbors;
  barangayStats: typeof barangayStats;
  crons: typeof crons;
  devAccounts: typeof devAccounts;
  devices: typeof devices;
  diagnosisRecords: typeof diagnosisRecords;
  diseaseReferences: typeof diseaseReferences;
  http: typeof http;
  "lib/access": typeof lib_access;
  "lib/barangayCentroids": typeof lib_barangayCentroids;
  "lib/mapPrivacy": typeof lib_mapPrivacy;
  "lib/outbreakRules": typeof lib_outbreakRules;
  "lib/verificationLog": typeof lib_verificationLog;
  "lib/weeklyReports": typeof lib_weeklyReports;
  notifications: typeof notifications;
  outbreakAlerts: typeof outbreakAlerts;
  posts: typeof posts;
  push: typeof push;
  pushData: typeof pushData;
  reports: typeof reports;
  storage: typeof storage;
  users: typeof users;
}>;

/**
 * A utility for referencing Convex functions in your app's public API.
 *
 * Usage:
 * ```js
 * const myFunctionReference = api.myModule.myFunction;
 * ```
 */
export declare const api: FilterApi<
  typeof fullApi,
  FunctionReference<any, "public">
>;

/**
 * A utility for referencing Convex functions in your app's internal API.
 *
 * Usage:
 * ```js
 * const myFunctionReference = internal.myModule.myFunction;
 * ```
 */
export declare const internal: FilterApi<
  typeof fullApi,
  FunctionReference<any, "internal">
>;

export declare const components: {};

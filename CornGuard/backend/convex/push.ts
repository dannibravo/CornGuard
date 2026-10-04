"use node";

import { v } from "convex/values";
import { GoogleAuth } from "google-auth-library";
import { internal } from "./_generated/api";
import { internalAction } from "./_generated/server";

/**
 * Delivers a `notifications` row as an FCM data message (formerly the onNotificationCreate
 * function): to every active device token of `recipientUserId`, or to the FCM topic named by
 * `areaScope`. Requires the Convex env var FCM_SERVICE_ACCOUNT_JSON — the Firebase project's
 * service-account key JSON (Project settings → Service accounts → Generate new private key).
 */
export const sendNotification = internalAction({
  args: { notificationId: v.id("notifications") },
  handler: async (ctx, { notificationId }) => {
    const delivery = await ctx.runQuery(internal.pushData.loadDelivery, { notificationId });
    if (!delivery) return;
    const { notification, tokens } = delivery;

    const serviceAccountJson = process.env.FCM_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      console.warn("FCM_SERVICE_ACCOUNT_JSON is not set; push not sent.");
      await ctx.runMutation(internal.pushData.setDeliveryStatus, { notificationId, status: "failed" });
      return;
    }
    const credentials = JSON.parse(serviceAccountJson);
    const auth = new GoogleAuth({
      credentials,
      scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
    });
    const accessToken = await auth.getAccessToken();
    const endpoint = `https://fcm.googleapis.com/v1/projects/${credentials.project_id}/messages:send`;

    // Data-only payload; `body` and `message` carry the same text so both old and new app
    // builds display it.
    const data = {
      notification_id: notificationId,
      type: notification.type,
      title: notification.title,
      body: notification.message,
      message: notification.message,
      related_post_id: notification.relatedPostId ?? "",
      related_record_id: notification.relatedRecordId ?? "",
    };

    const targets = notification.recipientUserId
      ? tokens.map((token) => ({ token }))
      : notification.areaScope
        ? [{ topic: notification.areaScope }]
        : [];

    let sent = 0;
    const deadTokens: string[] = [];
    for (const target of targets) {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify({ message: { ...target, data, android: { priority: "high" } } }),
      });
      if (response.ok) {
        sent++;
        continue;
      }
      const body = await response.text();
      console.warn(`FCM send failed (${response.status}): ${body}`);
      // The token will never work again (app uninstalled, or it belongs to another Firebase project).
      if ("token" in target && /UNREGISTERED|SENDER_ID_MISMATCH|registration token is not a valid/i.test(body)) {
        deadTokens.push(target.token);
      }
    }
    if (deadTokens.length > 0) {
      await ctx.runMutation(internal.pushData.deactivateTokens, { fcmTokens: deadTokens });
    }

    await ctx.runMutation(internal.pushData.setDeliveryStatus, {
      notificationId,
      status: sent > 0 ? "sent" : "failed",
    });
  },
});

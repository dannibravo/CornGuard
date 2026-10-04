import { mutation } from "./_generated/server";
import { requireActiveUser } from "./lib/access";

/**
 * Step 1 of an image upload: the app POSTs the JPEG bytes to this URL, receives
 * `{ storageId }`, and passes it as `imageId` to the mutation that creates the record/post.
 */
export const generateUploadUrl = mutation({
  args: {},
  handler: async (ctx) => {
    await requireActiveUser(ctx);
    return await ctx.storage.generateUploadUrl();
  },
});

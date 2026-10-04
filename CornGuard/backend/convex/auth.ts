import { Password } from "@convex-dev/auth/providers/Password";
import { convexAuth } from "@convex-dev/auth/server";

/**
 * Email/password accounts. The Android app calls the `auth:signIn` action directly with
 * `{ provider: "password", params: { email, password, flow: "signUp" | "signIn" } }` and gets
 * back `{ tokens: { token, refreshToken } }`; it refreshes by calling `signIn` with
 * `{ refreshToken }`. Profile fields are written afterwards by `users:createProfile`.
 */
export const { auth, signIn, signOut, store, isAuthenticated } = convexAuth({
  providers: [Password],
});

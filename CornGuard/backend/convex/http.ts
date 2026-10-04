import { httpRouter } from "convex/server";
import { auth } from "./auth";

const http = httpRouter();

// Serves the OIDC discovery + JWKS endpoints Convex uses to verify Convex Auth JWTs.
auth.addHttpRoutes(http);

export default http;

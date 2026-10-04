import { ConvexReactClient } from 'convex/react';
import { anyApi } from 'convex/server';

export const convex = new ConvexReactClient(import.meta.env.VITE_CONVEX_URL);
export const api = anyApi;
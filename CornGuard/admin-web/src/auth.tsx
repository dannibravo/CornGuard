import { useConvexAuth, useQuery } from 'convex/react';
import { api } from './convex';

/**
 * Admin access. caps 3's admin-web compared a username/password baked into the JavaScript bundle
 * and kept a localStorage flag; here the admin signs in with a real CornGuard (Convex Auth)
 * account, and every admin function re-checks the role on the server (requireAdmin), so this hook
 * only decides what to show.
 */
export type Viewer = {
  userId: string;
  email: string | null;
  displayName: string | null;
  role: 'farmer' | 'admin';
  accountStatus: 'active' | 'suspended';
};

export function useAdmin() {
  const { isLoading, isAuthenticated } = useConvexAuth();
  const viewer = useQuery(api.users.viewer, isAuthenticated ? {} : 'skip') as Viewer | null | undefined;
  return {
    isLoading: isLoading || (isAuthenticated && viewer === undefined),
    isAuthenticated,
    viewer: viewer ?? null,
    isAdmin: viewer?.role === 'admin' && viewer.accountStatus !== 'suspended',
  };
}

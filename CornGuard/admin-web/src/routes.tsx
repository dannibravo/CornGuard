import { Navigate, Outlet } from 'react-router-dom';
import { useAuthActions } from '@convex-dev/auth/react';
import { useAdmin } from './auth';
import { PageLoader, Button, Alert } from './components/ui';
import { AuthShell } from './pages/LoginPage';
import { useIdleSignOut } from './idleTimeout';

export function ProtectedRoute() {
  const { isLoading, isAuthenticated, isAdmin, viewer } = useAdmin();
  const { signOut } = useAuthActions();
  useIdleSignOut(isAuthenticated);

  if (isLoading) return <div className="fullscreen-center"><PageLoader label="Loading CornGuard Admin…" /></div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isAdmin) {
    return (
      <AuthShell>
        <div className="auth-header">
          <h1 className="auth-title">Admins only</h1>
        </div>
        <Alert variant="warning">
          {viewer?.email ?? 'This account'} is {viewer?.accountStatus === 'suspended' ? 'suspended' : 'not an admin'}.
          Admin access is granted by the CornGuard team.
        </Alert>
        <Button variant="primary" block icon="logout" onClick={() => void signOut()}>Sign out</Button>
      </AuthShell>
    );
  }
  return <Outlet />;
}

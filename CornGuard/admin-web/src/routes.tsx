import { Navigate, Outlet } from 'react-router-dom';
import { useAuthActions } from '@convex-dev/auth/react';
import { useAdmin } from './auth';

export function ProtectedRoute() {
  const { isLoading, isAuthenticated, isAdmin, viewer } = useAdmin();
  const { signOut } = useAuthActions();

  if (isLoading) return <div className="loading">Loading…</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isAdmin) {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <div className="login-logo" aria-hidden="true">🔒</div>
            <h1 className="login-title">Admins only</h1>
            <p className="login-subtitle">
              {viewer?.email ?? 'This account'} is {viewer?.accountStatus === 'suspended' ? 'suspended' : 'not an admin'}.
              Ask an existing admin to promote it in Users.
            </p>
          </div>
          <button className="btn btn-primary btn-full" onClick={() => void signOut()}>Sign out</button>
        </div>
      </div>
    );
  }
  return <Outlet />;
}

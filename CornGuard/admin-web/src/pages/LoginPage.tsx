import { useState, FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthActions } from '@convex-dev/auth/react';
import { useConvexAuth } from 'convex/react';

/** Signs in with a CornGuard account (the same email/password as the app). Admin role is checked after. */
export default function LoginPage() {
  const { signIn } = useAuthActions();
  const { isAuthenticated } = useConvexAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (isAuthenticated) return <Navigate to="/" replace />;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await signIn('password', { email: email.trim(), password, flow: 'signIn' });
    } catch {
      setError('Incorrect email or password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <div className="login-logo" aria-hidden="true">🌽</div>
          <h1 className="login-title">CornGuard Admin</h1>
          <p className="login-subtitle">Sign in with your CornGuard admin account</p>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          {error && <div className="login-error" role="alert">{error}</div>}

          <div className="form-group">
            <label htmlFor="email" className="form-label">Email</label>
            <input type="email" id="email" className="form-input" value={email}
              onChange={(e) => setEmail(e.target.value)} placeholder="admin@example.com"
              autoComplete="username" required disabled={loading} autoFocus />
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">Password</label>
            <input type="password" id="password" className="form-input" value={password}
              onChange={(e) => setPassword(e.target.value)} placeholder="Password"
              autoComplete="current-password" required disabled={loading} />
          </div>

          <button type="submit" className="btn btn-primary btn-full" disabled={loading}>
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>

        <div className="login-footer">
          <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>
            Only accounts with the admin role can use this site.
          </p>
        </div>
      </div>
    </div>
  );
}

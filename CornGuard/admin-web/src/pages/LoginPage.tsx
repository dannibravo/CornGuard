import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthActions } from '@convex-dev/auth/react';
import { useConvexAuth } from 'convex/react';
import { Icon } from '../components/Icon';
import { Alert, Button } from '../components/ui';
import { clearIdleSignOutNotice, resetIdleClock, wasSignedOutForIdle } from '../idleTimeout';

/** Signs in with a CornGuard account (the same email/password as the app). Admin role is checked after. */
export default function LoginPage() {
  const { signIn } = useAuthActions();
  const { isAuthenticated } = useConvexAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [idleNotice] = useState(wasSignedOutForIdle);
  useEffect(clearIdleSignOutNotice, []);

  if (isAuthenticated) return <Navigate to="/" replace />;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      resetIdleClock();
      await signIn('password', { email: email.trim(), password, flow: 'signIn' });
    } catch {
      setError('Incorrect email or password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <div className="auth-header">
        <h1 className="auth-title">Sign in</h1>
        <p className="auth-subtitle">Use your CornGuard admin account</p>
      </div>

      <form onSubmit={handleSubmit} className="auth-form">
        {error && <Alert variant="error">{error}</Alert>}
        {!error && idleNotice && (
          <Alert variant="info">You were signed out after 2 hours without activity. Please sign in again.</Alert>
        )}

        <div className="form-group">
          <label htmlFor="email" className="form-label">Email</label>
          <input type="email" id="email" className="form-input" value={email}
            onChange={(e) => setEmail(e.target.value)} placeholder="admin@example.com"
            autoComplete="username" required disabled={loading} autoFocus
            aria-invalid={!!error || undefined} />
        </div>

        <div className="form-group">
          <label htmlFor="password" className="form-label">Password</label>
          <div className="input-affix">
            <input type={showPassword ? 'text' : 'password'} id="password" className="form-input" value={password}
              onChange={(e) => setPassword(e.target.value)} placeholder="Password"
              autoComplete="current-password" required disabled={loading}
              aria-invalid={!!error || undefined} />
            <button type="button" className="icon-btn icon-btn-sm input-affix-btn" onClick={() => setShowPassword((s) => !s)}
              aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword}>
              <Icon name={showPassword ? 'eyeOff' : 'eye'} size={18} />
            </button>
          </div>
        </div>

        <Button type="submit" variant="primary" block busy={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>

      <p className="auth-footnote">Only accounts with the admin role can use this site.</p>
    </AuthShell>
  );
}

/** Split-screen frame shared by the sign-in and "admins only" screens. */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth-page">
      <aside className="auth-brand" aria-hidden="true">
        <div className="auth-brand-inner">
          <span className="brand-mark brand-mark-lg"><Icon name="corn" size={32} /></span>
          <h2>CornGuard Admin</h2>
          <p>Review leaf scans, monitor outbreak alerts and keep the farmer community healthy across Bukidnon.</p>
          <ul>
            <li><Icon name="map" size={18} /> Live outbreak map</li>
            <li><Icon name="alert" size={18} /> Automatic disease alerts</li>
            <li><Icon name="shield" size={18} /> Scan & community moderation</li>
          </ul>
        </div>
      </aside>
      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-mobile-brand">
            <span className="brand-mark"><Icon name="corn" size={22} /></span>
            <span className="brand-name">CornGuard Admin</span>
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}

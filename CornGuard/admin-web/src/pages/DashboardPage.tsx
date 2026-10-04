import { useQuery } from 'convex/react';
import { Link } from 'react-router-dom';
import { api } from '../convex';

export default function DashboardPage() {
  const stats = useQuery(api.admin.stats);

  const cards: { key: string; label: string; icon: string; to?: string; attention?: boolean }[] = [
    { key: 'pendingOutbreaks', label: 'Outbreaks awaiting review', icon: '🚨', to: '/outbreaks', attention: true },
    { key: 'scansUnverified', label: 'Scans awaiting review', icon: '🔬', to: '/moderation', attention: true },
    { key: 'postsUnverified', label: 'Unverified posts', icon: '📝', to: '/moderation' },
    { key: 'severeAreas', label: 'Severe barangay / disease', icon: '🗺️', to: '/map' },
    { key: 'users', label: 'Users', icon: '👥', to: '/users' },
    { key: 'scans', label: 'Shared scans', icon: '🌽' },
    { key: 'scansVerified', label: 'Verified scans', icon: '✅' },
    { key: 'suspendedUsers', label: 'Suspended accounts', icon: '⛔', to: '/users' },
  ];

  if (stats === undefined) return <div className="loading">Loading…</div>;

  return (
    <div className="dashboard">
      <div className="page-header">
        <h1 className="page-title">Dashboard</h1>
        <p className="page-subtitle">What needs attention in CornGuard</p>
      </div>

      <div className="stats-grid">
        {cards.map((card) => {
          const value = stats[card.key] ?? 0;
          const body = (
            <div className="stat-card" style={card.attention && value > 0 ? { outline: '2px solid #f59e0b' } : undefined}>
              <div className="stat-icon green" aria-hidden="true">{card.icon}</div>
              <div className="stat-info">
                <div className="stat-value">{value}</div>
                <div className="stat-label">{card.label}</div>
              </div>
            </div>
          );
          return card.to
            ? <Link key={card.key} to={card.to} style={{ textDecoration: 'none', color: 'inherit' }}>{body}</Link>
            : <div key={card.key}>{body}</div>;
        })}
      </div>

      <div className="card">
        <div className="card-header"><h2 className="card-title">How alerts work</h2></div>
        <div className="card-body" style={{ fontSize: 14, lineHeight: 1.6 }}>
          When a barangay reaches <strong>Severe</strong> for a disease (weighted score ≥ 4 from ≥ 3 farms in 14 days),
          it appears under <Link to="/outbreaks">Outbreaks</Link> as <em>pending</em>. Farmers in that barangay and
          the ones bordering it are notified <strong>only after you approve it</strong>. Rejecting scans in
          <Link to="/moderation"> Review</Link> lowers severity on the next recompute.
        </div>
      </div>
    </div>
  );
}

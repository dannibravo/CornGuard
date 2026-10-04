import { NavLink, useLocation } from 'react-router-dom';
import { useAuthActions } from '@convex-dev/auth/react';
import { useQuery } from 'convex/react';
import { api } from '../convex';
import { useAdmin } from '../auth';

export function Sidebar() {
  const location = useLocation();
  const { signOut } = useAuthActions();
  const { viewer } = useAdmin();
  const stats = useQuery(api.admin.stats);

  const navItems = [
    { path: '/', label: 'Dashboard', icon: '📊', count: undefined as number | undefined },
    { path: '/outbreaks', label: 'Outbreaks', icon: '🚨', count: stats?.pendingOutbreaks },
    { path: '/moderation', label: 'Review', icon: '🛡️', count: stats?.scansUnverified },
    { path: '/users', label: 'Users', icon: '👥', count: undefined },
    { path: '/map', label: 'Outbreak Map', icon: '🗺️', count: undefined },
  ];

  return (
    <aside className="sidebar" role="navigation" aria-label="Admin navigation">
      <div className="sidebar-brand">
        <span className="brand-icon" aria-hidden="true">🌽</span>
        <span className="brand-text">CornGuard Admin</span>
      </div>
      <nav className="sidebar-nav">
        <ul role="list">
          {navItems.map((item) => (
            <li key={item.path}>
              <NavLink
                to={item.path}
                end={item.path === '/'}
                className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                aria-current={location.pathname === item.path ? 'page' : undefined}
              >
                <span className="nav-icon" aria-hidden="true">{item.icon}</span>
                <span className="nav-label">{item.label}</span>
                {!!item.count && <span className="badge badge-orange" style={{ marginLeft: 'auto' }}>{item.count}</span>}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <div className="sidebar-footer">
        <div className="user-info">
          <span className="user-badge" aria-label="Signed-in admin">{viewer?.displayName ?? 'Admin'}</span>
          <span className="user-role">{viewer?.email ?? 'Administrator'}</span>
        </div>
        <button className="btn-logout" onClick={() => void signOut()} aria-label="Log out">
          🚪 Logout
        </button>
      </div>
    </aside>
  );
}

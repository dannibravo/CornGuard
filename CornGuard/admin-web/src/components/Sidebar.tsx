import { NavLink } from 'react-router-dom';
import { useAuthActions } from '@convex-dev/auth/react';
import { useQuery } from 'convex/react';
import { api } from '../convex';
import { useAdmin } from '../auth';
import { Icon, type IconName } from './Icon';
import { Avatar } from './ui';

type NavItem = { path: string; label: string; icon: IconName; count?: number };

/**
 * Three modes, all CSS-driven (admin.css "App shell"): full sidebar ≥1280px, icon rail
 * 1024–1279px, off-canvas drawer below 1024px (opened from the header's menu button).
 */
export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { signOut } = useAuthActions();
  const { viewer } = useAdmin();
  const stats = useQuery(api.admin.stats);
  const toReview = stats ? stats.scansUnverified + stats.reportsOpen : undefined;

  const groups: { label: string; items: NavItem[] }[] = [
    { label: 'Overview', items: [
      { path: '/', label: 'Dashboard', icon: 'dashboard' },
      { path: '/map', label: 'Outbreak Map', icon: 'map' },
    ] },
    { label: 'Operations', items: [
      { path: '/outbreaks', label: 'Outbreaks', icon: 'alert' },
      { path: '/moderation', label: 'Review', icon: 'shield', count: toReview },
    ] },
    { label: 'Administration', items: [
      { path: '/users', label: 'Users', icon: 'users' },
    ] },
  ];

  const name = viewer?.displayName ?? 'Admin';

  return (
    <aside id="admin-sidebar" className={`sidebar${open ? ' open' : ''}`} aria-label="Admin navigation">
      <div className="sidebar-brand">
        <span className="brand-mark" aria-hidden="true">
          <Icon name="corn" size={22} />
        </span>
        <span className="brand-text">
          <span className="brand-name">CornGuard</span>
          <span className="brand-sub">Admin console</span>
        </span>
        <button type="button" className="icon-btn sidebar-close" onClick={onClose} aria-label="Close navigation">
          <Icon name="close" size={20} />
        </button>
      </div>

      <nav className="sidebar-nav">
        {groups.map((group) => (
          <div key={group.label} className="nav-group">
            <div className="nav-group-label">{group.label}</div>
            <ul role="list">
              {group.items.map((item) => (
                <li key={item.path}>
                  <NavLink
                    to={item.path}
                    end={item.path === '/'}
                    className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
                    title={item.label}
                  >
                    <Icon name={item.icon} className="nav-icon" />
                    <span className="nav-label">{item.label}</span>
                    {!!item.count && (
                      <span className="nav-count" aria-label={`${item.count} awaiting review`}>{item.count > 99 ? '99+' : item.count}</span>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="sidebar-user" title={viewer?.email ?? undefined}>
          <Avatar name={viewer?.displayName ?? viewer?.email} />
          <span className="sidebar-user-text">
            <span className="sidebar-user-name" aria-label="Signed-in admin">{name}</span>
            <span className="sidebar-user-email">{viewer?.email ?? 'Administrator'}</span>
          </span>
        </div>
        <button type="button" className="btn btn-ghost btn-full sidebar-logout" onClick={() => void signOut()} title="Log out">
          <Icon name="logout" size={18} />
          <span className="nav-label">Log out</span>
        </button>
      </div>
    </aside>
  );
}

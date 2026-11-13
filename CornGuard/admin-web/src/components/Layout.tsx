import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useQuery } from 'convex/react';
import { api } from '../convex';
import { Sidebar } from './Sidebar';
import { Icon } from './Icon';
import { lockBodyScroll } from './useDialog';

/** Below this width the sidebar is an off-canvas drawer (keep in sync with admin.css). */
const DRAWER_QUERY = '(max-width: 1023.98px)';

const PAGE_META: Record<string, { title: string; subtitle: string }> = {
  '/': { title: 'Dashboard', subtitle: 'What needs your attention across CornGuard' },
  '/outbreaks': { title: 'Outbreak Alerts', subtitle: 'Automatic disease alerts and their push delivery' },
  '/moderation': { title: 'Review & Moderation', subtitle: 'Verify leaf scans and moderate community content' },
  '/users': { title: 'User Management', subtitle: 'Farmer and admin accounts' },
  '/map': { title: 'Outbreak Map', subtitle: 'Verified reports and outbreak levels across Bukidnon' },
};

export function Layout() {
  const { pathname } = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  // Same subscription as the Sidebar's badge; Convex shares it.
  const stats = useQuery(api.admin.stats);
  const meta = PAGE_META[pathname] ?? { title: 'Admin', subtitle: '' };
  const toReview = stats ? (stats.scansUnverified ?? 0) + (stats.reportsOpen ?? 0) : 0;

  useEffect(() => { setNavOpen(false); }, [pathname]);
  useEffect(() => { document.title = `${meta.title} · CornGuard Admin`; }, [meta.title]);

  // While the drawer is open: Escape closes it, the page behind doesn't scroll, and growing the
  // window past the drawer breakpoint closes it so the lock can't get stuck.
  useEffect(() => {
    if (!navOpen) return;
    const release = lockBodyScroll();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setNavOpen(false); };
    const mq = window.matchMedia(DRAWER_QUERY);
    const onMq = () => { if (!mq.matches) setNavOpen(false); };
    document.addEventListener('keydown', onKey);
    mq.addEventListener('change', onMq);
    return () => {
      release();
      document.removeEventListener('keydown', onKey);
      mq.removeEventListener('change', onMq);
    };
  }, [navOpen]);

  return (
    <div className={`layout${navOpen ? ' nav-open' : ''}`}>
      <a href="#main" className="skip-link">Skip to content</a>
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
      <div className="sidebar-backdrop" onClick={() => setNavOpen(false)} aria-hidden="true" />
      <div className="main">
        <header className="topbar">
          <button
            type="button"
            className="icon-btn topbar-menu"
            onClick={() => setNavOpen(true)}
            aria-label="Open navigation"
            aria-expanded={navOpen}
            aria-controls="admin-sidebar"
          >
            <Icon name="menu" size={22} />
          </button>
          <div className="topbar-heading">
            <h1 className="topbar-title">{meta.title}</h1>
            {meta.subtitle && <p className="topbar-subtitle">{meta.subtitle}</p>}
          </div>
          <div className="topbar-actions">
            {toReview > 0 && pathname !== '/moderation' && (
              <Link to="/moderation" className="attention-pill" title="Scans awaiting review and open reports">
                <span className="pulse-dot" aria-hidden="true" />
                <span><strong>{toReview}</strong> to review</span>
                <Icon name="arrowRight" size={14} />
              </Link>
            )}
          </div>
        </header>
        <main id="main" className="page-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export function Layout() {
  const location = useLocation();
  return (
    <div className="layout">
      <Sidebar />
      <main className="main-content">
        <header className="header">
          <h1 className="header-title">{getPageTitle(location.pathname)}</h1>
        </header>
        <div className="page-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

function getPageTitle(pathname: string): string {
  switch (pathname) {
    case '/': return 'Dashboard';
    case '/outbreaks': return 'Outbreak Alerts';
    case '/moderation': return 'Review & Moderation';
    case '/users': return 'User Management';
    case '/map': return 'Outbreak Map';
    default: return 'Admin';
  }
}

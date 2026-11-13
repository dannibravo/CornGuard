import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConvexAuthProvider } from '@convex-dev/auth/react';
import { convex } from './convex';
import { ProtectedRoute } from './routes';
import { Layout } from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import OutbreaksPage from './pages/OutbreaksPage';
import ModerationPage from './pages/ModerationPage';
import UsersPage from './pages/UsersPage';
import MapPage from './pages/MapPage';
import './styles/admin.css';

// Admin sessions end when the tab or browser closes: the auth token lives in sessionStorage,
// not localStorage (Convex Auth's default, which kept admins signed in across restarts).
// Remove tokens left in localStorage by builds from before this change.
try {
  Object.keys(localStorage)
    .filter((key) => key.startsWith('__convexAuth'))
    .forEach((key) => localStorage.removeItem(key));
} catch {
  // Storage blocked (private mode etc.): nothing to clean up.
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConvexAuthProvider client={convex} storage={sessionStorage}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/outbreaks" element={<OutbreaksPage />} />
              <Route path="/moderation" element={<ModerationPage />} />
              <Route path="/users" element={<UsersPage />} />
              <Route path="/map" element={<MapPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ConvexAuthProvider>
  </React.StrictMode>
);

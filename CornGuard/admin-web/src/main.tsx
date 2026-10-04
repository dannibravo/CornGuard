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

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConvexAuthProvider client={convex}>
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

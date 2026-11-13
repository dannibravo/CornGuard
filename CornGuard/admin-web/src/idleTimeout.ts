import { useEffect } from 'react';
import { useAuthActions } from '@convex-dev/auth/react';

/**
 * Admin sessions end after IDLE_LIMIT_MS without mouse, keyboard, touch or scroll activity.
 * Admin web only: Convex Auth's server-side session length is shared with the farmers' app,
 * so shortening it there would sign phones out too.
 * The last-activity time is kept in sessionStorage next to the auth token, so a page refresh
 * doesn't reset the clock and a tab left closed past the limit starts signed out.
 */
export const IDLE_LIMIT_MS = 2 * 60 * 60 * 1000;

const LAST_ACTIVITY_KEY = 'cg_admin_last_activity';
const IDLE_SIGNED_OUT_KEY = 'cg_admin_idle_signed_out';
const CHECK_EVERY_MS = 30 * 1000;
const WRITE_THROTTLE_MS = 15 * 1000;
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'wheel', 'scroll'] as const;

function read(key: string): string | null {
  try { return sessionStorage.getItem(key); } catch { return null; }
}
function write(key: string, value: string | null) {
  try {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, value);
  } catch { /* storage blocked: the in-memory timer still works for this page */ }
}

/** Signs the admin out after IDLE_LIMIT_MS of inactivity. Call only while signed in. */
export function useIdleSignOut(enabled: boolean) {
  const { signOut } = useAuthActions();

  useEffect(() => {
    if (!enabled) return;
    let lastActivity = Number(read(LAST_ACTIVITY_KEY)) || Date.now();
    let lastWrite = 0;
    let signingOut = false;

    const expire = () => {
      if (signingOut) return;
      signingOut = true;
      write(LAST_ACTIVITY_KEY, null);
      write(IDLE_SIGNED_OUT_KEY, '1');
      void signOut();
    };
    const check = () => {
      if (Date.now() - lastActivity >= IDLE_LIMIT_MS) expire();
    };
    const onActivity = () => {
      const now = Date.now();
      // A laptop waking from sleep fires activity events; check before counting them.
      if (now - lastActivity >= IDLE_LIMIT_MS) return expire();
      lastActivity = now;
      if (now - lastWrite >= WRITE_THROTTLE_MS) {
        lastWrite = now;
        write(LAST_ACTIVITY_KEY, String(now));
      }
    };
    const onVisible = () => { if (document.visibilityState === 'visible') check(); };

    check();
    if (!signingOut) write(LAST_ACTIVITY_KEY, String(lastActivity));
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));
    document.addEventListener('visibilitychange', onVisible);
    const timer = window.setInterval(check, CHECK_EVERY_MS);
    return () => {
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity));
      document.removeEventListener('visibilitychange', onVisible);
      window.clearInterval(timer);
    };
  }, [enabled, signOut]);
}

/** True after an idle sign-out, so the login page can say why the admin was signed out. */
export function wasSignedOutForIdle(): boolean {
  return read(IDLE_SIGNED_OUT_KEY) === '1';
}

/** Clears the idle sign-out notice once the login page has shown it. */
export function clearIdleSignOutNotice() {
  write(IDLE_SIGNED_OUT_KEY, null);
}

/** Called on a manual sign-in so a fresh session starts with a fresh clock. */
export function resetIdleClock() {
  write(LAST_ACTIVITY_KEY, String(Date.now()));
}

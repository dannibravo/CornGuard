import type { ReactNode } from 'react';

/**
 * Small inline-SVG icon set (24×24, stroked, inherits currentColor). Replaces the emoji icons,
 * which rendered differently on every OS and couldn't follow the theme colours. No icon
 * dependency on purpose — add a path here when a new icon is needed.
 */
const PATHS = {
  dashboard: <><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></>,
  alert: <><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" /></>,
  shield: <><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /><path d="m9 12 2 2 4-4" /></>,
  users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></>,
  map: <><path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2z" /><path d="M9 4v14" /><path d="M15 6v14" /></>,
  logout: <><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" /></>,
  menu: <><path d="M4 6h16" /><path d="M4 12h16" /><path d="M4 18h16" /></>,
  close: <><path d="M18 6 6 18" /><path d="m6 6 12 12" /></>,
  chevronRight: <path d="m9 18 6-6-6-6" />,
  arrowRight: <><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></>,
  inbox: <><path d="M22 12h-6l-2 3h-4l-2-3H2" /><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></>,
  image: <><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" /><path d="m21 15-3.09-3.09a2 2 0 0 0-2.82 0L6 21" /></>,
  imageOff: <><path d="m2 2 20 20" /><path d="M10.41 10.41a2 2 0 1 1-2.83-2.83" /><path d="M21 15.34V5a2 2 0 0 0-2-2H8.66" /><path d="M3 5v14a2 2 0 0 0 2 2h14" /><path d="m13.5 13.5 1.5-1.5L21 18" /></>,
  eye: <><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" /></>,
  eyeOff: <><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" /><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c6.5 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" /><path d="M6.61 6.61A13.53 13.53 0 0 0 2 12s3.5 7 10 7a9.74 9.74 0 0 0 5.39-1.61" /><path d="m2 2 20 20" /></>,
  info: <><circle cx="12" cy="12" r="10" /><path d="M12 16v-4" /><path d="M12 8h.01" /></>,
  warning: <><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3z" /><path d="M12 9v4" /><path d="M12 17h.01" /></>,
  scan: <><path d="M3 7V5a2 2 0 0 1 2-2h2" /><path d="M17 3h2a2 2 0 0 1 2 2v2" /><path d="M21 17v2a2 2 0 0 1-2 2h-2" /><path d="M7 21H5a2 2 0 0 1-2-2v-2" /><path d="M7 12h10" /></>,
  flag: <><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" /><path d="M4 22v-7" /></>,
  help: <><circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><path d="M12 17h.01" /></>,
  check: <path d="M20 6 9 17l-5-5" />,
  checkCircle: <><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><path d="m22 4-10 10.01-3-3" /></>,
  ban: <><circle cx="12" cy="12" r="10" /><path d="m4.9 4.9 14.2 14.2" /></>,
  post: <><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><path d="M14 2v6h6" /><path d="M16 13H8" /><path d="M16 17H8" /></>,
  leaf: <><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10z" /><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12" /></>,
  phone: <><rect x="6" y="2" width="12" height="20" rx="2" /><path d="M11 18h2" /></>,
  sort: <><path d="m7 15 5 5 5-5" /><path d="m7 9 5-5 5 5" /></>,
  sortUp: <path d="m7 14 5-5 5 5" />,
  sortDown: <path d="m7 10 5 5 5-5" />,
  /** CornGuard mark: corn cob silhouette in its husk. Filled, not stroked (see FILLED); the kernel
   *  grooves are cut out with evenodd. Same shape as public/favicon.svg — keep in sync. */
  corn: <>
    <path fillRule="evenodd" d="M12 1.5c2.9 0 4.6 3.4 4.6 8.1 0 4.2-1.6 7.3-4.6 7.3s-4.6-3.1-4.6-7.3C7.4 4.9 9.1 1.5 12 1.5zM11.35 3.1h1.3v12.3h-1.3zM8.3 6.3h7.4v1.2H8.3zM7.95 9.4h8.1v1.2H7.95zM8.3 12.5h7.4v1.2H8.3z" />
    <path d="M12 22.7c-4.1-.4-7.9-3.6-8.8-9.9 3.5.7 6.4 3.3 7.8 6.9l1 3z" />
    <path d="M12 22.7c4.1-.4 7.9-3.6 8.8-9.9-3.5.7-6.4 3.3-7.8 6.9l-1 3z" />
  </>,
} satisfies Record<string, ReactNode>;

export type IconName = keyof typeof PATHS;

/** Icons drawn as solid shapes rather than outlines. */
const FILLED: ReadonlySet<IconName> = new Set<IconName>(['corn']);

export function Icon({ name, size = 20, className = '', title }: { name: IconName; size?: number; className?: string; title?: string }) {
  const filled = FILLED.has(name);
  return (
    <svg
      className={`icon ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke={filled ? 'none' : 'currentColor'}
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      focusable="false"
    >
      {title && <title>{title}</title>}
      {PATHS[name]}
    </svg>
  );
}

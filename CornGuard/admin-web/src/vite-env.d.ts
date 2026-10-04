/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The CornGuard Convex deployment, e.g. https://<name>.convex.cloud (set in .env.local). */
  readonly VITE_CONVEX_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

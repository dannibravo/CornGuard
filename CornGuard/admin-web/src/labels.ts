// Shared display helpers. Disease codes match the app and backend (labels.json / DiseaseCode).
// @ts-ignore — plain GeoJSON asset (same file the app bundles)
import barangays from './assets/bukidnon-barangays.json';

export const DISEASE_LABEL: Record<string, string> = {
  northern_leaf_blight: 'Northern Leaf Blight',
  common_rust: 'Common Rust',
  gray_leaf_spot: 'Gray Leaf Spot',
  healthy: 'Healthy',
};
export const ALERT_DISEASES = ['northern_leaf_blight', 'common_rust', 'gray_leaf_spot'];

export const diseaseLabel = (code: string) => DISEASE_LABEL[code] ?? code;
export const pct = (x: number) => `${(x * 100).toFixed(1)}%`;
export const date = (ms: number | null | undefined) => (ms ? new Date(ms).toLocaleString() : '—');

/** Barangays per municipality from the bundled Bukidnon boundaries (official names, as the backend uses). */
export const BARANGAYS_BY_MUNICIPALITY: Record<string, string[]> = (() => {
  const out: Record<string, string[]> = {};
  for (const f of (barangays as any).features) {
    const { barangay, municipality } = f.properties;
    (out[municipality] ??= []).push(barangay);
  }
  for (const m of Object.keys(out)) out[m].sort();
  return out;
})();
export const MUNICIPALITIES = Object.keys(BARANGAYS_BY_MUNICIPALITY).sort();

export const statusBadge = (status: string) =>
  ({ verified: 'badge-green', approved: 'badge-green', visible: 'badge-green', active: 'badge-green',
     unverified: 'badge-orange', pending: 'badge-orange',
     rejected: 'badge-red', dismissed: 'badge-gray', hidden: 'badge-orange', removed: 'badge-red', suspended: 'badge-red',
  } as Record<string, string>)[status] ?? 'badge-gray';

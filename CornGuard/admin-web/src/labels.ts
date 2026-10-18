// Shared display helpers. Disease codes match the app and backend (labels.json / DiseaseCode).
export const DISEASE_LABEL: Record<string, string> = {
  northern_leaf_blight: 'Northern Leaf Blight',
  common_rust: 'Common Rust',
  gray_leaf_spot: 'Gray Leaf Spot',
  healthy: 'Healthy',
};

export const diseaseLabel = (code: string) => DISEASE_LABEL[code] ?? code;
export const pct = (x: number) => `${(x * 100).toFixed(1)}%`;
export const date = (ms: number | null | undefined) => (ms ? new Date(ms).toLocaleString() : '—');

export const statusBadge = (status: string) =>
  ({ verified: 'badge-green', approved: 'badge-green', visible: 'badge-green', active: 'badge-green',
     unverified: 'badge-orange', pending: 'badge-orange',
     rejected: 'badge-red', dismissed: 'badge-gray', hidden: 'badge-orange', removed: 'badge-red', suspended: 'badge-red',
  } as Record<string, string>)[status] ?? 'badge-gray';

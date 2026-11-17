// Shared display helpers. Disease codes match the app and backend (labels.json / DiseaseCode).
export const DISEASE_LABEL: Record<string, string> = {
  northern_leaf_blight: 'Northern Leaf Blight',
  common_rust: 'Common Rust',
  gray_leaf_spot: 'Gray Leaf Spot',
  healthy: 'Healthy',
};

/** The classes the model detects: one treatment guide each (backend diseaseReferences.GUIDE_CODES). */
export const GUIDE_CODES = ['northern_leaf_blight', 'common_rust', 'gray_leaf_spot', 'healthy'] as const;

// Treatment guide review tiers. KEEP IN SYNC with backend schema guideReviewStatus and the app's ReviewStatus.
export type GuideReviewStatus = 'unreviewed' | 'sources_reviewed' | 'expert_verified';
export const REVIEW_STATUS_LABEL: Record<GuideReviewStatus, string> = {
  unreviewed: 'Unreviewed',
  sources_reviewed: 'Sources reviewed',
  expert_verified: 'Expert verified',
};
export const REVIEW_STATUS_BADGE: Record<GuideReviewStatus, string> = {
  unreviewed: 'badge-gray',
  sources_reviewed: 'badge-blue',
  expert_verified: 'badge-green',
};

export const diseaseLabel = (code: string) => DISEASE_LABEL[code] ?? code;
export const pct = (x: number) => `${(x * 100).toFixed(1)}%`;
export const date = (ms: number | null | undefined) => (ms ? new Date(ms).toLocaleString() : '—');

export const statusBadge = (status: string) =>
  ({ verified: 'badge-green', approved: 'badge-green', visible: 'badge-green', active: 'badge-green',
     unverified: 'badge-orange', pending: 'badge-orange',
     rejected: 'badge-red', dismissed: 'badge-gray', hidden: 'badge-orange', removed: 'badge-red', suspended: 'badge-red',
  } as Record<string, string>)[status] ?? 'badge-gray';

// Why a farmer reported a post or comment. KEEP IN SYNC with backend/convex/schema.ts reportReason
// and the app's ReportReason.
export const REPORT_REASON_LABEL: Record<string, string> = {
  wrong_info: 'Wrong diagnosis or advice',
  spam: 'Spam or selling',
  offensive: 'Offensive or abusive',
  off_topic: 'Not about corn farming',
  other: 'Something else',
};

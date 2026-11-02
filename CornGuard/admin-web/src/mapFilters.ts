// Outbreak Map filters: which diseases and severities are plotted.
// KEEP IN SYNC with the filter helpers in android/app/src/main/assets/map/outbreak-map.html — both
// maps must filter the same way. Update both or neither.

/** The three tiers from barangayStats plus 'past' for stage "recent" (14-28 day old) reports. */
export type SeverityKey = 'severe' | 'moderate' | 'mild' | 'past';

export const SEVERITY_OPTIONS: { key: SeverityKey; label: string }[] = [
  { key: 'severe', label: 'Severe' },
  { key: 'moderate', label: 'Moderate' },
  { key: 'mild', label: 'Mild' },
  { key: 'past', label: 'Past (2–4 wks)' },
];

/** An empty set means "All" (no narrowing). */
export type MapFilters = { diseases: ReadonlySet<string>; severities: ReadonlySet<SeverityKey> };

export const EMPTY_FILTERS: MapFilters = { diseases: new Set(), severities: new Set() };

/** A report's severity key: 'past' for faded reports, otherwise its barangay+disease tier. */
export function severityKey(
  d: { stage?: string; barangay: string; municipality: string; diseaseCode: string },
  tierByAreaDisease: Map<string, string>,
): SeverityKey {
  if (d.stage === 'recent') return 'past';
  return (tierByAreaDisease.get(`${d.barangay}||${d.municipality}||${d.diseaseCode}`) || 'mild') as SeverityKey;
}

export function matchesFilters(diseaseCode: string, sev: SeverityKey, f: MapFilters) {
  return (f.diseases.size === 0 || f.diseases.has(diseaseCode)) && (f.severities.size === 0 || f.severities.has(sev));
}

/** Whether a barangay stat row (always a current tier, never 'past') passes the filters. */
export function statMatches(stat: { diseaseCode: string; severityTier: string }, f: MapFilters) {
  return matchesFilters(stat.diseaseCode, stat.severityTier as SeverityKey, f);
}

/** Toggles one key; selecting every key collapses back to "All" (empty). */
export function toggleKey<T>(set: ReadonlySet<T>, key: T, allKeys: readonly T[]): Set<T> {
  const next = new Set(set);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  return allKeys.every((k) => next.has(k)) ? new Set() : next;
}

/** How many chips are selected (badge count); 0 = nothing filtered. */
export const activeCount = (f: MapFilters) => f.diseases.size + f.severities.size;

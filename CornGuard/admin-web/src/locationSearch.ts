// Location search for the outbreak map. Searches only the bundled Bukidnon boundary file
// (bukidnon-barangays.json): every barangay plus every municipality derived from it, so the
// search can never offer a place the map has no outline for.
// KEEP IN SYNC with the inline copy in android/app/src/main/assets/map/outbreak-map.html.

export type LocationEntry = {
  kind: 'barangay' | 'municipality';
  /** Unset for a municipality. */
  barangay?: string;
  municipality: string;
  /** Main line shown in the results. */
  label: string;
  /** barangay||municipality, or ||municipality for a municipality (barangay names repeat across towns). */
  key: string;
  /** Normalised strings matched against the query. */
  name: string;
  /** name without "City of" / "(Capital)", so "malaybalay" ranks the city first. */
  shortName: string;
  full: string;
};

export const NO_LOCATION_MESSAGE =
  'Location not available — only barangays and municipalities in Bukidnon can be searched.';

/** Lowercase, accents stripped (Ñ → n), whitespace collapsed. */
export function normalizeName(s: string): string {
  return s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

export function locationKey(barangay: string | undefined, municipality: string): string {
  return `${barangay ?? ''}||${municipality}`;
}

export function buildLocationIndex(
  features: { properties: { barangay: string; municipality: string } }[],
): LocationEntry[] {
  const index: LocationEntry[] = [];
  const seen = new Set<string>();
  const add = (barangay: string | undefined, municipality: string) => {
    const key = locationKey(barangay, municipality);
    if (seen.has(key)) return;
    seen.add(key);
    const name = barangay ?? municipality;
    index.push({
      kind: barangay ? 'barangay' : 'municipality',
      barangay,
      municipality,
      label: name,
      key,
      name: normalizeName(name),
      shortName: normalizeName(name.replace(/^city of /i, '').replace(/\(.*?\)/g, '')),
      full: normalizeName(barangay ? `${barangay} ${municipality}` : municipality),
    });
  };
  features.forEach((f) => {
    const { barangay, municipality } = f.properties;
    if (!municipality) return;
    add(undefined, municipality);
    if (barangay) add(barangay, municipality);
  });
  return index;
}

/** Lower is better; null = no match. */
function matchRank(entry: LocationEntry, q: string): number | null {
  const names = [entry.name, entry.shortName];
  if (names.some((n) => n === q)) return 0;
  if (names.some((n) => n.startsWith(q))) return 1;
  if (entry.name.split(' ').some((word) => word.startsWith(q))) return 2;
  if (entry.name.includes(q)) return 3;
  // e.g. "poblacion valencia", or just the town name for its barangays
  if (q.split(' ').every((word) => entry.full.includes(word))) return 4;
  return null;
}

export function searchLocations(index: LocationEntry[], query: string, limit = 12): LocationEntry[] {
  const q = normalizeName(query);
  if (!q) return [];
  return index
    .map((entry) => ({ entry, rank: matchRank(entry, q) }))
    .filter((m): m is { entry: LocationEntry; rank: number } => m.rank !== null)
    .sort(
      (a, b) =>
        a.rank - b.rank ||
        (a.entry.kind === b.entry.kind ? 0 : a.entry.kind === 'municipality' ? -1 : 1) ||
        a.entry.label.localeCompare(b.entry.label) ||
        a.entry.municipality.localeCompare(b.entry.municipality),
    )
    .slice(0, limit)
    .map((m) => m.entry);
}

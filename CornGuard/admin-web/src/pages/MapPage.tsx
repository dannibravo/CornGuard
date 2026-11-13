import { useQuery } from 'convex/react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, ZoomControl, useMap } from 'react-leaflet';
import { api } from '../convex';
// @ts-ignore
import barangays from '../assets/bukidnon-barangays.json';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { diseaseLabel } from '../labels';
import { Icon } from '../components/Icon';
import {
  buildLocationIndex,
  searchLocations,
  NO_LOCATION_MESSAGE,
  type LocationEntry,
} from '../locationSearch';
import {
  EMPTY_FILTERS,
  SEVERITY_OPTIONS,
  activeCount,
  matchesFilters,
  severityKey,
  statMatches,
  toggleKey,
  type MapFilters,
  type SeverityKey,
} from '../mapFilters';

// KEEP IN SYNC with the app's android/app/src/main/assets/map/outbreak-map.html — the two maps must agree on
// what a marker means. Update both or neither.
// Marker fill = severity tier of that barangay+disease (deep enough for a white letter).
const TIER_COLOR: Record<string, string> = {
  mild: '#059669',      // Emerald Green
  moderate: '#d97706',  // Amber
  severe: '#dc2626',    // Red
};

// Marker shape + letter = which disease. Replaces caps 3's coloured rings, which were hard to
// tell apart against the fill colour.
type Mark = { shape: 'circle' | 'square' | 'diamond'; letter: string };
const DISEASE_MARK: Record<string, Mark> = {
  northern_leaf_blight: { shape: 'circle', letter: 'B' },
  common_rust: { shape: 'square', letter: 'R' },
  gray_leaf_spot: { shape: 'diamond', letter: 'G' },
};
const UNKNOWN_MARK: Mark = { shape: 'circle', letter: '?' };
const NEUTRAL = '#4b5563';
// Reports 14-28 days old (stage "recent"): grey, small, no longer counted. Older ones aren't sent.
const RECENT_COLOR = '#6b7280';
const RECENT_SIZE = 18;
const DAY_MS = 24 * 60 * 60 * 1000;

// Size also tracks severity, so the map stays readable for red-green colourblind viewers
// instead of relying on hue alone.
const TIER_SIZE: Record<string, number> = { severe: 28, moderate: 24, mild: 20 };

/** One marker as inline SVG (24x24 box): severity-coloured shape, white outline, white letter. */
function markerSvg(mark: Mark, fill: string, size: number, halo: boolean) {
  const outline = 'stroke="#ffffff" stroke-width="2"';
  const shape = {
    circle: `<circle cx="12" cy="12" r="10" fill="${fill}" ${outline}/>`,
    square: `<rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="${fill}" ${outline}/>`,
    diamond: `<rect x="4.4" y="4.4" width="15.2" height="15.2" rx="2.5" fill="${fill}" ${outline} transform="rotate(45 12 12)"/>`,
  }[mark.shape];
  const haloSvg = halo ? `<circle class="halo" cx="12" cy="12" r="12" fill="${fill}"/>` : '';
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true">${haloSvg}${shape}` +
    `<text x="12" y="16" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="11" ` +
    `font-weight="800" fill="#ffffff">${mark.letter}</text></svg>`;
}

/** offset: [x, y] pixels, so reports at the same point sit side by side (see spreadOffsets). */
function markerIcon(diseaseCode: string, tier: string, recent: boolean, [dx, dy]: [number, number] = [0, 0]) {
  const size = recent ? RECENT_SIZE : (TIER_SIZE[tier] ?? TIER_SIZE.mild);
  const fill = recent ? RECENT_COLOR : (TIER_COLOR[tier] ?? NEUTRAL);
  return L.divIcon({
    className: recent ? 'cg-marker cg-marker-recent' : 'cg-marker',
    html: markerSvg(DISEASE_MARK[diseaseCode] ?? UNKNOWN_MARK, fill, size, !recent && tier === 'severe'),
    iconSize: [size, size],
    iconAnchor: [size / 2 - dx, size / 2 - dy],
    popupAnchor: [dx, dy - size / 2],
  });
}

/**
 * Reports at exactly the same coordinates (e.g. two manual picks of one barangay, which share its
 * centroid) would draw as a single dot. Fans each such group out in a small ring, in screen pixels
 * so it reads the same at every zoom. Returns one [dx, dy] per detection, in order.
 * KEEP IN SYNC with spreadOffsets in the app's outbreak-map.html.
 */
function spreadOffsets(detections: { latitude: number; longitude: number }[]): [number, number][] {
  const groups = new Map<string, number[]>();
  detections.forEach((d, i) => {
    const key = `${d.latitude},${d.longitude}`;
    groups.set(key, [...(groups.get(key) ?? []), i]);
  });
  const offsets = detections.map((): [number, number] => [0, 0]);
  groups.forEach((idx) => {
    if (idx.length < 2) return;
    const radius = 10 + 3 * idx.length;
    idx.forEach((i, k) => {
      const angle = (2 * Math.PI * k) / idx.length - Math.PI / 2;
      offsets[i] = [Math.round(radius * Math.cos(angle)), Math.round(radius * Math.sin(angle))];
    });
  });
  return offsets;
}

/**
 * Compact legend overlaid on the map itself, mirroring the mobile map's.
 * Built as a real Leaflet control rather than an absolutely-positioned div so it
 * participates in Leaflet's control layout and doesn't swallow map drags.
 */
function Legend() {
  const map = useMap();
  useEffect(() => {
    const control = new L.Control({ position: 'bottomright' });

    control.onAdd = () => {
      // Starts collapsed every time the map mounts; only a click on it expands it.
      const div = L.DomUtil.create('div', 'map-legend collapsed');
      const swatch = (mark: Mark, fill: string) => `<span class="swatch">${markerSvg(mark, fill, 16, false)}</span>`;
      const tierRow = (tier: string, label: string) =>
        `<div class="legend-row">${swatch({ shape: 'circle', letter: '' }, TIER_COLOR[tier])}${label}</div>`;
      const diseaseRows = Object.keys(DISEASE_MARK)
        .map((name) => `<div class="legend-row">${swatch(DISEASE_MARK[name], NEUTRAL)}${diseaseLabel(name)}</div>`)
        .join('');

      div.innerHTML = `
        <h4 class="legend-toggle">Legend &#9656;</h4>
        <div class="legend-body">
          <h4>Outbreak level</h4>
          ${tierRow('severe', 'Severe Outbreak')}
          ${tierRow('moderate', 'Moderate Threat')}
          ${tierRow('mild', 'Mild Detection')}
          <div class="legend-row" style="opacity:0.75">${swatch({ shape: 'circle', letter: '' }, RECENT_COLOR)}Reported 2–4 weeks ago</div>
          <div class="legend-row"><span class="swatch" style="box-sizing:border-box;border:2px solid ${MONITORED_COLOR};border-radius:3px"></span>Baseline set (click the area for its usual level)</div>
          <h4 class="legend-sub">Disease (shape)</h4>
          ${diseaseRows}
        </div>
      `;

      // Otherwise clicking/scrolling the legend pans and zooms the map underneath it
      L.DomEvent.disableClickPropagation(div);
      L.DomEvent.disableScrollPropagation(div);
      div.addEventListener('click', () => div.classList.toggle('collapsed'));
      return div;
    };

    control.addTo(map);
    return () => {
      control.remove();
    };
  }, [map]);

  return null;
}

function FitBounds({ data }: { data: any }) {
  const map = useMap();
  useEffect(() => {
    const geoJson = L.geoJSON(data);
    const bounds = geoJson.getBounds();
    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [20, 20] });
    }
  }, [data, map]);
  return null;
}

// Barangay outlines carry no severity fill: a filled polygon implied the whole area
// was infected, when only specific farms actually reported. fillOpacity is a hair
// above zero rather than exactly 0 because an SVG path with fill-opacity:0 isn't
// "painted", so pointer-events skip it and the click-for-details popup breaks.
// Module-level on purpose: react-leaflet re-applies `style` whenever its identity changes,
// which would wipe the search highlight on every live data update.
// Outline only (never a fill); teal once the barangay has a baseline (admin map only: the app keeps
// its map simple for farmers).
const BASE_STYLE: L.PathOptions = { fillColor: '#1f2937', fillOpacity: 0.06, color: '#374151', weight: 1 };
const MONITORED_COLOR = '#14b8a6';
const MONITORED_STYLE: L.PathOptions = { ...BASE_STYLE, color: MONITORED_COLOR, weight: 2 };
// Satellite mode: dark grey outlines vanish on imagery, so they go light; teal stays teal.
const SATELLITE_BASE_STYLE: L.PathOptions = { ...BASE_STYLE, color: '#f9fafb', opacity: 0.8, weight: 1.2 };
const SATELLITE_MONITORED_STYLE: L.PathOptions = { ...SATELLITE_BASE_STYLE, color: MONITORED_COLOR, opacity: 1, weight: 2 };

// ─── Basemap: Map (OSM, default) or Satellite (Esri imagery + place labels) ───
// Streamed like the OSM tiles; nothing bundled. KEEP IN SYNC with the basemap section of
// android/app/src/main/assets/map/outbreak-map.html.
type Basemap = 'map' | 'satellite';
const BASEMAP_KEY = 'cg.basemap';
const ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services/';
const ESRI_ATTRIBUTION =
  'Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community';

function loadBasemap(): Basemap {
  try {
    return localStorage.getItem(BASEMAP_KEY) === 'satellite' ? 'satellite' : 'map';
  } catch {
    return 'map';
  }
}

// Icons from Lucide (ISC licence): "satellite" and "map". Stroke follows the button's text colour.
const SVG_OPEN =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">';
const BASEMAP_ICON: Record<'Map' | 'Satellite', string> = {
  Satellite:
    SVG_OPEN +
    '<path d="M13 7 9 3 5 7l4 4"/><path d="m17 11 4 4-4 4-4-4"/><path d="m8 12 4 4 6-6-4-4Z"/><path d="m16 8 3-3"/><path d="M9 21a6 6 0 0 0-6-6"/></svg>',
  Map:
    SVG_OPEN +
    '<path d="M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z"/><path d="M15 5.764v15"/><path d="M9 3.236v15"/></svg>',
};

/** The button shows the icon of the view a click switches TO. */
function labelBasemapButton(button: HTMLAnchorElement, basemap: Basemap) {
  const next = basemap === 'satellite' ? 'Map' : 'Satellite';
  button.innerHTML = BASEMAP_ICON[next];
  button.title = `Show ${next.toLowerCase()} view`;
  button.setAttribute('aria-label', button.title);
}

/** One-button toggle above the zoom control; its label names the view a click switches TO. */
function BasemapToggle({ basemap, onChange }: { basemap: Basemap; onChange: (b: Basemap) => void }) {
  const map = useMap();
  const buttonRef = useRef<HTMLAnchorElement | null>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const basemapRef = useRef(basemap);
  basemapRef.current = basemap;

  useEffect(() => {
    // Added after <ZoomControl>, so Leaflet stacks it just above the zoom buttons.
    const control = new L.Control({ position: 'bottomleft' });
    control.onAdd = () => {
      const bar = L.DomUtil.create('div', 'leaflet-bar basemap-control');
      const button = L.DomUtil.create('a', 'basemap-toggle', bar) as HTMLAnchorElement;
      button.href = '#';
      button.setAttribute('role', 'button');
      L.DomEvent.disableClickPropagation(bar);
      L.DomEvent.on(button, 'click', (e) => {
        L.DomEvent.preventDefault(e);
        onChangeRef.current(basemapRef.current === 'satellite' ? 'map' : 'satellite');
      });
      labelBasemapButton(button, basemapRef.current);
      buttonRef.current = button;
      return bar;
    };
    control.addTo(map);
    return () => {
      control.remove();
      buttonRef.current = null;
    };
  }, [map]);

  useEffect(() => {
    if (buttonRef.current) labelBasemapButton(buttonRef.current, basemap);
  }, [basemap]);

  return null;
}

/** True once any disease in the barangay has a baseline (it is past "collecting data"). */
function hasBaseline(stats: any[] | undefined) {
  return (stats ?? []).some((s) => s.monitoringStatus !== 'collecting' && s.baselineFarms != null);
}

/** Each disease row that carries a baseline (the area is past "collecting data" for it). */
function baselineRows(stats: any[]) {
  return stats
    .filter((s) => s.monitoringStatus !== 'collecting' && s.baselineFarms != null)
    .sort((a, b) => diseaseLabel(a.diseaseCode).localeCompare(diseaseLabel(b.diseaseCode)));
}

// The searched-for barangay or municipality. Blue stays distinct from all three severity colours.
const HIGHLIGHT_STYLE: L.PathOptions = { color: '#2563eb', weight: 3, fillColor: '#2563eb', fillOpacity: 0.15 };

/**
 * Search field at the top of the Baselines panel: finds a barangay or municipality from the bundled
 * boundary file, outlines it and flies to it (same matching and behaviour as the app's map search in
 * outbreak-map.html). A barangay pick also tells the panel (`onPick`) so it can open that area's
 * baselines. Lives outside the MapContainer, so Leaflet's own keyboard handlers (arrow keys pan the
 * map) never see the input's events.
 */
function LocationSearch({
  map,
  layerRef,
  highlightRef,
  onPick,
  autoFocus = false,
}: {
  map: L.Map | null;
  layerRef: React.RefObject<L.GeoJSON>;
  // Shared with the Baselines panel's "Show on map", so only one area is ever outlined blue.
  highlightRef: React.MutableRefObject<L.Path[]>;
  onPick?: (entry: LocationEntry) => void;
  autoFocus?: boolean;
}) {
  const index = useMemo(() => buildLocationIndex((barangays as any).features), []);
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  // True while the box shows a picked place's label: refocusing it shouldn't re-search that label.
  const [picked, setPicked] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const highlighted = highlightRef;
  const pendingPopup = useRef<(() => void) | null>(null);
  const results = useMemo(() => searchLocations(index, query), [index, query]);
  const listId = 'location-search-results';

  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [autoFocus]);

  const clearHighlight = () => {
    highlighted.current.forEach((layer) => layerRef.current?.resetStyle(layer));
    highlighted.current = [];
    if (map && pendingPopup.current) map.off('moveend', pendingPopup.current);
    pendingPopup.current = null;
  };

  const select = (entry: LocationEntry) => {
    const group = layerRef.current;
    if (!map || !group) return;
    clearHighlight();
    const matches: L.Polygon[] = [];
    group.eachLayer((layer: any) => {
      const p = layer.feature?.properties;
      if (p && p.municipality === entry.municipality && (entry.kind === 'municipality' || p.barangay === entry.barangay)) {
        matches.push(layer);
      }
    });
    if (matches.length === 0) return;

    const bounds = L.latLngBounds([]);
    matches.forEach((layer) => {
      layer.setStyle(HIGHLIGHT_STYLE);
      layer.bringToFront();
      bounds.extend(layer.getBounds());
    });
    highlighted.current = matches;
    map.closePopup();
    const flyOptions = { padding: [40, 40] as L.PointTuple, maxZoom: 14 };
    if (entry.kind === 'barangay') {
      // Open the barangay's outbreak popup once the map has arrived. Not on the first moveend:
      // a window resize fires one mid-flight, and opening the popup then auto-pans and cancels
      // the flight.
      const targetZoom = Math.min(
        flyOptions.maxZoom,
        map.getBoundsZoom(bounds, false, L.point(flyOptions.padding).multiplyBy(2)),
      );
      const openPopup = () => {
        if (Math.abs(map.getZoom() - targetZoom) > 0.01) return;
        map.off('moveend', openPopup);
        pendingPopup.current = null;
        matches[0].openPopup(bounds.getCenter());
      };
      pendingPopup.current = openPopup;
      map.on('moveend', openPopup);
    }
    map.flyToBounds(bounds, flyOptions);

    setQuery(entry.kind === 'barangay' ? `${entry.label}, ${entry.municipality}` : entry.label);
    setPicked(true);
    setOpen(false);
    inputRef.current?.blur();
    onPick?.(entry);
  };

  // Empties the field; the highlight and popup go with it.
  const clear = () => {
    clearHighlight();
    map?.closePopup();
    setQuery('');
    setPicked(false);
    setOpen(false);
    inputRef.current?.focus();
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      if (results.length === 0) return;
      setOpen(true);
      setActive((i) => (i + (e.key === 'ArrowDown' ? 1 : results.length - 1)) % results.length);
    } else if (e.key === 'Enter') {
      const entry = results[active] ?? results[0];
      if (entry) select(entry);
      else setOpen(true);
    } else if (e.key === 'Escape') {
      if (open) setOpen(false);
      else if (query) clear();
    }
  };

  const showList = open && !picked && query.trim() !== '';

  return (
    <div className="panel-search">
      <label className="search-field panel-search-field">
        <Icon name="search" size={16} />
        <input
          ref={inputRef}
          type="text"
          value={query}
          placeholder="Search barangay or municipality"
          aria-label="Search barangay or municipality"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          autoComplete="off"
          onChange={(e) => {
            setQuery(e.target.value);
            setPicked(false);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => { if (!picked) setOpen(true); }}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
        />
        {query && (
          <button type="button" className="icon-btn icon-btn-sm panel-search-clear" aria-label="Clear search" onClick={clear}>
            <Icon name="close" size={15} />
          </button>
        )}
      </label>
      {showList && (
        <ul className="panel-search-results" role="listbox" id={listId}>
          {results.length === 0 ? (
            <li className="panel-search-empty">{NO_LOCATION_MESSAGE}</li>
          ) : (
            results.map((r, i) => (
              <li
                key={r.key}
                role="option"
                aria-selected={i === active}
                className={i === active ? 'active' : undefined}
                // mousedown, not click: keeps the input focused so onBlur doesn't close the list first
                onMouseDown={(e) => {
                  e.preventDefault();
                  select(r);
                }}
                onMouseEnter={() => setActive(i)}
              >
                <span className="panel-search-name">{r.label}</span>
                <span className="panel-search-meta">{r.kind === 'municipality' ? 'Municipality' : r.municipality}</span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

const DISEASE_KEYS = Object.keys(DISEASE_MARK);
const SEVERITY_KEYS = SEVERITY_OPTIONS.map((o) => o.key);
const severitySwatchColor = (key: SeverityKey) => (key === 'past' ? RECENT_COLOR : TIER_COLOR[key]);

/**
 * Disease + severity filter, top-right over the map. Like LocationSearch it sits outside the
 * MapContainer, so clicks on it never reach Leaflet. Same behaviour as the app's map
 * (outbreak-map.html filter control).
 */
function MapFilterPanel({
  filters,
  onChange,
  shown,
  total,
  open,
  setOpen,
}: {
  filters: MapFilters;
  onChange: (f: MapFilters) => void;
  shown: number;
  total: number;
  open: boolean;
  setOpen: (open: boolean) => void;
}) {
  const count = activeCount(filters);
  const swatch = (mark: Mark, fill: string) => (
    <span className="swatch" dangerouslySetInnerHTML={{ __html: markerSvg(mark, fill, 16, false) }} />
  );
  const chip = (key: string, pressed: boolean, onClick: () => void, content: React.ReactNode) => (
    <button key={key} type="button" className="map-filter-chip" aria-pressed={pressed} onClick={onClick}>
      {content}
    </button>
  );

  return (
    <div className="map-filter">
      <button
        type="button"
        className="map-filter-toggle"
        aria-label="Filter outbreak map"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="round" aria-hidden="true">
          <path d="M3.5 5h17l-6.5 7.5V19l-4 1.5v-8z" />
        </svg>
        {count > 0 && <span className="map-filter-badge">{count}</span>}
      </button>
      {open && (
        <div className="map-filter-panel">
          <h4>Disease</h4>
          <div className="map-filter-chips">
            {chip('all', filters.diseases.size === 0, () => onChange({ ...filters, diseases: new Set() }), 'All')}
            {DISEASE_KEYS.map((code) =>
              chip(
                code,
                filters.diseases.has(code),
                () => onChange({ ...filters, diseases: toggleKey(filters.diseases, code, DISEASE_KEYS) }),
                <>{swatch(DISEASE_MARK[code], NEUTRAL)}{diseaseLabel(code)}</>,
              ),
            )}
          </div>
          <h4>Outbreak level</h4>
          <div className="map-filter-chips">
            {chip('all', filters.severities.size === 0, () => onChange({ ...filters, severities: new Set() }), 'All')}
            {SEVERITY_OPTIONS.map(({ key, label }) =>
              chip(
                key,
                filters.severities.has(key),
                () => onChange({ ...filters, severities: toggleKey(filters.severities, key, SEVERITY_KEYS) }),
                <>{swatch({ shape: 'circle', letter: '' }, severitySwatchColor(key))}{label}</>,
              ),
            )}
          </div>
          <div className="map-filter-footer">
            <span>Showing {shown} of {total} reports</span>
            <button type="button" className="map-filter-reset" disabled={count === 0} onClick={() => onChange(EMPTY_FILTERS)}>
              Reset filters
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// Warm-up before outbreak detection starts (backend/convex/lib/outbreakRules.ts OUTBREAK_RULES).
const WARMUP_WEEKS = 4;
const WARMUP_SCANS = 10;

/** Progress of a barangay that is still collecting data for its baseline (outbreak status Undetermined). */
function undeterminedText(stat: any) {
  const weeks = stat?.monitoringSince ? Math.min(WARMUP_WEEKS, Math.floor((Date.now() - stat.monitoringSince) / (7 * DAY_MS))) : 0;
  return `Collecting data for this area's baseline (${weeks} of ${WARMUP_WEEKS} weeks, ` +
    `${Math.min(stat?.historyScans ?? 0, WARMUP_SCANS)} of ${WARMUP_SCANS} earlier scans)`;
}

type BaselineArea = {
  key: string;
  barangay: string;
  municipality: string;
  baselines: any[]; // one row per disease with a baseline; empty while gathering data
  collecting: any | null; // a "collecting" row, for the warm-up progress
};

const BASELINE_PANEL_KEY = 'cg.baselinePanelOpen';
const byName = (a: string, b: string) => a.localeCompare(b, undefined, { sensitivity: 'base', numeric: true });

/** Every barangay in the bundled boundary file (one entry even when it has several polygons),
 *  with its stats when it has any, A to Z by barangay then municipality. */
function baselineAreas(statsByBarangay: Map<string, any[]>): BaselineArea[] {
  const areas = new Map<string, BaselineArea>();
  (barangays as any).features.forEach((f: any) => {
    const { barangay, municipality } = f.properties ?? {};
    if (!barangay || !municipality) return;
    const key = `${barangay}||${municipality}`;
    if (areas.has(key)) return;
    const rows = statsByBarangay.get(key) ?? [];
    areas.set(key, {
      key,
      barangay,
      municipality,
      baselines: baselineRows(rows),
      collecting: rows.find((s) => s.monitoringStatus === 'collecting') ?? null,
    });
  });
  return [...areas.values()].sort((a, b) => byName(a.barangay, b.barangay) || byName(a.municipality, b.municipality));
}

/**
 * "Baselines" panel beside the map: every area's usual farms per week per disease, and the farm counts
 * that would make it Moderate or Severe. Collapses to a thin strip (the map widens); each area expands
 * on its own; the list scrolls independently of the page. The location search sits at its top: a
 * barangay picked there also opens in the list.
 */
function BaselinePanel({
  areas,
  loading,
  open,
  setOpen,
  onShow,
  search,
}: {
  areas: BaselineArea[];
  loading: boolean;
  open: boolean;
  setOpen: (open: boolean) => void;
  onShow: (area: BaselineArea) => void;
  search: Pick<React.ComponentProps<typeof LocationSearch>, 'map' | 'layerRef' | 'highlightRef'>;
}) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  // Set by the collapsed strip's search button: focus the search once the panel has opened.
  const [focusSearch, setFocusSearch] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const toggle = (key: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  const withBaseline = areas.filter((a) => a.baselines.length > 0).length;

  // A searched barangay: open its entry and scroll the list (only the list, never the page) to it.
  const reveal = (entry: LocationEntry) => {
    if (entry.kind !== 'barangay') return;
    const key = `${entry.barangay}||${entry.municipality}`;
    setExpanded((prev) => new Set(prev).add(key));
    requestAnimationFrame(() => {
      const list = listRef.current;
      const el = list?.querySelector<HTMLElement>(`[data-area-key="${CSS.escape(key)}"]`);
      if (list && el) list.scrollTo({ top: el.offsetTop - list.offsetTop - 8, behavior: 'smooth' });
    });
  };

  if (!open) {
    return (
      <aside className="card baseline-panel collapsed">
        <button
          type="button"
          className="baseline-panel-strip-search"
          aria-label="Search barangay or municipality"
          title="Search barangay or municipality"
          onClick={() => { setFocusSearch(true); setOpen(true); }}
        >
          <Icon name="search" size={18} />
        </button>
        <button type="button" className="baseline-panel-strip" aria-expanded={false} onClick={() => setOpen(true)}>
          <span aria-hidden="true">&#9666;</span>
          <span className="baseline-panel-strip-label">Baselines ({areas.length})</span>
        </button>
      </aside>
    );
  }

  return (
    <aside className="card baseline-panel">
      <button type="button" className="baseline-panel-header" aria-expanded={true} onClick={() => setOpen(false)}>
        <span>
          <span className="baseline-panel-title">Baselines</span>
          <span className="baseline-panel-sub">
            {areas.length} areas · {loading ? 'loading baselines…' : `${withBaseline} with a baseline`}
          </span>
        </span>
        <span aria-hidden="true" title="Collapse">&#9656;</span>
      </button>

      <LocationSearch {...search} onPick={reveal} autoFocus={focusSearch} />

      <div className="baseline-list" ref={listRef}>
        {areas.map((area) => {
          const isOpen = expanded.has(area.key);
          const bodyId = `baseline-${area.key.replace(/[^a-z0-9]+/gi, '-')}`;
          const hasBaseline = area.baselines.length > 0;
          const provisional = area.baselines.some((s) => s.monitoringStatus === 'provisional');
          const borrowed = area.baselines.find((s) => s.baselineSource === 'neighbours');
          const weeks = hasBaseline ? Math.max(...area.baselines.map((s) => s.dataWeeks ?? 0)) : 0;
          return (
            <div key={area.key} data-area-key={area.key} className={`baseline-area${isOpen ? ' open' : ''}`}>
              <button
                type="button"
                className="baseline-area-header"
                aria-expanded={isOpen}
                aria-controls={bodyId}
                onClick={() => toggle(area.key)}
              >
                <span className="baseline-area-name">
                  {area.barangay}
                  <span className="baseline-area-muni">{area.municipality}</span>
                </span>
                {!loading && (
                  <span className={`baseline-pill${hasBaseline ? ' set' : ''}`}>{hasBaseline ? 'Baseline' : 'Gathering'}</span>
                )}
                <span className="baseline-chevron" aria-hidden="true">&#9656;</span>
              </button>
              {isOpen && (
                <div id={bodyId} className="baseline-area-body">
                  {hasBaseline ? (
                    <>
                      <p className="baseline-meta">
                        From {borrowed ? `nearby areas (${borrowed.neighbourAreas ?? '?'} barangays), ` : ''}
                        {weeks} week{weeks === 1 ? '' : 's'}{provisional ? ' · provisional' : ''}
                      </p>
                      {area.baselines.map((s) => (
                        <div key={s.diseaseCode} className="baseline-disease">
                          <div className="baseline-disease-row">
                            <strong>{diseaseLabel(s.diseaseCode)}</strong>
                            <span>{Number(s.baselineFarms).toFixed(1)} farms / week</span>
                          </div>
                          <div className="baseline-meta">
                            This week {s.distinctFarms ?? 0}
                            {s.moderateAt != null && <> · Moderate at {s.moderateAt}</>}
                            {s.severeAt != null && <> · Severe at {s.severeAt}</>}
                            {s.excludedWeeks > 0 && (
                              <> · {s.excludedWeeks} outbreak week{s.excludedWeeks === 1 ? '' : 's'} left out</>
                            )}
                          </div>
                        </div>
                      ))}
                    </>
                  ) : (
                    <>
                      <p className="baseline-none">
                        {loading ? 'Loading…' : 'No baseline established: gathering data'}
                      </p>
                      {area.collecting && <p className="baseline-meta">{undeterminedText(area.collecting)}</p>}
                    </>
                  )}
                  <button type="button" className="baseline-show" onClick={() => onShow(area)}>
                    Show on map
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </aside>
  );
}

function loadBaselinePanelOpen() {
  try {
    return localStorage.getItem(BASELINE_PANEL_KEY) !== 'false';
  } catch {
    return true;
  }
}

export default function MapPage() {
  // includeBaselines: also areas with a baseline but no reports this week (for the Baselines panel).
  const stats = useQuery(api.barangayStats.getAllStats, { includeBaselines: true });
  // Individual verified reports — these are what get drawn as dots. The aggregated
  // stats below only supply each dot's severity tier now, not a polygon fill.
  const detections = useQuery(api.diagnosisRecords.mapReports);
  const [map, setMap] = useState<L.Map | null>(null);
  const barangayLayerRef = useRef<L.GeoJSON>(null);

  // Grouped per barangay for the polygon popup, and keyed per barangay+disease so each
  // dot picks up the tier for its own disease rather than the barangay's worst.
  const statsByBarangay = new Map<string, any[]>();
  const tierByBarangayDisease = new Map<string, string>();
  const statByBarangayDisease = new Map<string, any>();
  if (stats) {
    stats.forEach((s: any) => {
      // Keyed by barangay AND municipality: barangay names (e.g. Poblacion) repeat across towns.
      const area = `${s.barangay}||${s.municipality}`;
      if (!statsByBarangay.has(area)) statsByBarangay.set(area, []);
      statsByBarangay.get(area)!.push(s);
      tierByBarangayDisease.set(`${area}||${s.diseaseCode}`, s.severityTier);
      statByBarangayDisease.set(`${area}||${s.diseaseCode}`, s);
    });
  }
  // Read by the barangay popups when they open, so they show the latest stats.
  const statsRef = useRef(statsByBarangay);
  statsRef.current = statsByBarangay;

  // Barangays with a baseline get a teal outline. The style function reads a ref, so resetStyle()
  // after a search highlight keeps the teal; outlines are restyled only when the set changes.
  const monitored = [...statsByBarangay.keys()].filter((area) => hasBaseline(statsByBarangay.get(area))).sort();
  const monitoredKey = monitored.join('|');
  const monitoredRef = useRef(new Set<string>());
  monitoredRef.current = new Set(monitored);
  // Basemap is read through a ref too, so styleFeature keeps one identity (see BASE_STYLE's note).
  const [basemap, setBasemap] = useState<Basemap>(loadBasemap);
  const basemapRef = useRef(basemap);
  basemapRef.current = basemap;
  const changeBasemap = (next: Basemap) => {
    setBasemap(next);
    try {
      localStorage.setItem(BASEMAP_KEY, next);
    } catch {
      /* not remembered; fine */
    }
  };
  const styleFeature = useMemo(
    () => (feature?: any) => {
      const monitoredArea = monitoredRef.current.has(`${feature?.properties?.barangay}||${feature?.properties?.municipality}`);
      if (basemapRef.current === 'satellite') return monitoredArea ? SATELLITE_MONITORED_STYLE : SATELLITE_BASE_STYLE;
      return monitoredArea ? MONITORED_STYLE : BASE_STYLE;
    },
    [],
  );
  useEffect(() => {
    barangayLayerRef.current?.eachLayer((layer) => {
      const path = layer as L.Path & { feature?: any };
      if (path.options.color === HIGHLIGHT_STYLE.color) return; // leave the search highlight alone
      path.setStyle(styleFeature(path.feature));
    });
  }, [monitoredKey, basemap, styleFeature]);

  const [filters, setFilters] = useState<MapFilters>(EMPTY_FILTERS);
  const [filterOpen, setFilterOpen] = useState(false);
  // Clicking or dragging the map folds the panel away (the filters stay applied), as in the app.
  useEffect(() => {
    if (!map) return;
    const close = () => setFilterOpen(false);
    map.on('click dragstart', close);
    return () => {
      map.off('click dragstart', close);
    };
  }, [map]);
  // Read by the barangay popups when they open, like statsRef.
  const filtersRef = useRef(filters);
  filtersRef.current = filters;

  const reportCount = detections?.length ?? 0;
  // Each report with its severity key, narrowed to the filters. Offsets are computed on the
  // filtered list so same-point rings close up instead of leaving gaps.
  const visible: { d: any; sev: SeverityKey }[] = (detections ?? [])
    .map((d: any) => ({ d, sev: severityKey(d, tierByBarangayDisease) }))
    .filter(({ d, sev }: { d: any; sev: SeverityKey }) => matchesFilters(d.diseaseCode, sev, filters));
  const offsets = spreadOffsets(visible.map(({ d }) => d));
  const filtering = activeCount(filters) > 0;
  // An open popup was built for the old filters (or its marker is about to disappear).
  const changeFilters = (f: MapFilters) => {
    map?.closePopup();
    setFilters(f);
  };

  // Baselines panel beside the map: all ~464 barangays, rebuilt only when the stats change
  // (statsByBarangay is a new Map every render, but it is derived from `stats` alone).
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const panelAreas = useMemo(() => baselineAreas(statsByBarangay), [stats]);
  const [panelOpen, setPanelOpenState] = useState(loadBaselinePanelOpen);
  const setPanelOpen = (open: boolean) => {
    setPanelOpenState(open);
    try {
      localStorage.setItem(BASELINE_PANEL_KEY, String(open));
    } catch {
      /* not remembered; fine */
    }
  };
  // The map's container changes width when the panel opens or closes; Leaflet only tracks window resizes.
  useEffect(() => {
    map?.invalidateSize();
  }, [map, panelOpen]);
  const pendingAreaPopup = useRef<(() => void) | null>(null);
  // The blue-outlined area(s), from a search pick or the panel's "Show on map".
  const highlightedRef = useRef<L.Path[]>([]);
  // "Show on map": fly to the barangay and open its popup once there (same guard as LocationSearch:
  // a resize mid-flight fires an early moveend, and opening the popup then would cancel the flight).
  const showArea = (area: BaselineArea) => {
    const group = barangayLayerRef.current;
    if (!map || !group) return;
    const matches: L.Polygon[] = [];
    group.eachLayer((layer: any) => {
      const p = layer.feature?.properties;
      if (p && p.barangay === area.barangay && p.municipality === area.municipality) matches.push(layer);
    });
    if (matches.length === 0) return;
    // Blue outline, as a search pick gets; replaces whatever was highlighted before.
    highlightedRef.current.forEach((layer) => group.resetStyle(layer));
    const bounds = L.latLngBounds([]);
    matches.forEach((layer) => {
      layer.setStyle(HIGHLIGHT_STYLE);
      layer.bringToFront();
      bounds.extend(layer.getBounds());
    });
    highlightedRef.current = matches;
    if (pendingAreaPopup.current) map.off('moveend', pendingAreaPopup.current);
    map.closePopup();
    const padding: L.PointTuple = [40, 40];
    const targetZoom = Math.min(14, map.getBoundsZoom(bounds, false, L.point(padding).multiplyBy(2)));
    const openPopup = () => {
      if (Math.abs(map.getZoom() - targetZoom) > 0.01) return;
      map.off('moveend', openPopup);
      pendingAreaPopup.current = null;
      matches[0].openPopup(bounds.getCenter());
    };
    pendingAreaPopup.current = openPopup;
    map.on('moveend', openPopup);
    map.flyToBounds(bounds, { padding, maxZoom: 14 });
  };

  return (
    <div className="map-page">
      {/* The page title lives in the app header; this line keeps the live plotted count. */}
      <p className="map-summary" aria-live="polite">
        <span className="map-summary-dot" aria-hidden="true" />
        {detections
          ? (filtering ? <><strong>{visible.length}</strong> of {reportCount} verified reports plotted</> : <><strong>{reportCount}</strong> verified reports plotted</>)
          : 'Loading reports…'}
      </p>

      <div className="map-layout">
      <div className="card map-card">
        <MapFilterPanel
          filters={filters}
          onChange={changeFilters}
          shown={visible.length}
          total={reportCount}
          open={filterOpen}
          setOpen={setFilterOpen}
        />
        {/* Not while the panel is open: it already says "Showing 0 of N", and would cover this. */}
        {detections && filtering && visible.length === 0 && !filterOpen && (
          <div className="map-filter-empty" role="status">
            No reports match these filters.{' '}
            <button type="button" onClick={() => changeFilters(EMPTY_FILTERS)}>Reset</button>
          </div>
        )}
        <MapContainer
          ref={setMap}
          center={[8.15, 125.13]}
          zoom={9}
          zoomControl={false}
          style={{ height: '100%', width: '100%', background: '#f4f1ea' }}
        >
          {/* Zoom bottom-left, as on the app's map. */}
          <ZoomControl position="bottomleft" />
          {/* Voyager, not light_all: the plain grey basemap dropped roads and admin
              boundaries almost entirely. This keeps the OSM look (green parks, orange
              highways, visible municipal borders) while staying light. */}
          {basemap === 'satellite' ? (
            <>
              {/* Esri's URL order is {y}/{x}. maxNativeZoom 18: zoom 19 upscales instead of
                  showing "Map data not yet available" tiles over rural areas. */}
              <TileLayer
                key="esri-imagery"
                url={`${ESRI}World_Imagery/MapServer/tile/{z}/{y}/{x}`}
                attribution={ESRI_ATTRIBUTION}
                maxNativeZoom={18}
                maxZoom={19}
              />
              <TileLayer
                key="esri-labels"
                url={`${ESRI}Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`}
                maxNativeZoom={18}
                maxZoom={19}
              />
            </>
          ) : (
            <TileLayer
              key="osm"
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            />
          )}
          <BasemapToggle basemap={basemap} onChange={changeBasemap} />
          <FitBounds data={barangays as any} />
          <Legend />

          <GeoJSON
            ref={barangayLayerRef}
            data={barangays as any}
            style={styleFeature}
            onEachFeature={(feature, layer) => {
              const barangayName = feature.properties.barangay;
              const municipalityName = feature.properties.municipality;
              // Built when opened: onEachFeature only runs once, before the stats query has loaded.
              layer.bindPopup(() => {
                const allStats = statsRef.current.get(`${barangayName}||${municipalityName}`) ?? [];
                // Rows with no reports this week only carry the baseline (see baselineHtml).
                const reported = allStats.filter((s) => s.rawReportCount > 0);
                const bStats = reported.filter((s) => statMatches(s, filtersRef.current));

                let popupContent = `
                  <div style="font-family: sans-serif; color: #fff; background: #222; padding: 4px; border-radius: 4px;">
                    <b style="color: #4ade80; font-size: 15px;">${barangayName}</b><br/>
                    <span style="color: #aaa; font-size: 12px;">${municipalityName}</span><br/>
                `;

                if (bStats && bStats.length > 0) {
                  bStats.forEach((stat) => {
                    popupContent += `
                      <hr style="border-color: #333; margin: 8px 0;" />
                      <span style="font-size: 13px;">
                        <strong>Active Disease:</strong> ${diseaseLabel(stat.diseaseCode)}<br/>
                        ${stat.monitoringStatus === 'collecting'
                          ? `<strong>Outbreak status:</strong> <span style="color: #9ca3af; font-weight: bold;">UNDETERMINED</span><br/>
                             <span style="color: #9ca3af;">${undeterminedText(stat)}</span><br/>`
                          : `<strong>Outbreak level:</strong> <span style="color: ${TIER_COLOR[stat.severityTier]}; text-transform: uppercase; font-weight: bold;">${stat.severityTier}</span><br/>
                             ${stat.baselineFarms != null ? `<strong>Usual:</strong> ${stat.baselineFarms.toFixed(1)} farms / week${stat.monitoringStatus === 'provisional' ? ' <span style="color: #9ca3af;">(provisional)</span>' : ''}<br/>` : ''}
                             <strong>Moderate at / Severe at:</strong> ${stat.moderateAt ?? '—'} / ${stat.severeAt ?? '—'} farms<br/>`}
                        <strong>Farms (7 days):</strong> ${stat.distinctFarms}${stat.lastWeekFarms != null ? ` <span style="color: #9ca3af;">· last week ${stat.lastWeekFarms}</span>` : ''}<br/>
                        <strong>Total Reports:</strong> ${stat.rawReportCount}
                      </span>
                    `;
                  });
                } else {
                  popupContent += `
                    <hr style="border-color: #333; margin: 8px 0;" />
                    <span style="color: #888; font-size: 13px;">${reported.length > 0 ? 'No reports match the current filters.' : 'No active disease reports this week.'}</span>
                  `;
                }

                // Usual levels moved to the Baselines panel beside the map; just point there.
                if (hasBaseline(allStats)) {
                  popupContent += `<hr style="border-color: #333; margin: 8px 0;" />
                    <span style="color: ${MONITORED_COLOR}; font-size: 12px;">Baseline set · see the Baselines panel for usual levels</span>`;
                }
                popupContent += `</div>`;
                return popupContent;
              });
            }}
          />

          {/* Report dots, after the polygons so they stack above them */}
          {visible.map(({ d, sev }, i: number) => {
            const recent = sev === 'past';
            const tier = recent ? 'mild' : sev;
            const daysAgo = Math.max(0, Math.round((Date.now() - d.capturedAt) / DAY_MS));
            const stat = statByBarangayDisease.get(`${d.barangay}||${d.municipality}||${d.diseaseCode}`);
            const undetermined = !recent && stat?.monitoringStatus === 'collecting';
            return (
              <Marker
                key={d.recordId}
                position={[d.latitude, d.longitude]}
                icon={markerIcon(d.diseaseCode, tier, recent, offsets[i])}
                // Severe on top, then moderate, then mild; faded past reports always underneath
                zIndexOffset={recent ? -1000 : tier === 'severe' ? 2000 : tier === 'moderate' ? 1000 : 0}
              >
                <Popup>
                  <div style={{ fontFamily: 'sans-serif', minWidth: '180px' }}>
                    <b style={{ color: '#4ade80', fontSize: '15px' }}>{diseaseLabel(d.diseaseCode)}</b><br />
                    <span style={{ color: '#888', fontSize: '12px' }}>
                      {d.barangay || 'Unresolved area'}{d.municipality ? `, ${d.municipality}` : ''}
                    </span>
                    <hr style={{ borderColor: '#333', margin: '8px 0' }} />
                    <span style={{ fontSize: '13px' }}>
                      {recent ? (
                        <><span style={{ color: '#6b7280' }}>
                          Reported {daysAgo} days ago · no longer counted in the outbreak level
                        </span><br /></>
                      ) : undetermined ? (
                        <><strong>Outbreak status:</strong>{' '}
                        <span style={{ color: '#9ca3af', fontWeight: 'bold' }}>UNDETERMINED</span><br />
                        <span style={{ color: '#6b7280' }}>{undeterminedText(stat)}</span><br /></>
                      ) : (
                        <><strong>Outbreak level:</strong>{' '}
                        <span style={{ color: TIER_COLOR[tier], textTransform: 'uppercase', fontWeight: 'bold' }}>
                          {tier}
                        </span><br /></>
                      )}
                      <strong>Reported by:</strong> {d.farmerName ?? 'Farmer'}<br />
                      <strong>Confidence:</strong> {Math.round(d.confidence * 100)}%<br />
                      <strong>Reported:</strong> {new Date(d.capturedAt).toLocaleDateString()}
                    </span>
                  </div>
                </Popup>
              </Marker>
            );
          })}
        </MapContainer>
      </div>
      <BaselinePanel
        areas={panelAreas}
        loading={stats === undefined}
        open={panelOpen}
        setOpen={setPanelOpen}
        onShow={showArea}
        search={{ map, layerRef: barangayLayerRef, highlightRef: highlightedRef }}
      />
      </div>

      {/* Colour meanings now live in the on-map legend overlay. This card keeps only what
          the overlay has no room for: the thresholds behind each tier. */}
      <div className="card section">
        <div className="card-header">
          <h2 className="card-title">How the outbreak level is assigned</h2>
        </div>
        <div className="card-body prose-sm">
          <p>
            Each marker is one verified report at its recorded coordinates. Its colour shows the
            outbreak level of that barangay <em>for that disease</em> (not how badly a leaf is affected);
            its shape and letter show which disease (circle B = Northern Leaf Blight, square R = Common
            Rust, diamond G = Gray Leaf Spot). Following the WHO and DA-BPI definitions of an outbreak
            (disease above its normal level), each barangay first <strong>collects data</strong>: until it has
            at least 4 weeks since its first verified scan and 10 verified scans of any kind (healthy included),
            its outbreak status is <strong>Undetermined</strong> and no alerts are sent. After that, the
            distinct farms reporting a disease in the last <strong>7 days</strong> are compared with the
            barangay's <strong>baseline</strong> for that disease: the average farms per week over up to 12
            earlier weeks (weeks with no scans at all are skipped; "provisional" until 12 weeks). Each report
            is also weighted by model confidence (80%+ = 1.0, 60–79% = 0.6, below 60% = 0.3). After 7 days a
            report turns into a faded grey marker (no longer counted); after 28 days it disappears. Healthy
            scans and suspended accounts are never shown. Method and sources: docs/OUTBREAK_DETECTION_METHOD.md.
          </p>
          <ul className="tier-list">
            <li>
              <strong style={{ color: TIER_COLOR.severe }}>Severe</strong> — at least <strong>3×</strong> the
              baseline and at least 5 distinct farms, weighted score &ge; 4.0. Sends a potential-outbreak warning
              automatically.
            </li>
            <li>
              <strong style={{ color: TIER_COLOR.moderate }}>Moderate</strong> — at least <strong>2×</strong> the
              baseline and at least 3 distinct farms, weighted score &ge; 2.0. Sends an awareness notice
              automatically. The distinct-farm minimums stop a single user's repeat scans, or a tiny baseline,
              from raising the level.
            </li>
            <li>
              <strong style={{ color: TIER_COLOR.mild }}>Mild</strong> — verified reports below those levels.
            </li>
            <li>
              <strong style={{ color: '#9ca3af' }}>Undetermined</strong> — the barangay is still collecting data
              (shown with the Mild colour on the map).
            </li>
            <li>
              <strong style={{ color: MONITORED_COLOR }}>Teal outline</strong> — the barangay has a baseline. The
              <strong> Baselines</strong> panel beside the map lists each area's usual farms per week per disease and
              the farm counts that would make it Moderate or Severe (shown here only; the farmers' app keeps its map
              simple).
            </li>
          </ul>
          <p className="prose-footnote">
            Baseline 0–1 farm/week → Moderate at 3, Severe at 5 · 2 → 4 / 6 · 3 → 6 / 9 · 5 → 10 / 15.
            Rejecting a scan in Review removes it on the next recompute, so the level falls back
            automatically. All thresholds are development values pending expert approval (D-07).
          </p>
        </div>
      </div>
    </div>
  );
}

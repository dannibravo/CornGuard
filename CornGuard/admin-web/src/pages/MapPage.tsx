import { useQuery } from 'convex/react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, ZoomControl, useMap } from 'react-leaflet';
import { api } from '../convex';
// More detailed than the app's copy (philippines-json-maps "hires", same barangay names); rebuilt
// with scripts/build-boundaries.mjs. Source and credits: THIRD_PARTY_NOTICES.md.
// @ts-ignore
import barangays from '../assets/bukidnon-barangays.json';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { diseaseLabel } from '../labels';
import {
  HEAT_FILL_OPACITY,
  HATCH,
  HeatLayer,
  RAMP,
  areaHeat,
  currentTheme,
  rampColor,
  strokeFor,
  type AreaHeat,
  type Theme,
} from '../heatmap';
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

// Disease markers are an admin-only layer (off by default); the farmers' app shows heat only.
// Marker fill = severity tier of that barangay+disease, from the "earth" level badges (same stops as
// the heat ramp, so a marker and its barangay agree). Letter colour keeps 4.5:1 on each fill.
const TIER_COLOR: Record<string, string> = {
  mild: '#e3b85a',
  moderate: '#d08a26',
  severe: '#b04f27',
};
const TIER_LETTER: Record<string, string> = { mild: '#1e261c', moderate: '#1e261c', severe: '#ffffff' };

// Marker shape + letter = which disease. Replaces caps 3's coloured rings, which were hard to
// tell apart against the fill colour.
type Mark = { shape: 'circle' | 'square' | 'diamond'; letter: string };
const DISEASE_MARK: Record<string, Mark> = {
  northern_leaf_blight: { shape: 'circle', letter: 'B' },
  common_rust: { shape: 'square', letter: 'R' },
  gray_leaf_spot: { shape: 'diamond', letter: 'G' },
};
const UNKNOWN_MARK: Mark = { shape: 'circle', letter: '?' };
const NEUTRAL = '#5b5f52';
// Reports 14-28 days old (stage "recent"): grey, small, no longer counted. Older ones aren't sent.
const RECENT_COLOR = '#696a5b';
const RECENT_SIZE = 18;
const DAY_MS = 24 * 60 * 60 * 1000;

// Size also tracks severity, so the map stays readable for red-green colourblind viewers
// instead of relying on hue alone.
const TIER_SIZE: Record<string, number> = { severe: 28, moderate: 24, mild: 20 };

/** One marker as inline SVG (24x24 box): severity-coloured shape, white outline, readable letter. */
function markerSvg(mark: Mark, fill: string, size: number, letterColor = '#ffffff') {
  const outline = 'stroke="#ffffff" stroke-width="2"';
  const shape = {
    circle: `<circle cx="12" cy="12" r="10" fill="${fill}" ${outline}/>`,
    square: `<rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="${fill}" ${outline}/>`,
    diamond: `<rect x="4.4" y="4.4" width="15.2" height="15.2" rx="2.5" fill="${fill}" ${outline} transform="rotate(45 12 12)"/>`,
  }[mark.shape];
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true">${shape}` +
    `<text x="12" y="16" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="11" ` +
    `font-weight="700" fill="${letterColor}">${mark.letter}</text></svg>`;
}

/** offset: [x, y] pixels, so reports at the same point sit side by side (see spreadOffsets). */
function markerIcon(diseaseCode: string, tier: string, recent: boolean, [dx, dy]: [number, number] = [0, 0]) {
  const size = recent ? RECENT_SIZE : (TIER_SIZE[tier] ?? TIER_SIZE.mild);
  const fill = recent ? RECENT_COLOR : (TIER_COLOR[tier] ?? NEUTRAL);
  return L.divIcon({
    className: recent ? 'cg-marker cg-marker-recent' : 'cg-marker',
    html: markerSvg(DISEASE_MARK[diseaseCode] ?? UNKNOWN_MARK, fill, size, recent ? '#ffffff' : TIER_LETTER[tier] ?? '#ffffff'),
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
 * Legend overlaid on the map (a real Leaflet control, so it doesn't swallow map drags): the fixed
 * heat scale with the Moderate / Severe cut-offs, "collecting data" hatching, the baseline outline
 * and, when that layer is on, the disease markers.
 */
function Legend({ theme, showHeat, showMarkers }: { theme: Theme; showHeat: boolean; showMarkers: boolean }) {
  const map = useMap();
  useEffect(() => {
    const control = new L.Control({ position: 'bottomright' });

    control.onAdd = () => {
      // Starts collapsed every time the map mounts; only a click on it expands it.
      const div = L.DomUtil.create('div', 'map-legend collapsed');
      const swatch = (mark: Mark, fill: string, letter?: string) =>
        `<span class="swatch">${markerSvg(mark, fill, 16, letter)}</span>`;
      const stops = RAMP[theme].map(([t, c]) => `${c} ${t * 100}%`).join(', ');
      const hatch = HATCH[theme];
      const heatRows = showHeat ? `
          <h4>Outbreak level · last 7 days</h4>
          <div class="legend-ramp" style="background: linear-gradient(90deg, ${stops})"></div>
          <div class="legend-ticks">
            <span class="edge-left" style="left:0">Mild</span>
            <span style="left:50%">Moderate<br>2×</span>
            <span style="left:80%">Severe<br>3×</span>
          </div>
          <div class="legend-row"><span class="swatch" style="box-sizing:border-box;border:1px dashed ${hatch.line};background:repeating-linear-gradient(135deg, ${hatch.line} 0 1px, ${hatch.bg} 1px 5px)"></span>Collecting data</div>` : '';
      const markerRows = showMarkers ? `
          <h4 class="legend-sub">Disease markers</h4>
          ${(['severe', 'moderate', 'mild'] as const).map((tier) =>
            `<div class="legend-row">${swatch({ shape: 'circle', letter: '' }, TIER_COLOR[tier])}${tier[0].toUpperCase() + tier.slice(1)}</div>`).join('')}
          <div class="legend-row" style="opacity:0.75">${swatch({ shape: 'circle', letter: '' }, RECENT_COLOR)}Reported 2–4 weeks ago</div>
          ${Object.keys(DISEASE_MARK).map((name) =>
            `<div class="legend-row">${swatch(DISEASE_MARK[name], NEUTRAL)}${diseaseLabel(name)}</div>`).join('')}` : '';

      div.innerHTML = `
        <h4 class="legend-toggle">Legend &#9656;</h4>
        <div class="legend-body">
          ${heatRows}
          <div class="legend-row"><span class="swatch" style="box-sizing:border-box;border:2px solid ${monitoredColor(theme)};border-radius:3px"></span>Baseline set (click the area for details)</div>
          ${markerRows}
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
  }, [map, theme, showHeat, showMarkers]);

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

// Polygon styles. With the heat layer on, a barangay is filled by its heat this week on the fixed
// scale (see heatmap.ts); otherwise it is outline only. fill stays on (transparent) so a click
// anywhere in the barangay opens its popup. Barangays with a baseline get a forest outline (admin map
// only). Built through a ref-reading function with one identity: react-leaflet re-applies `style`
// whenever its identity changes, which would wipe the search highlight on every live data update.
const OUTLINE: Record<Theme, string> = { light: '#cfc5ae', dark: '#3d4536' };
const OUTLINE_ON_TILES = '#5b5f52';
const monitoredColor = (theme: Theme) => (theme === 'dark' ? '#9ccb82' : '#2f5d3a');

function polygonStyle(opts: {
  heat: AreaHeat;
  monitored: boolean;
  theme: Theme;
  satellite: boolean;
  onTiles: boolean;
  showHeat: boolean;
}): L.PathOptions {
  const { heat, monitored, theme, satellite, onTiles, showHeat } = opts;
  if (showHeat && heat && 'heat' in heat) {
    const fill = rampColor(heat.heat, theme);
    return { fill: true, fillColor: fill, fillOpacity: HEAT_FILL_OPACITY, color: strokeFor(fill, theme), opacity: 1, weight: 1, dashArray: undefined };
  }
  if (showHeat && heat && 'collecting' in heat) {
    return { fill: true, fillOpacity: 0, color: HATCH[theme].line, opacity: 1, weight: 1, dashArray: '4 3' };
  }
  if (monitored) {
    return { fill: true, fillOpacity: 0, color: satellite ? '#9ccb82' : monitoredColor(theme), opacity: 1, weight: 2, dashArray: undefined };
  }
  if (satellite) return { fill: true, fillOpacity: 0, color: '#f6f3ea', opacity: 0.8, weight: 1.2, dashArray: undefined };
  return {
    fill: true,
    fillOpacity: 0,
    color: onTiles ? OUTLINE_ON_TILES : OUTLINE[theme],
    opacity: onTiles ? 0.7 : 1,
    weight: onTiles ? 1.2 : 0.8,
    dashArray: undefined,
  };
}

/** Light / dark, following the system like the rest of admin.css. */
function useTheme(): Theme {
  const [theme, setTheme] = useState<Theme>(currentTheme);
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!mq) return;
    const onChange = () => setTheme(currentTheme());
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return theme;
}

const MAX_ZOOM = 16;

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

// The searched-for (or "Show on map") barangay or municipality: an outline only (no fill, so the
// heat stays visible). Picked for the map underneath, not the UI theme: a light blue on the light
// street tiles in dark mode was nearly invisible. Strong blue on the map, bright yellow on satellite;
// both stay distinct from the amber-to-red heat ramp.
function highlightStyle(satellite: boolean): L.PathOptions {
  return { color: satellite ? '#ffd60a' : '#1a56db', weight: 4, opacity: 1, dashArray: undefined };
}

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
  highlightStyle: styleForHighlight,
  onPick,
  autoFocus = false,
}: {
  map: L.Map | null;
  layerRef: React.RefObject<L.GeoJSON>;
  // Shared with the Baselines panel's "Show on map", so only one area is ever outlined.
  highlightRef: React.MutableRefObject<L.Path[]>;
  /** The outline for the current basemap (see highlightStyle). */
  highlightStyle: () => L.PathOptions;
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
      layer.setStyle(styleForHighlight());
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
    <span className="swatch" dangerouslySetInnerHTML={{ __html: markerSvg(mark, fill, 16) }} />
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

/** "1 farm" / "3 farms". */
function farmsText(n: number) {
  return `${n} farm${n === 1 ? '' : 's'}`;
}

/**
 * A baseline in plain words, never as a decimal: "0.4 farms / week" read like a count of
 * infected farms, so show "less than 1 a week" / "about 2 a week" instead.
 */
function usualText(baselineFarms: number) {
  const b = Number(baselineFarms);
  return b < 1 ? 'less than 1 a week' : `about ${Math.round(b)} a week`;
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
  search: Pick<React.ComponentProps<typeof LocationSearch>, 'map' | 'layerRef' | 'highlightRef' | 'highlightStyle'>;
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
            {loading ? 'Loading normal levels…' : `${withBaseline} of ${areas.length} areas ${withBaseline === 1 ? 'has' : 'have'} a normal level set`}
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
                        Based on {weeks} week{weeks === 1 ? '' : 's'} of reports
                        {borrowed ? ` from ${borrowed.neighbourAreas ?? '?'} nearby barangays` : ''}
                        {provisional ? ' · may still change' : ''}
                      </p>
                      {area.baselines.map((s) => (
                        <div key={s.diseaseCode} className="baseline-disease">
                          <div className="baseline-disease-row">
                            <strong>{diseaseLabel(s.diseaseCode)}</strong>
                            <span>Normal: {usualText(s.baselineFarms)}</span>
                          </div>
                          <div className="baseline-meta">
                            This week: {farmsText(s.distinctFarms ?? 0)}
                            {s.moderateAt != null && <> · Moderate if {s.moderateAt}+ farms</>}
                            {s.severeAt != null && <> · Severe if {s.severeAt}+ farms</>}
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
  // Admin-only query: exact coordinates + reporter name, for investigation. The general
  // diagnosisRecords.mapReports (used by the farmer-facing app) no longer returns either —
  // see its privacy note in backend/convex/diagnosisRecords.ts.
  const detections = useQuery(api.admin.mapReports);
  const [map, setMap] = useState<L.Map | null>(null);
  const barangayLayerRef = useRef<L.GeoJSON>(null);
  // The blue-outlined area(s), from a search pick or the panel's "Show on map".
  const highlightedRef = useRef<L.Path[]>([]);
  const theme = useTheme();
  // Layers: heat on, disease markers off (admin-only detail), street map on.
  const [showHeat, setShowHeat] = useState(true);
  const [showMarkers, setShowMarkers] = useState(false);
  const [streetTiles, setStreetTiles] = useState(true);

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
  // Basemap is read through a ref too, so styleFeature keeps one identity (see polygonStyle's note).
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
  const [filters, setFilters] = useState<MapFilters>(EMPTY_FILTERS);
  // Heat per barangay for the current filters (disease + level), on the fixed scale.
  const heatByArea = useMemo(() => {
    const rows = new Map<string, any[]>();
    (stats ?? []).forEach((s: any) => {
      if (s.rawReportCount <= 0 || !statMatches(s, filters)) return;
      const key = `${s.barangay}||${s.municipality}`;
      rows.set(key, [...(rows.get(key) ?? []), s]);
    });
    const heat = new Map<string, AreaHeat>();
    rows.forEach((r, key) => heat.set(key, areaHeat(r)));
    return heat;
  }, [stats, filters]);
  const heatKey = [...heatByArea.entries()].map(([k, h]) => `${k}:${h && 'heat' in h ? h.heat.toFixed(3) : 'c'}`).join('|');
  // Street tiles at every zoom when ticked: a ticked box with a blank province view looked broken.
  const onTiles = basemap === 'map' && streetTiles;
  // Everything styleFeature reads, through a ref (one function identity; see polygonStyle's note).
  const styleInputs = useRef({ heatByArea, theme, onTiles, showHeat });
  styleInputs.current = { heatByArea, theme, onTiles, showHeat };
  const styleFeature = useMemo(
    () => (feature?: any) => {
      const key = `${feature?.properties?.barangay}||${feature?.properties?.municipality}`;
      const { heatByArea: heat, theme: t, onTiles: tiles, showHeat: heatOn } = styleInputs.current;
      return polygonStyle({
        heat: heat.get(key) ?? null,
        monitored: monitoredRef.current.has(key),
        theme: t,
        satellite: basemapRef.current === 'satellite',
        onTiles: tiles,
        showHeat: heatOn,
      });
    },
    [],
  );
  useEffect(() => {
    barangayLayerRef.current?.eachLayer((layer) => {
      const path = layer as L.Path & { feature?: any };
      path.setStyle(styleFeature(path.feature));
      // Keep the search highlight's outline on top of the new fill.
      if (highlightedRef.current.includes(path)) path.setStyle(highlightStyle(basemapRef.current === 'satellite'));
    });
  }, [monitoredKey, basemap, styleFeature, heatKey, theme, onTiles, showHeat]);

  // Hatching + centroid glows, under the polygons.
  const heatLayerRef = useRef<HeatLayer | null>(null);
  useEffect(() => {
    if (!map || !showHeat) return;
    const layer = new HeatLayer(
      () => {
        const out: any[] = [];
        barangayLayerRef.current?.eachLayer((l) => out.push(l));
        return out;
      },
      (l) => styleInputs.current.heatByArea.get(`${l.feature?.properties?.barangay}||${l.feature?.properties?.municipality}`) ?? null,
      () => styleInputs.current.theme,
    );
    layer.addTo(map);
    heatLayerRef.current = layer;
    return () => {
      layer.remove();
      heatLayerRef.current = null;
    };
  }, [map, showHeat]);
  useEffect(() => {
    heatLayerRef.current?.redraw();
  }, [heatKey, theme]);

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
    // Same outline as a search pick gets; replaces whatever was highlighted before.
    highlightedRef.current.forEach((layer) => group.resetStyle(layer));
    const bounds = L.latLngBounds([]);
    matches.forEach((layer) => {
      layer.setStyle(highlightStyle(basemapRef.current === 'satellite'));
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
      <div className="map-toolbar">
        <p className="map-summary" aria-live="polite">
          <span className="map-summary-dot" aria-hidden="true" />
          {detections
            ? (filtering
              ? <><strong>{visible.length}</strong> of {reportCount} verified reports match · last 28 days, in total</>
              : <><strong>{reportCount}</strong> verified report{reportCount === 1 ? '' : 's'} · last 28 days, in total</>)
            : 'Loading reports…'}
          {showMarkers ? ' · shown as markers' : ''}
        </p>
        <fieldset className="map-layers">
          <legend className="sr-only">Map layers</legend>
          <label><input type="checkbox" checked={showHeat} onChange={(e) => setShowHeat(e.target.checked)} /> Heat</label>
          <label><input type="checkbox" checked={showMarkers} onChange={(e) => setShowMarkers(e.target.checked)} /> Disease markers</label>
          <label title="Show the OpenStreetMap street map under the barangay boundaries.">
            <input type="checkbox" checked={streetTiles} onChange={(e) => setStreetTiles(e.target.checked)} /> Street map
          </label>
        </fieldset>
      </div>

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
          maxZoom={MAX_ZOOM}
          preferCanvas
          zoomControl={false}
          style={{ height: '100%', width: '100%', background: 'var(--surface-sunken)' }}
        >
          {/* Zoom bottom-left, as on the app's map. */}
          <ZoomControl position="bottomleft" />
          {/* Satellite stays on demand (the toggle). Street tiles whenever "Street map" is ticked;
              unticked, the map is the bundled boundaries alone, with no tile requests. */}
          {basemap === 'satellite' ? (
            <>
              {/* Esri's URL order is {y}/{x}. */}
              <TileLayer
                key="esri-imagery"
                url={`${ESRI}World_Imagery/MapServer/tile/{z}/{y}/{x}`}
                attribution={ESRI_ATTRIBUTION}
                maxNativeZoom={MAX_ZOOM}
                maxZoom={MAX_ZOOM}
              />
              <TileLayer
                key="esri-labels"
                url={`${ESRI}Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`}
                maxNativeZoom={MAX_ZOOM}
                maxZoom={MAX_ZOOM}
              />
            </>
          ) : onTiles ? (
            <TileLayer
              key="osm"
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              maxZoom={MAX_ZOOM}
            />
          ) : null}
          <BasemapToggle basemap={basemap} onChange={changeBasemap} />
          <FitBounds data={barangays as any} />
          <Legend theme={theme} showHeat={showHeat} showMarkers={showMarkers} />

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

                const esc = (v: unknown) => String(v ?? '').replace(/[&<>"']/g, (c) =>
                  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
                const level = (stat: any) => stat.monitoringStatus === 'collecting'
                  ? '<span class="map-popup-level collecting">Not enough data yet</span>'
                  : `<span class="map-popup-level ${stat.severityTier}">${esc(stat.severityTier)}</span>`;
                let popupContent = `
                  <div class="map-popup">
                    <div class="map-popup-title">${esc(barangayName)}</div>
                    <div class="map-popup-sub">${esc(municipalityName)}</div>
                `;

                if (bStats && bStats.length > 0) {
                  // One plain sentence per disease: how many farms this week, and what is normal.
                  // Thresholds and history live in the Baselines panel.
                  bStats.forEach((stat) => {
                    const farms = `${farmsText(stat.distinctFarms ?? 0)} reported it in the last 7 days.`;
                    const usual = stat.monitoringStatus !== 'collecting' && stat.baselineFarms != null
                      ? ` Normal: ${usualText(stat.baselineFarms)}.`
                      : '';
                    popupContent += `
                      <hr class="map-popup-rule" />
                      <div class="map-popup-row"><strong>${esc(diseaseLabel(stat.diseaseCode))}</strong>${level(stat)}</div>
                      <div class="map-popup-body">
                        ${esc(farms + usual)}
                        ${stat.monitoringStatus === 'collecting'
                          ? `<br/><span class="map-popup-muted">${esc(undeterminedText(stat))}</span>`
                          : ''}
                      </div>
                    `;
                  });
                } else {
                  popupContent += `
                    <hr class="map-popup-rule" />
                    <span class="map-popup-muted">${reported.length > 0 ? 'No reports match the current filters.' : 'No active disease reports this week.'}</span>
                  `;
                }

                popupContent += `</div>`;
                return popupContent;
              });
            }}
          />

          {/* Report markers (admin layer, off by default), after the polygons so they stack above them */}
          {showMarkers && visible.map(({ d, sev }, i: number) => {
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
                  <div className="map-popup">
                    <div className="map-popup-title">{diseaseLabel(d.diseaseCode)}</div>
                    <div className="map-popup-sub">
                      {d.barangay || 'Unresolved area'}{d.municipality ? `, ${d.municipality}` : ''}
                    </div>
                    <hr className="map-popup-rule" />
                    <div className="map-popup-body">
                      {recent ? (
                        <><span className="map-popup-muted">
                          Reported {daysAgo} days ago · no longer counted in the outbreak level
                        </span><br /></>
                      ) : undetermined ? (
                        <><strong>Outbreak status:</strong>{' '}
                        <span className="map-popup-level collecting">Undetermined</span><br />
                        <span className="map-popup-muted">{undeterminedText(stat)}</span><br /></>
                      ) : (
                        <><strong>Outbreak level:</strong>{' '}
                        <span className={`map-popup-level ${tier}`}>{tier}</span><br /></>
                      )}
                      <strong>Reported by:</strong> {d.farmerName ?? 'Farmer'}<br />
                      <strong>Confidence:</strong> {Math.round(d.confidence * 100)}%<br />
                      <strong>Reported:</strong> {new Date(d.capturedAt).toLocaleDateString()}
                    </div>
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
        search={{ map, layerRef: barangayLayerRef, highlightRef: highlightedRef, highlightStyle: () => highlightStyle(basemapRef.current === 'satellite') }}
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
            <strong>Heat</strong> colours each barangay by its outbreak level this week on a fixed scale
            (the same in the farmers' app): the distinct farms reporting a disease compared with that
            barangay's own usual level. Moderate starts at 50% of the scale (2× usual), Severe at 80% (3×);
            within a level the colour deepens as more farms report. A barangay shows its hottest disease
            (narrow it with the filter), still-collecting barangays are hatched, and the colour describes the
            whole barangay, not where the farms are. The optional <strong>disease markers</strong> are one
            verified report each at its recorded coordinates (admin only): colour = that barangay's level for
            that disease, shape and letter = the disease (circle B = Northern Leaf Blight, square R = Common
            Rust, diamond G = Gray Leaf Spot). Following the WHO and DA-BPI definitions of an outbreak
            (disease above its normal level), each barangay first <strong>collects data</strong>: until it has
            at least 4 weeks since its first verified scan and 10 verified scans of any kind (healthy included),
            its outbreak status is <strong>Undetermined</strong> and no alerts are sent. After that, the
            distinct farms reporting a disease in the last <strong>7 days</strong> are compared with the
            barangay's <strong>baseline</strong> for that disease: the average farms per week over up to 12
            earlier weeks (weeks with no scans at all are skipped; "provisional" until 12 weeks). Each report
            is also weighted by model confidence (80%+ = 1.0, 60–79% = 0.6, below 60% = 0.3). After 7 days a
            report no longer counts (as a marker it turns faded grey); after 28 days it disappears. Healthy
            scans and suspended accounts are never shown. Method and sources: docs/OUTBREAK_DETECTION_METHOD.md.
          </p>
          <ul className="tier-list">
            <li>
              <strong>Severe</strong> — at least <strong>3×</strong> the
              baseline and at least 5 distinct farms, weighted score &ge; 4.0. Sends a potential-outbreak warning
              automatically.
            </li>
            <li>
              <strong>Moderate</strong> — at least <strong>2×</strong> the
              baseline and at least 3 distinct farms, weighted score &ge; 2.0. Sends an awareness notice
              automatically. The distinct-farm minimums stop a single user's repeat scans, or a tiny baseline,
              from raising the level.
            </li>
            <li>
              <strong>Mild</strong> — verified reports below those levels.
            </li>
            <li>
              <strong>Undetermined</strong> — the barangay is still collecting data (hatched on the map, no heat).
            </li>
            <li>
              <strong>Green outline</strong> — the barangay has a baseline. The
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

import { useQuery } from 'convex/react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, ZoomControl, useMap } from 'react-leaflet';
import { api } from '../convex';
// @ts-ignore
import barangays from '../assets/bukidnon-barangays.json';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { diseaseLabel } from '../labels';
import {
  buildLocationIndex,
  searchLocations,
  NO_LOCATION_MESSAGE,
  type LocationEntry,
} from '../locationSearch';

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
          <h4>Severity</h4>
          ${tierRow('severe', 'Severe Outbreak')}
          ${tierRow('moderate', 'Moderate Threat')}
          ${tierRow('mild', 'Mild Detection')}
          <div class="legend-row" style="opacity:0.75">${swatch({ shape: 'circle', letter: '' }, RECENT_COLOR)}Reported 2–4 weeks ago</div>
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
const styleFeature = () => ({
  fillColor: '#1f2937',
  fillOpacity: 0.06,
  color: '#374151',
  weight: 1,
});

// The searched-for barangay or municipality. Blue stays distinct from all three severity colours.
const HIGHLIGHT_STYLE: L.PathOptions = { color: '#2563eb', weight: 3, fillColor: '#2563eb', fillOpacity: 0.15 };

/**
 * Search box over the map: finds a barangay or municipality from the bundled boundary file,
 * outlines it and flies to it. Same behaviour as the app's map (outbreak-map.html).
 * Rendered beside the MapContainer rather than inside it, so Leaflet's own mouse/keyboard
 * handlers (arrow keys pan the map) never see the input's events.
 */
function LocationSearch({ map, layerRef }: { map: L.Map | null; layerRef: React.RefObject<L.GeoJSON> }) {
  const index = useMemo(() => buildLocationIndex((barangays as any).features), []);
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const highlighted = useRef<L.Path[]>([]);
  const pendingPopup = useRef<(() => void) | null>(null);
  const results = useMemo(() => searchLocations(index, query), [index, query]);

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
    setOpen(false);
    inputRef.current?.blur();
  };

  // Back to the icon; the search, highlight and popup go with it.
  const close = () => {
    clearHighlight();
    map?.closePopup();
    setQuery('');
    setOpen(false);
    setExpanded(false);
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
      close();
    }
  };

  // Collapsed to a single magnifier button until clicked, to keep the map uncluttered.
  if (!expanded) {
    return (
      <div className="map-search collapsed">
        <button
          type="button"
          className="map-search-toggle"
          aria-label="Search barangay or municipality"
          onClick={() => setExpanded(true)}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="M15.5 15.5 21 21" />
          </svg>
        </button>
      </div>
    );
  }

  return (
    <div className="map-search">
      <div className="map-search-box">
        <input
          ref={inputRef}
          type="text"
          value={query}
          placeholder="Search barangay or municipality"
          aria-label="Search barangay or municipality"
          autoComplete="off"
          autoFocus
          onChange={(e) => {
            setQuery(e.target.value);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => {
            setOpen(false);
            // Nothing typed: fold back to the icon. (A chosen place stays shown until closed.)
            if (!query.trim()) setExpanded(false);
          }}
          onKeyDown={onKeyDown}
        />
        <button type="button" className="map-search-clear" aria-label="Close search" onClick={close}>
          &times;
        </button>
      </div>
      {open && query.trim() && (
        <ul className="map-search-results" role="listbox">
          {results.length === 0 ? (
            <li className="map-search-empty">{NO_LOCATION_MESSAGE}</li>
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
                <span className="map-search-name">{r.label}</span>
                <span className="map-search-meta">{r.kind === 'municipality' ? 'Municipality' : r.municipality}</span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

export default function MapPage() {
  const stats = useQuery(api.barangayStats.getAllStats);
  // Individual verified reports — these are what get drawn as dots. The aggregated
  // stats below only supply each dot's severity tier now, not a polygon fill.
  const detections = useQuery(api.diagnosisRecords.mapReports);
  const [map, setMap] = useState<L.Map | null>(null);
  const barangayLayerRef = useRef<L.GeoJSON>(null);

  // Grouped per barangay for the polygon popup, and keyed per barangay+disease so each
  // dot picks up the tier for its own disease rather than the barangay's worst.
  const statsByBarangay = new Map<string, any[]>();
  const tierByBarangayDisease = new Map<string, string>();
  if (stats) {
    stats.forEach((s: any) => {
      // Keyed by barangay AND municipality: barangay names (e.g. Poblacion) repeat across towns.
      const area = `${s.barangay}||${s.municipality}`;
      if (!statsByBarangay.has(area)) statsByBarangay.set(area, []);
      statsByBarangay.get(area)!.push(s);
      tierByBarangayDisease.set(`${area}||${s.diseaseCode}`, s.severityTier);
    });
  }
  // Read by the barangay popups when they open, so they show the latest stats.
  const statsRef = useRef(statsByBarangay);
  statsRef.current = statsByBarangay;

  const reportCount = detections?.length ?? 0;
  const offsets = spreadOffsets(detections ?? []);

  return (
    <div className="dashboard">
      <div className="page-header">
        <h1 className="page-title">Outbreak Map</h1>
        <p className="page-subtitle">
          Individual verified reports across Bukidnon
          {detections ? ` — ${reportCount} plotted` : ''}
        </p>
      </div>

      <div className="card" style={{ position: 'relative', height: '70vh', minHeight: '500px', overflow: 'hidden', padding: 0 }}>
        <LocationSearch map={map} layerRef={barangayLayerRef} />
        <MapContainer
          ref={setMap}
          center={[8.15, 125.13]}
          zoom={9}
          zoomControl={false}
          style={{ height: '100%', width: '100%', background: '#f4f1ea' }}
        >
          {/* Zoom bottom-left, as on the app's map: top-left holds the search box. */}
          <ZoomControl position="bottomleft" />
          {/* Voyager, not light_all: the plain grey basemap dropped roads and admin
              boundaries almost entirely. This keeps the OSM look (green parks, orange
              highways, visible municipal borders) while staying light. */}
          <TileLayer
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          />
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
                const bStats = statsRef.current.get(`${barangayName}||${municipalityName}`);

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
                        <strong>Severity:</strong> <span style="color: ${TIER_COLOR[stat.severityTier]}; text-transform: uppercase; font-weight: bold;">${stat.severityTier}</span><br/>
                        <strong>Farms Affected:</strong> ${stat.distinctFarms}<br/>
                        <strong>Total Reports:</strong> ${stat.rawReportCount}
                      </span>
                    `;
                  });
                } else {
                  popupContent += `
                    <hr style="border-color: #333; margin: 8px 0;" />
                    <span style="color: #888; font-size: 13px;">No active outbreaks reported.</span>
                  `;
                }

                popupContent += `</div>`;
                return popupContent;
              });
            }}
          />

          {/* Report dots, after the polygons so they stack above them */}
          {(detections ?? []).map((d: any, i: number) => {
            const recent = d.stage === 'recent';
            const tier = tierByBarangayDisease.get(`${d.barangay}||${d.municipality}||${d.diseaseCode}`) || 'mild';
            const daysAgo = Math.max(0, Math.round((Date.now() - d.capturedAt) / DAY_MS));
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
                      ) : (
                        <><strong>Severity:</strong>{' '}
                        <span style={{ color: TIER_COLOR[tier], textTransform: 'uppercase', fontWeight: 'bold' }}>
                          {tier}
                        </span><br /></>
                      )}
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

      {/* Colour meanings now live in the on-map legend overlay. This card keeps only what
          the overlay has no room for: the thresholds behind each tier. */}
      <div className="card" style={{ marginTop: '16px' }}>
        <div className="card-header">
          <h3 className="card-title">How severity is assigned</h3>
        </div>
        <div className="card-body">
          <p style={{ margin: '0 0 12px 0', color: 'var(--text-secondary)', fontSize: '13px' }}>
            Each marker is one verified report at its recorded coordinates. Its colour shows the
            outbreak tier of that barangay <em>for that disease</em>; its shape and letter show
            which disease (circle B = Northern Leaf Blight, square R = Common Rust, diamond G = Gray
            Leaf Spot). Tiers are recomputed over a rolling 14-day window, weighting each report
            by its model confidence. After 14 days a report turns into a faded grey marker (no longer
            counted); after 28 days it disappears. Healthy scans and suspended accounts are never shown.
          </p>
          <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '13px', lineHeight: 1.7 }}>
            <li>
              <strong style={{ color: TIER_COLOR.severe }}>Severe</strong> — weighted score
              &ge; 4.0 <em>and</em> reports from &ge; 3 distinct farms. The distinct-farm
              requirement is what stops a single user's repeat scans from declaring an outbreak.
            </li>
            <li>
              <strong style={{ color: TIER_COLOR.moderate }}>Moderate</strong> — weighted score
              &ge; 2.0 <em>or</em> reports from &ge; 2 distinct farms.
            </li>
            <li>
              <strong style={{ color: TIER_COLOR.mild }}>Mild</strong> — at least one verified
              report inside the window.
            </li>
          </ul>
          <p style={{ margin: '12px 0 0 0', color: 'var(--text-muted)', fontSize: '12px' }}>
            Rejecting a scan in Review removes its weight on the next recompute, so
            severity falls back automatically.
          </p>
        </div>
      </div>
    </div>
  );
}

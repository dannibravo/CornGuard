import { useQuery } from 'convex/react';
import { useEffect } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, useMap } from 'react-leaflet';
import { api } from '../convex';
// @ts-ignore
import barangays from '../assets/bukidnon-barangays.json';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { diseaseLabel } from '../labels';

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

function markerIcon(diseaseCode: string, tier: string, recent: boolean) {
  const size = recent ? RECENT_SIZE : (TIER_SIZE[tier] ?? TIER_SIZE.mild);
  const fill = recent ? RECENT_COLOR : (TIER_COLOR[tier] ?? NEUTRAL);
  return L.divIcon({
    className: recent ? 'cg-marker cg-marker-recent' : 'cg-marker',
    html: markerSvg(DISEASE_MARK[diseaseCode] ?? UNKNOWN_MARK, fill, size, !recent && tier === 'severe'),
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });
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
      const div = L.DomUtil.create('div', 'map-legend');
      const swatch = (mark: Mark, fill: string) => `<span class="swatch">${markerSvg(mark, fill, 16, false)}</span>`;
      const tierRow = (tier: string, label: string) =>
        `<div class="legend-row">${swatch({ shape: 'circle', letter: '' }, TIER_COLOR[tier])}${label}</div>`;
      const diseaseRows = Object.keys(DISEASE_MARK)
        .map((name) => `<div class="legend-row">${swatch(DISEASE_MARK[name], NEUTRAL)}${diseaseLabel(name)}</div>`)
        .join('');

      div.innerHTML = `
        <h4>Severity</h4>
        ${tierRow('severe', 'Severe Outbreak')}
        ${tierRow('moderate', 'Moderate Threat')}
        ${tierRow('mild', 'Mild Detection')}
        <div class="legend-row" style="opacity:0.75">${swatch({ shape: 'circle', letter: '' }, RECENT_COLOR)}Reported 2–4 weeks ago</div>
        <h4 class="legend-sub">Disease (shape)</h4>
        ${diseaseRows}
      `;

      // Otherwise clicking/scrolling the legend pans and zooms the map underneath it
      L.DomEvent.disableClickPropagation(div);
      L.DomEvent.disableScrollPropagation(div);
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

export default function MapPage() {
  const stats = useQuery(api.barangayStats.getAllStats);
  // Individual verified reports — these are what get drawn as dots. The aggregated
  // stats below only supply each dot's severity tier now, not a polygon fill.
  const detections = useQuery(api.diagnosisRecords.mapReports);

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

  // Barangay outlines carry no severity fill: a filled polygon implied the whole area
  // was infected, when only specific farms actually reported. fillOpacity is a hair
  // above zero rather than exactly 0 because an SVG path with fill-opacity:0 isn't
  // "painted", so pointer-events skip it and the click-for-details popup breaks.
  const styleFeature = () => ({
    fillColor: '#1f2937',
    fillOpacity: 0.06,
    color: '#374151',
    weight: 1,
  });

  const reportCount = detections?.length ?? 0;

  return (
    <div className="dashboard">
      <div className="page-header">
        <h1 className="page-title">Outbreak Map</h1>
        <p className="page-subtitle">
          Individual verified reports across Bukidnon
          {detections ? ` — ${reportCount} plotted` : ''}
        </p>
      </div>

      <div className="card" style={{ height: '70vh', minHeight: '500px', overflow: 'hidden', padding: 0 }}>
        <MapContainer
          center={[8.15, 125.13]}
          zoom={9}
          style={{ height: '100%', width: '100%', background: '#f4f1ea' }}
        >
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
            data={barangays as any}
            style={styleFeature}
            onEachFeature={(feature, layer) => {
              const barangayName = feature.properties.barangay;
              const municipalityName = feature.properties.municipality;
              const bStats = statsByBarangay.get(`${barangayName}||${municipalityName}`);

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
              layer.bindPopup(popupContent);
            }}
          />

          {/* Report dots, after the polygons so they stack above them */}
          {(detections ?? []).map((d: any) => {
            const recent = d.stage === 'recent';
            const tier = tierByBarangayDisease.get(`${d.barangay}||${d.municipality}||${d.diseaseCode}`) || 'mild';
            const daysAgo = Math.max(0, Math.round((Date.now() - d.capturedAt) / DAY_MS));
            return (
              <Marker
                key={d.recordId}
                position={[d.latitude, d.longitude]}
                icon={markerIcon(d.diseaseCode, tier, recent)}
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

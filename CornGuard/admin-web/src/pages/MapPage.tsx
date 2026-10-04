import { useQuery } from 'convex/react';
import { useEffect } from 'react';
import { MapContainer, TileLayer, GeoJSON, CircleMarker, Popup, useMap } from 'react-leaflet';
import { api } from '../convex';
// @ts-ignore
import barangays from '../assets/bukidnon-barangays.json';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { diseaseLabel } from '../labels';

// KEEP IN SYNC with the app's android/app/src/main/assets/map/outbreak-map.html — the two maps must agree on
// what a colour means. Update both or neither.
const TIER_COLOR: Record<string, string> = {
  mild: '#10b981',      // Emerald Green
  moderate: '#f59e0b',  // Amber/Yellow
  severe: '#ef4444',    // Red
};

// Dot fill = severity tier of that barangay+disease; ring = which disease it is.
const DISEASE_RING: Record<string, string> = {
  northern_leaf_blight: '#60a5fa', // Blue
  common_rust: '#f472b6',          // Pink
  gray_leaf_spot: '#a78bfa',       // Violet
};
const UNKNOWN_RING = '#e5e7eb';

// Radius also tracks severity, so the map stays readable for red-green colourblind
// viewers instead of relying on hue alone.
const TIER_RADIUS: Record<string, number> = { severe: 8, moderate: 6.5, mild: 5 };

const ringColor = (diseaseCode: string) => DISEASE_RING[diseaseCode] || UNKNOWN_RING;

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
      const diseaseRows = Object.keys(DISEASE_RING)
        .map(
          (name) => `
            <div class="legend-row">
              <i class="ring" style="border-color: ${DISEASE_RING[name]}"></i>${diseaseLabel(name)}
            </div>`
        )
        .join('');

      div.innerHTML = `
        <h4>Severity</h4>
        <div class="legend-row"><i class="dot" style="background: ${TIER_COLOR.severe}"></i>Severe Outbreak</div>
        <div class="legend-row"><i class="dot" style="background: ${TIER_COLOR.moderate}"></i>Moderate Threat</div>
        <div class="legend-row"><i class="dot" style="background: ${TIER_COLOR.mild}"></i>Mild Detection</div>
        <h4 class="legend-sub">Disease (ring)</h4>
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
            const tier = tierByBarangayDisease.get(`${d.barangay}||${d.municipality}||${d.diseaseCode}`) || 'mild';
            return (
              <CircleMarker
                key={d.recordId}
                center={[d.latitude, d.longitude]}
                radius={TIER_RADIUS[tier] ?? 5}
                pathOptions={{
                  fillColor: TIER_COLOR[tier],
                  fillOpacity: 0.85,
                  color: ringColor(d.diseaseCode),
                  weight: 2,
                  opacity: 0.95,
                }}
              >
                <Popup>
                  <div style={{ fontFamily: 'sans-serif', minWidth: '180px' }}>
                    <b style={{ color: '#4ade80', fontSize: '15px' }}>{diseaseLabel(d.diseaseCode)}</b><br />
                    <span style={{ color: '#888', fontSize: '12px' }}>
                      {d.barangay || 'Unresolved area'}{d.municipality ? `, ${d.municipality}` : ''}
                    </span>
                    <hr style={{ borderColor: '#333', margin: '8px 0' }} />
                    <span style={{ fontSize: '13px' }}>
                      <strong>Severity:</strong>{' '}
                      <span style={{ color: TIER_COLOR[tier], textTransform: 'uppercase', fontWeight: 'bold' }}>
                        {tier}
                      </span><br />
                      <strong>Confidence:</strong> {Math.round(d.confidence * 100)}%<br />
                      <strong>Reported:</strong> {new Date(d.capturedAt).toLocaleDateString()}
                    </span>
                  </div>
                </Popup>
              </CircleMarker>
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
            Each dot is one verified report at its recorded coordinates. Its fill shows the
            outbreak tier of that barangay <em>for that disease</em>; its ring shows which
            disease. Tiers are recomputed over a rolling 14-day window, weighting each report
            by its model confidence.
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

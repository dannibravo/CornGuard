// Outbreak heat for the admin map: colour ramp, fallback heat formula and a small Leaflet canvas
// layer for hatching ("collecting data") and centroid glows.
// KEEP IN SYNC with the heat section of android/app/src/main/assets/map/outbreak-map.html (same
// ramp, same rules) and with heatIntensity in backend/convex/lib/outbreakRules.ts (same formula).
import L from 'leaflet';

export type Theme = 'light' | 'dark';

/** Fixed scale: Moderate starts at 0.5 (2× the usual level), Severe at 0.8 (3×). Never normalised. */
export const RAMP: Record<Theme, [number, string][]> = {
  light: [[0, '#efdca0'], [0.25, '#e3b85a'], [0.5, '#d08a26'], [0.8, '#b04f27'], [1, '#7a2417']],
  dark: [[0, '#6b5226'], [0.25, '#a06c24'], [0.5, '#d08b33'], [0.8, '#f2a37f'], [1, '#fcd3c2']],
};
export const HATCH: Record<Theme, { line: string; bg: string }> = {
  light: { line: '#a39a85', bg: '#f4efe2' },
  dark: { line: '#7d7b6d', bg: '#1d2219' },
};
export const HEAT_FILL_OPACITY = 0.78;

export function currentTheme(): Theme {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

const hexToRgb = (h: string) => {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};
const rgbToHex = (c: number[]) => '#' + c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');

export function rampColor(t: number, theme: Theme): string {
  const stops = RAMP[theme];
  const x = Math.min(1, Math.max(0, t));
  for (let i = 1; i < stops.length; i++) {
    if (x <= stops[i][0]) {
      const [a, ca] = stops[i - 1];
      const [b, cb] = stops[i];
      const f = (x - a) / (b - a);
      const A = hexToRgb(ca);
      const B = hexToRgb(cb);
      return rgbToHex(A.map((v, k) => v + (B[k] - v) * f));
    }
  }
  return stops[stops.length - 1][1];
}

/** Outline 25% darker than the fill (25% lighter in dark mode, where the ramp gets lighter). */
export function strokeFor(fill: string, theme: Theme): string {
  const c = hexToRgb(fill);
  return rgbToHex(theme === 'dark' ? c.map((v) => v + (255 - v) * 0.25) : c.map((v) => v * 0.75));
}

type StatLike = {
  intensity?: number | null;
  monitoringStatus?: string;
  severityTier: string;
  distinctFarms?: number;
  moderateAt?: number | null;
  severeAt?: number | null;
};

/** The backend's heat when sent; otherwise the same formula (older backends). null = collecting. */
export function heatOf(s: StatLike): number | null {
  if (s.intensity !== undefined) return s.intensity;
  if (s.monitoringStatus === 'collecting') return null;
  const farms = Math.max(0, s.distinctFarms ?? 0);
  if (farms === 0) return 0;
  const clamp01 = (x: number) => Math.min(1, Math.max(0, x));
  const moderateAt = Math.max(1, s.moderateAt ?? 3);
  const severeAt = Math.max(moderateAt + 1, s.severeAt ?? 5);
  if (s.severityTier === 'severe') return 0.8 + 0.2 * clamp01((farms - severeAt) / severeAt);
  if (s.severityTier === 'moderate') return 0.5 + 0.28 * clamp01((farms - moderateAt) / (severeAt - moderateAt));
  return Math.min(0.45, 0.12 + 0.33 * farms / moderateAt);
}

/** { heat } for the hottest row, { collecting } when only collecting rows, null = no reports. */
export type AreaHeat = { heat: number } | { collecting: true } | null;

export function areaHeat(rows: StatLike[]): AreaHeat {
  let heat: number | null = null;
  let collecting = false;
  rows.forEach((s) => {
    const h = heatOf(s);
    if (h === null) collecting = true;
    else if (h > 0 && (heat === null || h > heat)) heat = h;
  });
  if (heat !== null) return { heat };
  return collecting ? { collecting: true } : null;
}

// Design radii in px with the whole province ~600px wide (zoom 10); they grow half as fast as the
// map, so the glow stays a hint around the barangay rather than looking like a new area.
const GLOW_RADIUS = { severe: 44, moderate: 34, mild: 24 };
const GLOW_REFERENCE_ZOOM = 10;
const GLOW_BLUR = 9;
const GLOW_ALPHA = 0.4;

type HeatPath = L.Polygon & { feature?: any; _cgCenter?: L.LatLng };

/**
 * Hatching for "collecting" barangays and a soft glow at each heated barangay's centroid, drawn on
 * one canvas under the polygons. Glows are painted opaque in ascending order on an offscreen canvas
 * (where two overlap the hotter one wins: a maximum, never a sum), then blurred in one pass.
 */
export class HeatLayer extends L.Layer {
  private canvas!: HTMLCanvasElement;
  private glow!: HTMLCanvasElement;

  constructor(
    private getLayers: () => HeatPath[],
    private stateOf: (layer: HeatPath) => AreaHeat,
    private getTheme: () => Theme,
  ) {
    super();
  }

  onAdd(map: L.Map) {
    if (!map.getPane('heatPane')) map.createPane('heatPane').style.zIndex = '350';
    this.canvas = L.DomUtil.create('canvas', 'cg-heat') as HTMLCanvasElement;
    this.canvas.style.pointerEvents = 'none';
    this.glow = document.createElement('canvas');
    map.getPane('heatPane')!.appendChild(this.canvas);
    map.on('moveend zoomend resize viewreset', this.reset, this);
    map.on('zoomstart', this.hide, this);
    this.reset();
    return this;
  }

  onRemove(map: L.Map) {
    L.DomUtil.remove(this.canvas);
    map.off('moveend zoomend resize viewreset', this.reset, this);
    map.off('zoomstart', this.hide, this);
    return this;
  }

  redraw() {
    if (this._map) this.reset();
  }

  private hide() {
    this.canvas.style.visibility = 'hidden';
  }

  private reset() {
    const map = this._map;
    const size = map.getSize();
    const ratio = window.devicePixelRatio || 1;
    const w = Math.round(size.x * ratio);
    const h = Math.round(size.y * ratio);
    if (this.canvas.width !== w || this.canvas.height !== h) {
      [this.canvas, this.glow].forEach((c) => { c.width = w; c.height = h; });
      this.canvas.style.width = `${size.x}px`;
      this.canvas.style.height = `${size.y}px`;
    }
    L.DomUtil.setPosition(this.canvas, map.containerPointToLayerPoint([0, 0]));
    this.draw(ratio);
    this.canvas.style.visibility = 'visible';
  }

  private draw(ratio: number) {
    const map = this._map;
    const theme = this.getTheme();
    const ctx = this.canvas.getContext('2d')!;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    const view = map.getBounds().pad(0.2);
    const hot: { layer: HeatPath; heat: number }[] = [];
    let pattern: CanvasPattern | null = null;
    this.getLayers().forEach((layer) => {
      const st = this.stateOf(layer);
      if (!st || !view.intersects(layer.getBounds())) return;
      if ('heat' in st) {
        hot.push({ layer, heat: st.heat });
        return;
      }
      if (!pattern) pattern = ctx.createPattern(hatchTile(HATCH[theme], ratio), 'repeat');
      ctx.save();
      ctx.beginPath();
      trace(ctx, map, layer);
      ctx.fillStyle = HATCH[theme].bg;
      ctx.globalAlpha = 0.85;
      ctx.fill('evenodd');
      ctx.globalAlpha = 1;
      ctx.setTransform(1, 0, 0, 1, 0, 0); // pattern in device pixels
      ctx.fillStyle = pattern!;
      ctx.fill('evenodd');
      ctx.restore();
    });
    if (hot.length === 0) return;
    hot.sort((a, b) => a.heat - b.heat);
    const g = this.glow.getContext('2d')!;
    g.setTransform(ratio, 0, 0, ratio, 0, 0);
    g.clearRect(0, 0, this.glow.width, this.glow.height);
    const scale = Math.min(1.6, Math.pow(2, (map.getZoom() - GLOW_REFERENCE_ZOOM) / 2));
    hot.forEach(({ layer, heat }) => {
      const c = map.latLngToContainerPoint(centroidOf(layer));
      const base = heat >= 0.8 ? GLOW_RADIUS.severe : heat >= 0.5 ? GLOW_RADIUS.moderate : GLOW_RADIUS.mild;
      g.beginPath();
      g.arc(c.x, c.y, base * scale, 0, Math.PI * 2);
      g.fillStyle = rampColor(heat, theme);
      g.fill();
    });
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = GLOW_ALPHA;
    ctx.filter = `blur(${Math.round(GLOW_BLUR * ratio)}px)`;
    ctx.drawImage(this.glow, 0, 0);
    ctx.restore();
  }
}

function hatchTile(hatch: { line: string }, ratio: number) {
  const s = Math.round(8 * ratio);
  const c = document.createElement('canvas');
  c.width = c.height = s;
  const x = c.getContext('2d')!;
  x.strokeStyle = hatch.line;
  x.lineWidth = Math.max(1, ratio);
  x.beginPath();
  x.moveTo(0, s); x.lineTo(s, 0);
  x.moveTo(-s / 2, s / 2); x.lineTo(s / 2, -s / 2);
  x.moveTo(s / 2, s * 1.5); x.lineTo(s * 1.5, s / 2);
  x.stroke();
  return c;
}

function trace(ctx: CanvasRenderingContext2D, map: L.Map, layer: HeatPath) {
  const rings: L.LatLng[][] = [];
  const collect = (latlngs: any) => {
    if (latlngs.length && latlngs[0] instanceof L.LatLng) rings.push(latlngs);
    else latlngs.forEach(collect);
  };
  collect(layer.getLatLngs());
  rings.forEach((ring) => {
    ring.forEach((ll, i) => {
      const p = map.latLngToContainerPoint(ll);
      if (i === 0) ctx.moveTo(p.x, p.y);
      else ctx.lineTo(p.x, p.y);
    });
    ctx.closePath();
  });
}

function centroidOf(layer: HeatPath): L.LatLng {
  if (!layer._cgCenter) {
    try {
      layer._cgCenter = layer.getCenter();
    } catch {
      layer._cgCenter = layer.getBounds().getCenter();
    }
  }
  return layer._cgCenter;
}

/** Intensity meter for a popup: fill to the heat, ticks at Moderate (50%) and Severe (80%). */
export function meterHtml(heat: number | null, theme: Theme): string {
  if (heat === null) {
    return '<div class="heat-meter heat-meter-collecting" role="img" aria-label="Outbreak intensity: not enough data yet"></div>';
  }
  const pct = Math.round(Math.min(1, Math.max(0, heat)) * 100);
  return `<div class="heat-meter" role="img" aria-label="Outbreak intensity ${pct}% (Moderate at 50%, Severe at 80%)">` +
    `<span class="heat-meter-fill" style="width:${pct}%;background:${rampColor(heat, theme)}"></span>` +
    '<span class="heat-meter-tick" style="left:50%"></span><span class="heat-meter-tick" style="left:80%"></span></div>';
}

import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from 'convex/react';
import { api } from '../convex';
import { diseaseLabel } from '../labels';
import { Alert, Button, PageSection } from './ui';

/** admin.weeklyReports row (backend convex/lib/weeklyReports.ts WeeklyRow). */
type WeeklyRow = { weekStart: number; barangay: string; municipality: string; diseaseCode: string; scans: number; farms: number };
type WeeklyResult = { weekStarts: number[]; rows: WeeklyRow[]; truncated: boolean };

/** Fixed series order and colours (admin.css disease tokens; same order as the app's map pills). */
const SERIES = [
  { code: 'common_rust', color: 'var(--disease-rust)' },
  { code: 'gray_leaf_spot', color: 'var(--disease-gls)' },
  { code: 'northern_leaf_blight', color: 'var(--disease-blight)' },
] as const;

const WEEK_OPTIONS = [8, 12, 26];
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const PHT_MS = 8 * 60 * 60 * 1000;

/** A PHT week start as YYYY-MM-DD (week starts are Monday 00:00 PHT). */
const isoDay = (ms: number) => new Date(ms + PHT_MS).toISOString().slice(0, 10);
const shortDay = (ms: number) =>
  new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'Asia/Manila' });

/**
 * Dashboard: verified disease reports per week, stacked by disease, plus a CSV export of the same
 * data by barangay for the Municipal Agriculture Office. Counts are verified scans only — the set
 * the outbreak levels are computed from — so it never mixes in unreviewed or Healthy scans.
 */
export function WeeklyReportsCard() {
  const [weeks, setWeeks] = useState(8);
  // Queries must not read the clock: "now" is fixed when the page opens.
  const [endAt] = useState(() => Date.now());
  const data = useQuery(api.admin.weeklyReports, { weeks, endAt }) as WeeklyResult | undefined;

  const perWeek = useMemo(() => {
    if (!data) return [];
    return data.weekStarts.map((weekStart) => {
      const counts: Record<string, number> = {};
      for (const r of data.rows) if (r.weekStart === weekStart) counts[r.diseaseCode] = (counts[r.diseaseCode] ?? 0) + r.scans;
      const total = SERIES.reduce((n, s) => n + (counts[s.code] ?? 0), 0);
      return { weekStart, counts, total };
    });
  }, [data]);
  const empty = !!data && data.rows.length === 0;

  return (
    <PageSection
      id="weekly"
      title="Weekly verified reports"
      description="Verified disease scans per week (Monday–Sunday, Philippine time). Healthy scans and suspended accounts are not counted."
      actions={
        <>
          <label className="weekly-range">
            <span className="sr-only">Weeks shown</span>
            <select value={weeks} onChange={(e) => setWeeks(Number(e.target.value))} aria-label="Weeks shown">
              {WEEK_OPTIONS.map((w) => <option key={w} value={w}>Last {w} weeks</option>)}
            </select>
          </label>
          <Button size="sm" icon="download" disabled={!data || empty} onClick={() => data && downloadCsv(data)}>
            Download CSV
          </Button>
        </>
      }
    >
      {data?.truncated && (
        <Alert variant="warning">Only the most recent 5,000 verified scans were counted; older weeks may be incomplete.</Alert>
      )}
      {data === undefined ? (
        <div className="weekly-chart-skeleton skeleton" aria-label="Loading weekly reports" />
      ) : empty ? (
        <p className="weekly-empty">No verified disease reports in the last {weeks} weeks.</p>
      ) : (
        <>
          <Legend />
          <StackedWeeks weeks={perWeek} />
          <details className="weekly-table">
            <summary>Show as table</summary>
            <div className="weekly-table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Week of</th>
                    {SERIES.map((s) => <th key={s.code} scope="col" className="num">{diseaseLabel(s.code)}</th>)}
                    <th scope="col" className="num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {perWeek.map((w) => (
                    <tr key={w.weekStart}>
                      <th scope="row">{shortDay(w.weekStart)}</th>
                      {SERIES.map((s) => <td key={s.code} className="num">{w.counts[s.code] ?? 0}</td>)}
                      <td className="num"><strong>{w.total}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      )}
    </PageSection>
  );
}

function Legend() {
  return (
    <ul className="chart-legend" aria-label="Diseases">
      {SERIES.map((s) => (
        <li key={s.code}><span className="chart-swatch" style={{ background: s.color }} aria-hidden="true" />{diseaseLabel(s.code)}</li>
      ))}
    </ul>
  );
}

type Week = { weekStart: number; counts: Record<string, number>; total: number };

const H = 220;
const PAD = { top: 20, right: 8, bottom: 28, left: 36 };
const GAP = 2; // surface gap between stacked segments

function StackedWeeks({ weeks }: { weeks: Week[] }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const width = useWidth(wrapRef);
  const [active, setActive] = useState<number | null>(null);

  const max = Math.max(1, ...weeks.map((w) => w.total));
  const ticks = niceTicks(max);
  const top = ticks[ticks.length - 1];
  const plotW = Math.max(0, width - PAD.left - PAD.right);
  const plotH = H - PAD.top - PAD.bottom;
  const col = weeks.length ? plotW / weeks.length : 0;
  const barW = Math.max(6, Math.min(40, col * 0.6));
  const y = (v: number) => PAD.top + plotH - (v / top) * plotH;
  const labelEvery = Math.ceil(weeks.length / Math.max(1, Math.floor(plotW / 56)));
  const latest = weeks[weeks.length - 1];
  const summary = `Verified disease reports per week. Latest week: ${latest?.total ?? 0} reports.`;
  const tip = active !== null ? weeks[active] : null;

  return (
    <div className="weekly-chart" ref={wrapRef} onPointerLeave={() => setActive(null)}>
      {width > 0 && (
        <svg width={width} height={H} role="img" aria-label={summary}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} className={t === 0 ? 'chart-baseline' : 'chart-grid'} />
              <text x={PAD.left - 8} y={y(t)} dy="0.32em" textAnchor="end" className="chart-tick">{t}</text>
            </g>
          ))}
          {weeks.map((w, i) => {
            const cx = PAD.left + col * i + col / 2;
            let acc = 0;
            const segs = SERIES.map((s) => ({ ...s, v: w.counts[s.code] ?? 0 })).filter((s) => s.v > 0);
            return (
              <g key={w.weekStart}
                tabIndex={0}
                role="img"
                aria-label={`Week of ${shortDay(w.weekStart)}: ${w.total} verified reports, ${SERIES.map((s) => `${diseaseLabel(s.code)} ${w.counts[s.code] ?? 0}`).join(', ')}`}
                onPointerEnter={() => setActive(i)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
                className={`chart-col${active === i ? ' is-active' : ''}`}>
                {/* Hit target: the whole column, taller and wider than the bar. */}
                <rect x={PAD.left + col * i} y={PAD.top} width={col} height={plotH} className="chart-hit" />
                {segs.map((s, k) => {
                  const y0 = y(acc);
                  acc += s.v;
                  const y1 = y(acc);
                  const isTop = k === segs.length - 1;
                  const h = Math.max(1, y0 - y1 - (isTop ? 0 : GAP));
                  return isTop
                    ? <path key={s.code} d={roundedTop(cx - barW / 2, y0 - h, barW, h, Math.min(4, h, barW / 2))} style={{ fill: s.color }} />
                    : <rect key={s.code} x={cx - barW / 2} y={y0 - h} width={barW} height={h} style={{ fill: s.color }} />;
                })}
                {i % labelEvery === (weeks.length - 1) % labelEvery && (
                  <text x={cx} y={H - PAD.bottom + 18} textAnchor="middle" className="chart-tick">{shortDay(w.weekStart)}</text>
                )}
                {i === weeks.length - 1 && w.total > 0 && (
                  <text x={cx} y={y(w.total) - 6} textAnchor="middle" className="chart-value">{w.total}</text>
                )}
              </g>
            );
          })}
        </svg>
      )}
      {tip && active !== null && (
        <div className="chart-tooltip" role="presentation"
          style={{ left: Math.min(Math.max(PAD.left + col * active + col / 2, 90), width - 90), top: PAD.top }}>
          <strong>Week of {shortDay(tip.weekStart)}</strong>
          {SERIES.map((s) => (
            <span key={s.code} className="chart-tooltip-row">
              <span className="chart-swatch" style={{ background: s.color }} aria-hidden="true" />
              {diseaseLabel(s.code)}<span className="num">{tip.counts[s.code] ?? 0}</span>
            </span>
          ))}
          <span className="chart-tooltip-row chart-tooltip-total">Total<span className="num">{tip.total}</span></span>
        </div>
      )}
    </div>
  );
}

/** A bar with its top corners rounded (the data end); the baseline end stays square. */
function roundedTop(x: number, y: number, w: number, h: number, r: number) {
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
}

/** 0 plus up to 4 round tick values covering [0, max]. */
function niceTicks(max: number): number[] {
  const rough = max / 4;
  const mag = 10 ** Math.floor(Math.log10(rough));
  const step = Math.max(1, [1, 2, 5, 10].map((m) => m * mag).find((s) => s >= rough) ?? mag * 10);
  const out = [];
  for (let t = 0; t < max + step; t += step) out.push(t);
  return out;
}

function useWidth(ref: React.RefObject<HTMLElement>) {
  const [w, setW] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setW(Math.floor(entry.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, [ref]);
  return w;
}

/** One row per week × barangay × disease, for the Municipal Agriculture Office. */
function downloadCsv(data: WeeklyResult) {
  const cell = (v: string | number) => {
    const s = String(v);
    // Quote everything that needs it; a leading =,+,-,@ is prefixed so spreadsheets don't run it as a formula.
    const safe = /^[=+\-@]/.test(s) ? `'${s}` : s;
    return /[",\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
  };
  const header = ['week_start', 'week_end', 'municipality', 'barangay', 'disease', 'verified_scans', 'farms_reporting'];
  const lines = data.rows.map((r) => [
    isoDay(r.weekStart), isoDay(r.weekStart + WEEK_MS - 1), r.municipality, r.barangay, diseaseLabel(r.diseaseCode), r.scans, r.farms,
  ].map(cell).join(','));
  const csv = '﻿' + [header.join(','), ...lines].join('\r\n') + '\r\n';
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `cornguard-verified-reports-${isoDay(data.weekStarts[0])}-to-${isoDay(data.weekStarts[data.weekStarts.length - 1] + WEEK_MS - 1)}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

import { useQuery } from 'convex/react';
import { Link } from 'react-router-dom';
import { api } from '../convex';
import { Icon, type IconName } from '../components/Icon';
import { HelpText, PageSection } from '../components/ui';

type Tile = { key: string; label: string; icon: IconName; to?: string; tone?: 'warning' | 'danger'; hint?: string };

/** Things an admin should act on: highlighted when non-zero. */
const ATTENTION: Tile[] = [
  { key: 'scansUnverified', label: 'Scans awaiting review', icon: 'scan', to: '/moderation', tone: 'warning', hint: 'Verify or reject in Review' },
  { key: 'reportsOpen', label: 'Reported posts & comments', icon: 'flag', to: '/moderation', tone: 'warning', hint: 'Farmers flagged these' },
  { key: 'dataPoorClusters', label: 'Clusters without a baseline', icon: 'help', to: '/outbreaks', tone: 'warning', hint: 'Not alerted — check manually' },
  { key: 'severeAreas', label: 'Severe barangay / disease', icon: 'warning', to: '/map', tone: 'danger', hint: 'See them on the map' },
];

const OVERVIEW: Tile[] = [
  { key: 'alertsSent', label: 'Alerts sent (14 days)', icon: 'alert', to: '/outbreaks' },
  { key: 'users', label: 'Users', icon: 'users', to: '/users' },
  { key: 'scans', label: 'Shared scans', icon: 'leaf' },
  { key: 'scansVerified', label: 'Verified scans', icon: 'checkCircle' },
  { key: 'postsUnverified', label: 'Unverified posts', icon: 'post', to: '/moderation' },
  { key: 'suspendedUsers', label: 'Suspended accounts', icon: 'ban', to: '/users' },
];

export default function DashboardPage() {
  const stats = useQuery(api.admin.stats);
  const loading = stats === undefined;
  const val = (key: string): number => (stats?.[key] ?? 0) as number;

  const attentionTotal = ATTENTION.reduce((n, t) => n + val(t.key), 0);
  const scans = val('scans');
  const verified = val('scansVerified');
  const rate = scans > 0 ? verified / scans : null;

  return (
    <div className="page-stack">
      <section aria-labelledby="attention-title">
        <div className="section-heading">
          <h2 id="attention-title">Needs attention</h2>
          {!loading && (
            attentionTotal === 0
              ? <span className="badge badge-green"><Icon name="check" size={12} /> All clear</span>
              : <span className="badge badge-orange">{attentionTotal} open</span>
          )}
        </div>
        <div className="tile-grid tile-grid-lg">
          {ATTENTION.map((t) => <StatTile key={t.key} tile={t} value={loading ? null : val(t.key)} emphasise />)}
        </div>
      </section>

      <section aria-labelledby="overview-title">
        <div className="section-heading">
          <h2 id="overview-title">Overview</h2>
        </div>
        <div className="overview-layout">
          <div className="tile-grid">
            {OVERVIEW.map((t) => <StatTile key={t.key} tile={t} value={loading ? null : val(t.key)} />)}
          </div>
          <div className="card rate-card">
            <div className="rate-head">
              <span className="rate-label">Scan verification</span>
              <span className="rate-value">{loading ? <span className="skeleton skeleton-text" /> : rate === null ? '—' : `${Math.round(rate * 100)}%`}</span>
            </div>
            <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={rate === null ? undefined : Math.round(rate * 100)} aria-label="Share of shared scans verified">
              <span style={{ width: `${(rate ?? 0) * 100}%` }} />
            </div>
            <p className="rate-desc">
              {loading ? ' ' : scans === 0
                ? 'No scans shared yet.'
                : `${verified.toLocaleString()} of ${scans.toLocaleString()} shared scans are verified. Only verified scans count toward outbreak levels.`}
            </p>
            <Link to="/moderation" className="link-arrow">Open Review <Icon name="arrowRight" size={14} /></Link>
          </div>
        </div>
      </section>

      <PageSection title="How alerts work">
        <HelpText summary="Show how outbreak alerts are decided" defaultOpen={false}>
          <ol className="steps">
            <li>
              Each barangay first <strong>collects data</strong> (at least 4 weeks and 10 verified scans); until then its
              outbreak status is <em>Undetermined</em> and no alerts are sent.
            </li>
            <li>
              A barangay without enough data of its own <strong>borrows the baseline</strong> of the barangays bordering it,
              if they have enough. If neither has, a cluster of 5 or more farms there is not alerted but listed under
              "Clusters without a baseline" for you to review.
            </li>
            <li>
              After that, the farms reporting a disease in the last 7 days are compared with the barangay's own baseline
              (verified scans only; see the <Link to="/map">Map</Link> page for the thresholds), and alerts are
              sent <strong>automatically</strong> when the level goes up.
            </li>
            <li>
              Reaching <strong>Moderate</strong> (2× the baseline) sends an awareness notice; reaching <strong>Severe</strong> (3×)
              sends a potential-outbreak warning. Farmers in that barangay and the ones bordering it (including in
              neighbouring towns) are notified, and the same level isn't repeated there within 14 days.
            </li>
            <li>
              Every alert is listed under <Link to="/outbreaks">Outbreaks</Link>, where you can mark a false alarm.
              Rejecting scans in <Link to="/moderation">Review</Link> lowers the level on the next recompute.
            </li>
          </ol>
        </HelpText>
      </PageSection>
    </div>
  );
}

function StatTile({ tile, value, emphasise = false }: { tile: Tile; value: number | null; emphasise?: boolean }) {
  const active = emphasise && value !== null && value > 0;
  const className = `stat-card${emphasise ? ' stat-card-lg' : ''}${active ? ` is-${tile.tone ?? 'warning'}` : ''}${tile.to ? ' is-link' : ''}`;
  const content = (
    <>
      <span className={`stat-icon${active ? ` tone-${tile.tone}` : ''}`} aria-hidden="true">
        <Icon name={tile.icon} size={emphasise ? 22 : 20} />
      </span>
      <span className="stat-info">
        <span className="stat-value">{value === null ? <span className="skeleton skeleton-num" /> : value.toLocaleString()}</span>
        <span className="stat-label">{tile.label}</span>
        {emphasise && value !== null && (
          <span className="stat-hint">{active ? tile.hint : 'Nothing pending'}</span>
        )}
      </span>
      {tile.to && <Icon name="chevronRight" size={18} className="stat-chevron" />}
    </>
  );
  return tile.to
    ? <Link to={tile.to} className={className}>{content}</Link>
    : <div className={className}>{content}</div>;
}

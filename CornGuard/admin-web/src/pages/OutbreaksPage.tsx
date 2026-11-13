import { useMemo, useState } from 'react';
import { useMutation, useQuery } from 'convex/react';
import { Link } from 'react-router-dom';
import { api } from '../convex';
import { DataTable } from '../components/DataTable';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Tabs } from '../components/Tabs';
import { Alert, Button, HelpText, PageLoader, PageSection } from '../components/ui';
import { date, diseaseLabel } from '../labels';

/**
 * Log of the automatic disease-level alerts (backend/convex/outbreakAlerts.ts). Nothing waits for an
 * admin: a barangay rising to Moderate sends an awareness notice, rising to Severe an outbreak
 * warning, to farmers in that barangay and the bordering ones. An admin can mark one a false alarm:
 * within its first minute that cancels it, afterwards it only records the review.
 */
type Severity = {
  diseaseCode: string; tier: string | null; farms: number; reports: number; score: number;
  baselineFarms: number | null; lastWeekFarms: number | null; severeAt: number | null;
  monitoringStatus: 'collecting' | 'provisional' | 'established' | null;
};
/** The numbers when the alert fired (backend outbreakAlerts.autoAlert); null on older rows and test alerts. */
type Trigger = {
  diseaseCode: string; farms: number; baselineFarms: number; threshold: number; monitoringStatus: string;
  baselineSource?: string;
};
/** A cluster in an area with no baseline (admin.dataPoorClusters): not alerted, listed for review. */
type Cluster = {
  barangay: string; municipality: string; diseaseCode: string; farms: number; reports: number; score: number;
  lastUpdated: number;
};
type Outbreak = {
  outbreakId: string; barangay: string; municipality: string; diseaseCodes: string[];
  level: 'moderate' | 'severe';
  status: 'scheduled' | 'sent' | 'dismissed' | 'pending'; source: 'automatic' | 'manual';
  declaredAt: number; message: string | null; reviewedBy: string | null; reviewedAt: number | null; note: string | null;
  recipientsNotified: number | null;
  trigger: Trigger[] | null;
  severity: Severity[];
};
type Notification = { notificationId: string; type: string; title: string; deliveryStatus: string; createdAt: number };
type Filter = 'all' | 'severe' | 'moderate' | 'dismissed';

const LEVEL_BADGE = { moderate: 'badge-orange', severe: 'badge-red' } as const;
const STATUS_LABEL: Record<Outbreak['status'], string> = {
  scheduled: 'SENDING', sent: 'SENT', dismissed: 'FALSE ALARM', pending: 'NOT SENT',
};
const STATUS_BADGE: Record<Outbreak['status'], string> = {
  scheduled: 'badge-orange', sent: 'badge-green', dismissed: 'badge-gray', pending: 'badge-gray',
};

const matches = (o: Outbreak, f: Filter) =>
  f === 'all' ? true : f === 'dismissed' ? o.status === 'dismissed' : o.level === f && o.status !== 'dismissed';

const area = (barangay: string, municipality: string) => (
  <div className="cell-stack"><span>{barangay}</span><span className="cell-sub">{municipality}</span></div>
);

export default function OutbreaksPage() {
  const outbreaks = useQuery(api.admin.listOutbreaks, {}) as Outbreak[] | undefined;
  const notifications = useQuery(api.admin.recentNotifications, { limit: 100 }) as Notification[] | undefined;
  const clusters = useQuery(api.admin.dataPoorClusters, {}) as Cluster[] | undefined;
  const dismiss = useMutation(api.admin.dismissOutbreak);
  const [filter, setFilter] = useState<Filter>('all');
  const [confirm, setConfirm] = useState<Outbreak | null>(null);
  const [error, setError] = useState('');

  const rows = useMemo(() => (outbreaks ?? []).filter((o) => matches(o, filter)), [outbreaks, filter]);
  const count = (f: Filter) => (outbreaks ?? []).filter((o) => matches(o, f)).length;

  if (outbreaks === undefined) return <PageLoader label="Loading alerts…" />;

  const run = async () => {
    if (!confirm) return;
    setError('');
    try {
      await dismiss({ outbreakId: confirm.outbreakId });
    } catch (e: any) {
      setError(e?.data ?? e?.message ?? 'Action failed');
    }
  };

  return (
    <div className="page-stack">
      {/* ---------- Clusters in areas without a baseline ---------- */}
      {clusters && clusters.length > 0 && (
        <PageSection
          tone="warning"
          title={<>Clusters without a baseline <span className="badge badge-orange">{clusters.length}</span></>}
          description="No automatic alert was sent for these."
          flush
        >
          <div className="card-alert">
            <Alert variant="warning">
              Many farms reported the same disease this week in a barangay where neither it nor the barangays bordering
              it have enough data yet to know what is normal, so no automatic alert went out. Check the scans in{' '}
              <Link to="/moderation">Review</Link> and, if they look real, contact the Municipal Agriculture Office.
            </Alert>
          </div>
          <DataTable<Cluster>
            data={clusters}
            keyField={(c) => `${c.barangay}||${c.municipality}||${c.diseaseCode}`}
            emptyMessage=""
            columns={[
              { key: 'barangay', header: 'Area', minWidth: '160px', render: (c) => area(c.barangay, c.municipality) },
              { key: 'diseaseCode', header: 'Disease', render: (c) => diseaseLabel(c.diseaseCode) },
              { key: 'farms', header: 'Farms (7 days)', width: '130px', align: 'right', render: (c) => <strong className="num">{c.farms}</strong> },
              { key: 'reports', header: 'Reports', width: '100px', align: 'right', hideBelow: 'md', render: (c) => <span className="num">{c.reports}</span> },
              { key: 'lastUpdated', header: 'Last report', width: '170px', hideBelow: 'md', render: (c) => <span className="cell-date">{date(c.lastUpdated)}</span> },
            ]}
          />
        </PageSection>
      )}

      {/* ---------- Automatic alerts ---------- */}
      <PageSection
        title="Automatic alerts"
        description="Sent when a barangay's level for a disease goes up."
        flush
      >
        <div className="card-intro">
          <HelpText summary="When are alerts sent, and to whom?">
            <p>
              Sent automatically when a barangay's level for a disease goes up: <strong>Moderate</strong> (2× its
              usual level) sends an awareness notice, <strong>Severe</strong> (3×) a potential-outbreak warning.
              Barangays still collecting data for their baseline (outbreak status Undetermined) send nothing.
              Recipients are farmers in that barangay and the barangays bordering it (including those in neighbouring
              towns). The same level is not repeated for the same barangay and disease within 14 days.
            </p>
          </HelpText>
          {error && <Alert variant="error" onDismiss={() => setError('')}>{String(error)}</Alert>}
          <Tabs value={filter} onChange={(v) => setFilter(v as Filter)} label="Filter alerts" tabs={[
            { id: 'all', label: 'All', count: count('all') }, { id: 'severe', label: 'Severe', count: count('severe') },
            { id: 'moderate', label: 'Moderate', count: count('moderate') }, { id: 'dismissed', label: 'False alarms', count: count('dismissed') }]} />
        </div>
        <DataTable<Outbreak>
          data={rows}
          keyField="outbreakId"
          emptyTitle="No alerts"
          emptyMessage={filter === 'all'
            ? 'No alerts yet. They appear here when a barangay reaches Moderate or Severe.'
            : 'No alerts in this list.'}
          columns={[
            { key: 'declaredAt', header: 'Sent', sortable: true, width: '150px', render: (r) => <span className="cell-date">{date(r.declaredAt)}</span> },
            { key: 'level', header: 'Level', width: '100px', render: (r) => (
              <span className={`badge ${LEVEL_BADGE[r.level]}`}>{r.level.toUpperCase()}</span>) },
            { key: 'barangay', header: 'Area', sortable: true, minWidth: '150px', render: (r) => area(r.barangay, r.municipality) },
            { key: 'diseaseCodes', header: 'Disease(s)', minWidth: '140px', render: (r) => r.diseaseCodes.map(diseaseLabel).join(', ') },
            { key: 'trigger', header: 'Why it fired', minWidth: '220px', render: (r) => r.trigger?.length
              ? <div className="cell-stack">{r.trigger.map((t) => (
                <div key={t.diseaseCode} className="trigger-line">
                  {diseaseLabel(t.diseaseCode)}: <strong>{t.farms} farms</strong> vs usual {t.baselineFarms.toFixed(1)}/week
                  {t.baselineSource === 'neighbours' ? ' from nearby areas' : ''}
                  <span className="cell-sub">
                    {' '}({r.level === 'severe' ? 'Severe' : 'Moderate'} at {t.threshold}
                    {t.monitoringStatus === 'provisional' ? ', provisional baseline' : ''})
                  </span>
                </div>))}</div>
              : <span className="text-muted">—</span> },
            { key: 'severity', header: 'Level now', width: '120px', render: (r) => (
              <div className="badge-row">{r.severity.map((s) => (
                <span key={s.diseaseCode} className={`badge ${s.tier === 'severe' ? 'badge-red' : s.tier === 'moderate' ? 'badge-orange' : 'badge-gray'}`}
                  title={`${diseaseLabel(s.diseaseCode)}: ${s.farms} farm(s), ${s.reports} report(s) in the last 7 days` +
                    (s.lastWeekFarms != null ? `; ${s.lastWeekFarms} farm(s) the week before` : '') +
                    (s.baselineFarms != null ? `; usual ${s.baselineFarms.toFixed(1)} farm(s) a week` +
                      (s.monitoringStatus === 'provisional' ? ' (provisional)' : '') : '')}>
                  {(s.tier ?? 'none').toUpperCase()}</span>))}</div>) },
            { key: 'recipientsNotified', header: 'Notified', width: '90px', align: 'right', hideBelow: 'lg', render: (r) => <span className="num">{r.recipientsNotified ?? '—'}</span> },
            { key: 'status', header: 'Status', width: '120px', render: (r) => (
              <span className={`badge ${STATUS_BADGE[r.status]}`}>{r.source === 'manual' ? 'TEST' : STATUS_LABEL[r.status]}</span>) },
            { key: 'note', header: 'Review', minWidth: '160px', hideBelow: 'lg', render: (r) => r.reviewedBy
              ? <span className="cell-sub">{`${r.reviewedBy} · ${date(r.reviewedAt)}${r.note ? ` — ${r.note}` : ''}`}</span>
              : <span className="cell-sub">{r.message ?? '—'}</span> },
            { key: 'outbreakId', header: '', width: '130px', align: 'right', render: (r) => r.status === 'dismissed' ? null : (
              <Button size="sm" variant="secondary" onClick={() => setConfirm(r)}>False alarm</Button>) },
          ]}
        />
      </PageSection>

      {/* ---------- Delivery log ---------- */}
      <PageSection title="Push delivery log" description="The last 100 notifications sent to farmers' phones." flush>
        <DataTable<Notification>
          data={notifications ?? []}
          loading={notifications === undefined}
          keyField="notificationId"
          emptyTitle="Nothing sent yet"
          emptyMessage="No notifications sent yet."
          searchKeys={['title', 'type']}
          searchPlaceholder="Search notifications"
          columns={[
            { key: 'createdAt', header: 'Time', sortable: true, width: '170px', render: (r) => <span className="cell-date">{date(r.createdAt)}</span> },
            { key: 'type', header: 'Type', sortable: true, width: '150px', hideBelow: 'md', render: (r) => <code className="code-chip">{r.type}</code> },
            { key: 'title', header: 'Title', minWidth: '200px' },
            { key: 'deliveryStatus', header: 'Delivery', width: '110px', render: (r) => (
              <span className={`badge badge-dot ${r.deliveryStatus === 'sent' ? 'badge-green' : r.deliveryStatus === 'failed' ? 'badge-red' : 'badge-orange'}`}>{r.deliveryStatus}</span>) },
          ]}
        />
      </PageSection>

      <ConfirmDialog
        isOpen={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={run}
        title="Mark as false alarm?"
        message={confirm?.status === 'scheduled'
          ? `This alert for ${confirm.barangay} hasn't gone out yet; it will be cancelled.`
          : `This alert for ${confirm?.barangay ?? ''} was already sent; farmers won't be messaged again. Consider rejecting the scans behind it in Review.`}
        confirmText="Mark false alarm"
        variant="danger"
      />
    </div>
  );
}

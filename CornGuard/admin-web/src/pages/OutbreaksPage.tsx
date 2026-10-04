import { useMemo, useState } from 'react';
import { useMutation, useQuery } from 'convex/react';
import { api } from '../convex';
import { DataTable } from '../components/DataTable';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Tabs } from '../components/Tabs';
import { ALERT_DISEASES, BARANGAYS_BY_MUNICIPALITY, MUNICIPALITIES, date, diseaseLabel, statusBadge } from '../labels';

/**
 * caps 3's Alerts page, reworked for CornGuard's review flow: Severe outbreaks wait here as
 * "pending" until an admin approves (farmers are notified) or dismisses them (nobody is).
 * Admins can also raise an alert by hand.
 */
type Severity = { diseaseCode: string; tier: string | null; farms: number; reports: number; score: number };
type Outbreak = {
  outbreakId: string; barangay: string; municipality: string; diseaseCodes: string[];
  status: 'pending' | 'approved' | 'dismissed'; source: 'automatic' | 'manual';
  declaredAt: number; message: string | null; reviewedBy: string | null; reviewedAt: number | null; note: string | null;
  recipientsNotified: number | null; wouldNotify: { farmersNearby: number; withDevice: number } | null;
  severity: Severity[];
};
type Notification = { notificationId: string; type: string; title: string; deliveryStatus: string; createdAt: number };

export default function OutbreaksPage() {
  const outbreaks = useQuery(api.admin.listOutbreaks, {}) as Outbreak[] | undefined;
  const notifications = useQuery(api.admin.recentNotifications, { limit: 100 }) as Notification[] | undefined;
  const approve = useMutation(api.admin.approveOutbreak);
  const dismiss = useMutation(api.admin.dismissOutbreak);
  const [filter, setFilter] = useState<'all' | 'approved' | 'dismissed'>('all');
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [confirm, setConfirm] = useState<{ action: 'approve' | 'dismiss'; o: Outbreak } | null>(null);
  const [error, setError] = useState('');

  const pending = useMemo(() => (outbreaks ?? []).filter((o) => o.status === 'pending'), [outbreaks]);
  const history = useMemo(
    () => (outbreaks ?? []).filter((o) => o.status !== 'pending' && (filter === 'all' || o.status === filter)),
    [outbreaks, filter],
  );

  if (outbreaks === undefined) return <div className="loading">Loading…</div>;

  const run = async () => {
    if (!confirm) return;
    setError('');
    try {
      const args = { outbreakId: confirm.o.outbreakId, note: notes[confirm.o.outbreakId]?.trim() || undefined };
      await (confirm.action === 'approve' ? approve(args) : dismiss(args));
    } catch (e: any) {
      setError(e?.data ?? e?.message ?? 'Action failed');
    }
  };

  return (
    <div className="alerts-page">
      {/* ---------- Pending review ---------- */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <h2 className="card-title">Awaiting review ({pending.length})</h2>
        </div>
        <div className="card-body">
          {error && <div role="alert" style={{ color: 'var(--danger-red)', marginBottom: 12 }}>{error}</div>}
          {pending.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">✅</div>
              <div className="empty-title">Nothing to review</div>
              <div className="empty-desc">Outbreaks appear here when a barangay reaches Severe.</div>
            </div>
          ) : pending.map((o) => (
            <div key={o.outbreakId} className="card" style={{ marginBottom: 16, border: '1px solid #f59e0b' }}>
              <div className="card-body">
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                  <div>
                    <h3 style={{ margin: '0 0 4px 0' }}>{o.barangay}, {o.municipality}</h3>
                    <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Declared {date(o.declaredAt)}</div>
                  </div>
                  <div style={{ fontSize: 13, textAlign: 'right' }}>
                    Would notify <strong>{o.wouldNotify?.withDevice ?? 0}</strong> farmer(s) with the app
                    <br /><span style={{ color: 'var(--text-muted)' }}>{o.wouldNotify?.farmersNearby ?? 0} farmer profile(s) in this or bordering barangays</span>
                  </div>
                </div>
                <table className="data-table" style={{ marginTop: 12 }}>
                  <thead><tr><th>Disease</th><th>Severity now</th><th>Farms</th><th>Reports</th><th>Score</th></tr></thead>
                  <tbody>
                    {o.severity.map((s) => (
                      <tr key={s.diseaseCode}>
                        <td><strong>{diseaseLabel(s.diseaseCode)}</strong></td>
                        <td><span className={`badge ${s.tier === 'severe' ? 'badge-red' : s.tier === 'moderate' ? 'badge-orange' : 'badge-gray'}`}>
                          {(s.tier ?? 'none').toUpperCase()}</span></td>
                        <td>{s.farms}</td><td>{s.reports}</td><td>{s.score.toFixed(1)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {o.severity.some((s) => s.tier !== 'severe') && (
                  <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                    Severity has dropped since this was declared (e.g. scans were rejected) — consider dismissing.
                  </p>
                )}
                <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap', alignItems: 'center' }}>
                  <input className="form-input" style={{ flex: 1, minWidth: 220 }} placeholder="Note (optional, e.g. confirmed by DA technician)"
                    value={notes[o.outbreakId] ?? ''} onChange={(e) => setNotes({ ...notes, [o.outbreakId]: e.target.value })} />
                  <button className="btn btn-primary" onClick={() => setConfirm({ action: 'approve', o })}>Approve &amp; notify farmers</button>
                  <button className="btn btn-secondary" onClick={() => setConfirm({ action: 'dismiss', o })}>Dismiss</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <RaiseAlert />

      {/* ---------- History ---------- */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header"><h2 className="card-title">History</h2></div>
        <div className="card-body">
          <Tabs value={filter} onChange={(v) => setFilter(v as typeof filter)} tabs={[
            { id: 'all', label: 'All' }, { id: 'approved', label: 'Sent' }, { id: 'dismissed', label: 'Dismissed' }]} />
          <DataTable<Outbreak>
            data={history}
            keyField="outbreakId"
            emptyMessage="No reviewed outbreaks yet."
            columns={[
              { key: 'declaredAt', header: 'Declared', sortable: true, width: '170px', render: (r) => date(r.declaredAt) },
              { key: 'barangay', header: 'Area', sortable: true, render: (r) => `${r.barangay}, ${r.municipality}` },
              { key: 'diseaseCodes', header: 'Disease(s)', render: (r) => r.diseaseCodes.map(diseaseLabel).join(', ') },
              { key: 'status', header: 'Status', width: '110px', render: (r) => <span className={`badge ${statusBadge(r.status)}`}>{r.status === 'approved' ? 'SENT' : r.status.toUpperCase()}</span> },
              { key: 'source', header: 'Source', width: '100px' },
              { key: 'recipientsNotified', header: 'Notified', width: '90px', render: (r) => r.recipientsNotified ?? '—' },
              { key: 'reviewedBy', header: 'Reviewed by', render: (r) => r.reviewedBy ? `${r.reviewedBy} · ${date(r.reviewedAt)}` : '—' },
              { key: 'note', header: 'Note / message', render: (r) => r.note ?? r.message ?? '—' },
            ]}
          />
        </div>
      </div>

      {/* ---------- Delivery log ---------- */}
      <div className="card">
        <div className="card-header"><h2 className="card-title">Push delivery log</h2></div>
        <div className="card-body">
          <DataTable<Notification>
            data={notifications ?? []}
            loading={notifications === undefined}
            keyField="notificationId"
            emptyMessage="No notifications sent yet."
            columns={[
              { key: 'createdAt', header: 'Time', sortable: true, width: '170px', render: (r) => date(r.createdAt) },
              { key: 'type', header: 'Type', sortable: true, width: '150px' },
              { key: 'title', header: 'Title' },
              { key: 'deliveryStatus', header: 'Delivery', width: '110px', render: (r) => (
                <span className={`badge ${r.deliveryStatus === 'sent' ? 'badge-green' : r.deliveryStatus === 'failed' ? 'badge-red' : 'badge-orange'}`}>{r.deliveryStatus}</span>) },
            ]}
          />
        </div>
      </div>

      <ConfirmDialog
        isOpen={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={run}
        title={confirm?.action === 'approve' ? 'Send outbreak alert?' : 'Dismiss outbreak?'}
        message={confirm?.action === 'approve'
          ? `${confirm.o.wouldNotify?.withDevice ?? 0} farmer(s) in ${confirm.o.barangay} and bordering barangays will get a push about ${confirm.o.diseaseCodes.map(diseaseLabel).join(' and ')}.`
          : `Nobody will be notified about this outbreak in ${confirm?.o.barangay ?? ''}.`}
        confirmText={confirm?.action === 'approve' ? 'Send alert' : 'Dismiss'}
        variant={confirm?.action === 'approve' ? 'primary' : 'danger'}
      />
    </div>
  );
}

function RaiseAlert() {
  const raise = useMutation(api.admin.raiseAlert);
  const [municipality, setMunicipality] = useState('');
  const [barangay, setBarangay] = useState('');
  const [diseases, setDiseases] = useState<string[]>([]);
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);

  const valid = municipality && barangay && diseases.length > 0;
  const send = async () => {
    setStatus('');
    try {
      await raise({ barangay, municipality, diseaseCodes: diseases, message: message.trim() || undefined });
      setStatus(`Alert sent for ${barangay}, ${municipality}.`);
      setBarangay(''); setDiseases([]); setMessage('');
    } catch (e: any) {
      setStatus(`Failed: ${e?.data ?? e?.message ?? 'unknown error'}`);
    }
  };

  return (
    <div className="card" style={{ marginBottom: 24 }}>
      <div className="card-header"><h2 className="card-title">Raise an alert manually</h2></div>
      <div className="card-body">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          <div className="form-group">
            <label className="form-label" htmlFor="alert-municipality">Municipality *</label>
            <select id="alert-municipality" className="form-input" value={municipality}
              onChange={(e) => { setMunicipality(e.target.value); setBarangay(''); }}>
              <option value="">Select municipality…</option>
              {MUNICIPALITIES.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="alert-barangay">Barangay *</label>
            <select id="alert-barangay" className="form-input" value={barangay} disabled={!municipality}
              onChange={(e) => setBarangay(e.target.value)}>
              <option value="">{municipality ? 'Select barangay…' : 'Select municipality first'}</option>
              {(BARANGAYS_BY_MUNICIPALITY[municipality] ?? []).map((b) => <option key={b} value={b}>{b}</option>)}
            </select>
          </div>
          <div className="form-group">
            <span className="form-label">Disease(s) *</span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {ALERT_DISEASES.map((d) => (
                <label key={d} style={{ fontSize: 14 }}>
                  <input type="checkbox" checked={diseases.includes(d)}
                    onChange={(e) => setDiseases(e.target.checked ? [...diseases, d] : diseases.filter((x) => x !== d))} />{' '}
                  {diseaseLabel(d)}
                </label>
              ))}
            </div>
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label" htmlFor="alert-message">Message (optional — a standard message is used if empty)</label>
            <textarea id="alert-message" className="form-input" rows={2} value={message}
              placeholder="What should farmers in this area do?" onChange={(e) => setMessage(e.target.value)} />
          </div>
        </div>
        {status && <div role="status" style={{ marginTop: 8, fontSize: 14 }}>{status}</div>}
        <button className="btn btn-primary" style={{ marginTop: 12 }} disabled={!valid} onClick={() => setConfirmOpen(true)}>
          Send alert
        </button>
      </div>
      <ConfirmDialog isOpen={confirmOpen} onClose={() => setConfirmOpen(false)} onConfirm={send}
        title="Send this alert now?"
        message={`Farmers in ${barangay}, ${municipality} and bordering barangays will be notified immediately.`}
        confirmText="Send alert" variant="primary" />
    </div>
  );
}

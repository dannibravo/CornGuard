import { useState } from 'react';
import { useQuery, useMutation } from 'convex/react';
import { api } from '../convex';
import { DataTable } from '../components/DataTable';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ImageThumb } from '../components/ImageThumb';
import { date, diseaseLabel, pct, statusBadge } from '../labels';
import { useAdmin } from '../auth';

/**
 * caps 3's Users page for CornGuard. Accounts are suspended rather than deleted, so their history
 * stays auditable; a suspended account can't post or share, gets no alerts, and its scans stop
 * counting toward outbreaks.
 */
type User = {
  userId: string; name: string | null; email: string | null; role: 'farmer' | 'admin'; accountStatus: 'active' | 'suspended';
  barangay: string; municipality: string; createdAt: number; hasDevice: boolean;
  scansShared: number; scansVerified: number; scansRejected: number; posts: number; postsRemoved: number;
};
type Scan = { recordId: string; diseaseCode: string; confidence: number; status: string; imageUrl: string | null;
  barangay: string; municipality: string; capturedAt: number };
type Pending = { kind: 'suspend' | 'reactivate' | 'promote'; user: User } | null;

export default function UsersPage() {
  const users = useQuery(api.admin.listUsers, {}) as User[] | undefined;
  const setStatus = useMutation(api.admin.setAccountStatus);
  const promote = useMutation(api.admin.promoteToAdmin);
  const { viewer } = useAdmin();
  const [pending, setPending] = useState<Pending>(null);
  const [scansOf, setScansOf] = useState<User | null>(null);
  const [error, setError] = useState('');

  if (users === undefined) return <div className="loading">Loading users…</div>;

  const run = async () => {
    if (!pending) return;
    setError('');
    try {
      if (pending.kind === 'promote') await promote({ userId: pending.user.userId });
      else await setStatus({ userId: pending.user.userId, status: pending.kind === 'suspend' ? 'suspended' : 'active' });
    } catch (e: any) { setError(e?.data ?? e?.message ?? 'Action failed'); }
  };
  const who = (u: User) => u.name ?? u.email ?? 'this user';

  return (
    <div className="users-page">
      <div className="card">
        <div className="card-header"><h2 className="card-title">All Users ({users.length})</h2></div>
        <div className="card-body" style={{ padding: 0 }}>
          {error && <div role="alert" style={{ color: 'var(--danger-red)', margin: 12 }}>{error}</div>}
          <DataTable<User>
            data={users}
            keyField="userId"
            emptyMessage="No users yet."
            columns={[
              { key: 'name', header: 'Name', sortable: true, render: (u) => <><strong>{u.name ?? '(no profile)'}</strong><br /><span style={{ fontSize: 12 }}>{u.email}</span></> },
              { key: 'role', header: 'Role', sortable: true, width: '90px', render: (u) => <span className={`badge ${u.role === 'admin' ? 'badge-blue' : 'badge-green'}`}>{u.role}</span> },
              { key: 'accountStatus', header: 'Status', sortable: true, width: '100px', render: (u) => <span className={`badge ${statusBadge(u.accountStatus)}`}>{u.accountStatus}</span> },
              { key: 'barangay', header: 'Location', sortable: true, render: (u) => u.barangay ? `${u.barangay}, ${u.municipality}` : '—' },
              { key: 'hasDevice', header: 'App push', width: '90px', render: (u) => (u.hasDevice ? '📱 yes' : '—') },
              { key: 'scansShared', header: 'Scans', sortable: true, width: '150px', render: (u) => <span style={{ fontSize: 12 }}>{u.scansShared} shared<br />{u.scansVerified} verified · {u.scansRejected} rejected</span> },
              { key: 'posts', header: 'Posts', sortable: true, width: '90px', render: (u) => <span style={{ fontSize: 12 }}>{u.posts}{u.postsRemoved ? ` (${u.postsRemoved} hidden)` : ''}</span> },
              { key: 'createdAt', header: 'Joined', sortable: true, width: '120px', render: (u) => new Date(u.createdAt).toLocaleDateString() },
              { key: 'actions', header: 'Actions', width: '230px', render: (u) => (
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <button className="btn btn-sm btn-secondary" onClick={() => setScansOf(u)}>Scans</button>
                  {u.userId !== viewer?.userId && (u.accountStatus === 'active'
                    ? <button className="btn btn-sm btn-danger" onClick={() => setPending({ kind: 'suspend', user: u })}>Suspend</button>
                    : <button className="btn btn-sm btn-primary" onClick={() => setPending({ kind: 'reactivate', user: u })}>Reactivate</button>)}
                  {u.role !== 'admin' && <button className="btn btn-sm btn-secondary" onClick={() => setPending({ kind: 'promote', user: u })}>Make admin</button>}
                </div>
              ) },
            ]}
          />
        </div>
      </div>

      <ConfirmDialog
        isOpen={!!pending}
        onClose={() => setPending(null)}
        onConfirm={run}
        title={pending?.kind === 'suspend' ? 'Suspend account?' : pending?.kind === 'reactivate' ? 'Reactivate account?' : 'Make admin?'}
        message={!pending ? '' : pending.kind === 'suspend'
          ? `${who(pending.user)} won't be able to post or share, won't get alerts, and their scans stop counting toward outbreaks. You can reactivate them later.`
          : pending.kind === 'reactivate'
            ? `${who(pending.user)} can use CornGuard normally again; their scans count again.`
            : `${who(pending.user)} will be able to use this admin site, approve outbreaks and moderate content.`}
        confirmText={pending?.kind === 'suspend' ? 'Suspend' : pending?.kind === 'reactivate' ? 'Reactivate' : 'Make admin'}
        variant={pending?.kind === 'suspend' ? 'danger' : 'primary'}
      />

      {scansOf && <UserScansModal user={scansOf} onClose={() => setScansOf(null)} />}
    </div>
  );
}

function UserScansModal({ user, onClose }: { user: User; onClose: () => void }) {
  const scans = useQuery(api.admin.listRecords, { userId: user.userId }) as Scan[] | undefined;
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal modal-lg" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">{user.name ?? user.email}'s shared scans ({scans?.length ?? '…'})</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close">×</button>
        </div>
        <div className="modal-body" style={{ maxHeight: '60vh', overflow: 'auto' }}>
          <DataTable<Scan>
            data={scans ?? []}
            loading={scans === undefined}
            keyField="recordId"
            emptyMessage="This user hasn't shared any scans."
            columns={[
              { key: 'imageUrl', header: 'Photo', width: '80px', render: (r) => <ImageThumb src={r.imageUrl} size="sm" onClick={() => {}} /> },
              { key: 'diseaseCode', header: 'Disease', sortable: true, render: (r) => diseaseLabel(r.diseaseCode) },
              { key: 'confidence', header: 'Confidence', sortable: true, width: '100px', render: (r) => pct(r.confidence) },
              { key: 'barangay', header: 'Area', render: (r) => `${r.barangay}, ${r.municipality}` },
              { key: 'capturedAt', header: 'Scanned', sortable: true, width: '170px', render: (r) => date(r.capturedAt) },
              { key: 'status', header: 'Status', width: '110px', render: (r) => <span className={`badge ${statusBadge(r.status)}`}>{r.status}</span> },
            ]}
          />
        </div>
      </div>
    </div>
  );
}

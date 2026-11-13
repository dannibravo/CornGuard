import { useMemo, useState } from 'react';
import { useQuery, useMutation } from 'convex/react';
import { api } from '../convex';
import { DataTable } from '../components/DataTable';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ImageThumb } from '../components/ImageThumb';
import { Modal } from '../components/Modal';
import { Tabs } from '../components/Tabs';
import { Icon } from '../components/Icon';
import { ActionGroup, Alert, Avatar, Button, PageLoader, PageSection } from '../components/ui';
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
type Pending = { kind: 'suspend' | 'reactivate'; user: User } | null;
type Filter = 'all' | 'farmer' | 'admin' | 'suspended';

export default function UsersPage() {
  const users = useQuery(api.admin.listUsers, {}) as User[] | undefined;
  const setStatus = useMutation(api.admin.setAccountStatus);
  const { viewer } = useAdmin();
  const [pending, setPending] = useState<Pending>(null);
  const [scansOf, setScansOf] = useState<User | null>(null);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<Filter>('all');

  const counts = useMemo(() => {
    const list = users ?? [];
    return {
      all: list.length,
      farmer: list.filter((u) => u.role === 'farmer').length,
      admin: list.filter((u) => u.role === 'admin').length,
      suspended: list.filter((u) => u.accountStatus === 'suspended').length,
    };
  }, [users]);

  const rows = useMemo(() => (users ?? []).filter((u) =>
    filter === 'all' ? true : filter === 'suspended' ? u.accountStatus === 'suspended' : u.role === filter), [users, filter]);

  if (users === undefined) return <PageLoader label="Loading users…" />;

  const run = async () => {
    if (!pending) return;
    setError('');
    try {
      await setStatus({ userId: pending.user.userId, status: pending.kind === 'suspend' ? 'suspended' : 'active' });
    } catch (e: any) { setError(e?.data ?? e?.message ?? 'Action failed'); }
  };
  const who = (u: User) => u.name ?? u.email ?? 'this user';

  return (
    <div className="page-stack">
      <PageSection title="All users" description={`${users.length.toLocaleString()} accounts. Suspending keeps history for auditing.`} flush>
        {error && <div className="card-alert"><Alert variant="error" onDismiss={() => setError('')}>{String(error)}</Alert></div>}
        <DataTable<User>
          data={rows}
          keyField="userId"
          emptyTitle="No users"
          emptyMessage={filter === 'all' ? 'No users yet.' : 'No users in this group.'}
          searchKeys={['name', 'email', 'barangay', 'municipality']}
          searchPlaceholder="Search name, email or place"
          toolbar={
            <Tabs variant="pill" label="Filter users" value={filter} onChange={(v) => setFilter(v as Filter)} tabs={[
              { id: 'all', label: 'All', count: counts.all },
              { id: 'farmer', label: 'Farmers', count: counts.farmer },
              { id: 'admin', label: 'Admins', count: counts.admin },
              { id: 'suspended', label: 'Suspended', count: counts.suspended }]} />
          }
          columns={[
            { key: 'name', header: 'Name', sortable: true, minWidth: '200px', render: (u) => (
              <div className="user-cell">
                <Avatar name={u.name ?? u.email} size="sm" />
                <div className="cell-stack">
                  <strong>{u.name ?? '(no profile)'}</strong>
                  <span className="cell-sub">{u.email}</span>
                </div>
              </div>) },
            { key: 'role', header: 'Role', sortable: true, width: '90px', render: (u) => <span className={`badge ${u.role === 'admin' ? 'badge-blue' : 'badge-green'}`}>{u.role}</span> },
            { key: 'accountStatus', header: 'Status', sortable: true, width: '110px', render: (u) => <span className={`badge badge-dot ${statusBadge(u.accountStatus)}`}>{u.accountStatus}</span> },
            { key: 'barangay', header: 'Location', sortable: true, minWidth: '160px', render: (u) => u.barangay
              ? <div className="cell-stack"><span>{u.barangay}</span><span className="cell-sub">{u.municipality}</span></div>
              : <span className="text-muted">—</span> },
            { key: 'hasDevice', header: 'App push', width: '90px', hideBelow: 'xl', render: (u) => (u.hasDevice
              ? <span className="inline-icon" title="Has the app with push notifications"><Icon name="phone" size={15} /> Yes</span>
              : <span className="text-muted">—</span>) },
            { key: 'scansShared', header: 'Scans', sortable: true, minWidth: '170px', nowrap: true, render: (u) => (
              <div className="cell-stack" title={`${u.scansShared} shared · ${u.scansVerified} verified · ${u.scansRejected} rejected`}>
                <span className="num">{u.scansShared} shared</span>
                <span className="cell-sub"><span className="text-success">{u.scansVerified} verified</span> · <span className={u.scansRejected ? 'text-danger' : ''}>{u.scansRejected} rejected</span></span>
              </div>) },
            { key: 'posts', header: 'Posts', sortable: true, width: '90px', hideBelow: 'md', render: (u) => (
              <div className="cell-stack"><span className="num">{u.posts}</span>{!!u.postsRemoved && <span className="cell-sub">{u.postsRemoved} hidden</span>}</div>) },
            { key: 'createdAt', header: 'Joined', sortable: true, width: '110px', hideBelow: 'xl', nowrap: true, render: (u) => new Date(u.createdAt).toLocaleDateString() },
            { key: 'actions', header: '', width: '190px', align: 'right', render: (u) => (
              <ActionGroup align="end">
                <Button size="sm" variant="secondary" onClick={() => setScansOf(u)}>Scans</Button>
                {u.userId !== viewer?.userId && (u.accountStatus === 'active'
                  ? <Button size="sm" variant="danger" onClick={() => setPending({ kind: 'suspend', user: u })}>Suspend</Button>
                  : <Button size="sm" variant="primary" onClick={() => setPending({ kind: 'reactivate', user: u })}>Reactivate</Button>)}
              </ActionGroup>
            ) },
          ]}
        />
      </PageSection>

      <ConfirmDialog
        isOpen={!!pending}
        onClose={() => setPending(null)}
        onConfirm={run}
        title={pending?.kind === 'suspend' ? 'Suspend account?' : 'Reactivate account?'}
        message={!pending ? '' : pending.kind === 'suspend'
          ? `${who(pending.user)} won't be able to post or share, won't get alerts, and their scans stop counting toward outbreaks. You can reactivate them later.`
          : `${who(pending.user)} can use CornGuard normally again; their scans count again.`}
        confirmText={pending?.kind === 'suspend' ? 'Suspend' : 'Reactivate'}
        variant={pending?.kind === 'suspend' ? 'danger' : 'primary'}
      />

      {scansOf && <UserScansModal user={scansOf} onClose={() => setScansOf(null)} />}
    </div>
  );
}

function UserScansModal({ user, onClose }: { user: User; onClose: () => void }) {
  const scans = useQuery(api.admin.listRecords, { userId: user.userId }) as Scan[] | undefined;
  return (
    <Modal isOpen onClose={onClose} size="lg" title={<>{user.name ?? user.email}'s shared scans <span className="text-muted">({scans?.length ?? '…'})</span></>}>
      <DataTable<Scan>
        data={scans ?? []}
        loading={scans === undefined}
        keyField="recordId"
        emptyTitle="No scans"
        emptyMessage="This user hasn't shared any scans."
        columns={[
          { key: 'imageUrl', header: 'Photo', width: '72px', render: (r) => <ImageThumb src={r.imageUrl} size="sm" alt={diseaseLabel(r.diseaseCode)} onClick={() => {}} /> },
          { key: 'diseaseCode', header: 'Disease', sortable: true, render: (r) => diseaseLabel(r.diseaseCode) },
          { key: 'confidence', header: 'Confidence', sortable: true, width: '110px', align: 'right', render: (r) => <span className="num">{pct(r.confidence)}</span> },
          { key: 'barangay', header: 'Area', hideBelow: 'md', render: (r) => `${r.barangay}, ${r.municipality}` },
          { key: 'capturedAt', header: 'Scanned', sortable: true, width: '170px', hideBelow: 'lg', render: (r) => date(r.capturedAt) },
          { key: 'status', header: 'Status', width: '110px', render: (r) => <span className={`badge ${statusBadge(r.status)}`}>{r.status}</span> },
        ]}
      />
    </Modal>
  );
}

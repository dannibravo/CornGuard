import { useCallback, useState } from 'react';
import { useQuery, useMutation } from 'convex/react';
import { api } from '../convex';
import { DataTable } from '../components/DataTable';
import { ImageThumb } from '../components/ImageThumb';
import { Tabs } from '../components/Tabs';
import { ActionGroup, Alert, Button, Note } from '../components/ui';
import { date, diseaseLabel, pct, REPORT_REASON_LABEL, statusBadge } from '../labels';

/**
 * caps 3's Moderation page for CornGuard. Nothing is permanently deleted: scans are verified or
 * rejected (rejected scans stop counting toward outbreaks), posts are verified/rejected and
 * hidden/removed, comments hidden/removed — all reversible and auditable.
 */
type Scan = {
  recordId: string; userId: string; userName: string; userSuspended: boolean; diseaseCode: string; confidence: number;
  status: 'unverified' | 'verified' | 'rejected'; imageUrl: string | null; barangay: string; municipality: string;
  hasLocation: boolean; capturedAt: number; sharedAt: number; modelVersion: string; verifiedBy: string | null; verifiedAt: number | null;
};
type Post = {
  postId: string; userName: string; userSuspended: boolean; title: string; body: string; diseaseTag: string;
  imageUrl: string | null; barangay: string; municipality: string; createdAt: number;
  verificationStatus: string; moderationStatus: string; upvotes: number; comments: number;
  linkedScan: { recordId: string; diseaseCode: string; confidence: number; status: string; imageUrl: string | null; sameAuthor: boolean } | null;
  authorHistory: { scansShared: number; scansVerified: number; scansRejected: number; posts: number; postsRemoved: number };
};
type Comment = { commentId: string; userName: string; body: string; postTitle: string; moderationStatus: string; createdAt: number };
type Report = {
  targetType: 'post' | 'comment'; targetId: string; postId: string; postTitle: string; text: string; imageUrl: string | null;
  authorName: string; moderationStatus: string; reporterCount: number; reasonCounts: Record<string, number>; notes: string[];
  firstReportedAt: number; lastReportedAt: number; outcome: 'dismissed' | 'actioned' | null; resolvedAt: number | null;
};

export default function ModerationPage() {
  const [tab, setTab] = useState<'scans' | 'reports' | 'posts' | 'comments'>('scans');
  const stats = useQuery(api.admin.stats);
  return (
    <div className="card section">
      <div className="card-intro card-intro-tabs">
        <Tabs value={tab} onChange={(v) => setTab(v as typeof tab)} label="Moderation sections" tabs={[
          { id: 'scans', label: 'Leaf scans', count: stats?.scansUnverified || undefined },
          { id: 'reports', label: 'Reported', count: stats?.reportsOpen || undefined },
          { id: 'posts', label: 'Posts', count: stats?.postsUnverified || undefined },
          { id: 'comments', label: 'Comments' }]} />
      </div>
      <div role="tabpanel" aria-labelledby={`tab-${tab}`}>
        {tab === 'scans' && <ScansTab />}
        {tab === 'reports' && <ReportsTab />}
        {tab === 'posts' && <PostsTab />}
        {tab === 'comments' && <CommentsTab />}
      </div>
    </div>
  );
}

/**
 * Runs a row action with feedback: the clicked button shows a spinner, the rest of that row's
 * buttons are disabled until it settles (no double submits), and a failure shows a dismissible banner.
 * Keys are `${rowId}:${action}`.
 */
function useAction() {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState<ReadonlySet<string>>(new Set());
  const wrap = (key: string, fn: () => Promise<unknown>) => async () => {
    setError('');
    setBusy((s) => new Set(s).add(key));
    try { await fn(); } catch (e: any) { setError(e?.data ?? e?.message ?? 'Action failed'); }
    finally { setBusy((s) => { const n = new Set(s); n.delete(key); return n; }); }
  };
  const isBusy = useCallback((key: string) => busy.has(key), [busy]);
  const rowBusy = useCallback((rowId: string) => [...busy].some((k) => k.startsWith(`${rowId}:`)), [busy]);
  const banner = error ? <div className="card-alert"><Alert variant="error" onDismiss={() => setError('')}>{String(error)}</Alert></div> : null;
  return { wrap, banner, isBusy, rowBusy };
}

function ScansTab() {
  const [status, setStatus] = useState<'unverified' | 'verified' | 'rejected' | 'all'>('unverified');
  const scans = useQuery(api.admin.listRecords, status === 'all' ? {} : { status }) as Scan[] | undefined;
  const setVerification = useMutation(api.admin.setRecordVerification);
  const { wrap, banner, isBusy, rowBusy } = useAction();

  return (
    <>
      <div className="card-intro">
        <Note>
          Shared scans count toward outbreaks only when <strong>verified</strong>. Scans ≥ 60 % with a photo are verified
          automatically; check the photo and reject anything that isn't a corn leaf or shows a different disease.
        </Note>
        <Tabs variant="pill" label="Scan status" value={status} onChange={(v) => setStatus(v as typeof status)} tabs={[
          { id: 'unverified', label: 'Awaiting review' }, { id: 'verified', label: 'Verified' },
          { id: 'rejected', label: 'Rejected' }, { id: 'all', label: 'All' }]} />
      </div>
      {banner}
      <DataTable<Scan>
        data={scans ?? []}
        loading={scans === undefined}
        keyField="recordId"
        emptyTitle={status === 'unverified' ? 'All caught up' : 'No scans'}
        emptyMessage={status === 'unverified' ? 'No scans are waiting for review.' : 'No scans in this list.'}
        searchKeys={['userName', 'barangay', 'municipality', 'diseaseCode']}
        searchPlaceholder="Search farmer, area or disease"
        rowClassName={(r) => (rowBusy(r.recordId) ? 'row-busy' : undefined)}
        columns={[
          { key: 'imageUrl', header: 'Photo', width: '72px', render: (r) => <ImageThumb src={r.imageUrl} size="md" alt={diseaseLabel(r.diseaseCode)} onClick={() => {}} /> },
          { key: 'diseaseCode', header: 'Model says', sortable: true, minWidth: '150px', render: (r) => (
            <div className="cell-stack"><strong>{diseaseLabel(r.diseaseCode)}</strong><span className="cell-sub num show-below-lg">{pct(r.confidence)} confidence</span></div>) },
          { key: 'confidence', header: 'Confidence', sortable: true, width: '110px', align: 'right', hideBelow: 'lg', render: (r) => <ConfidenceBar value={r.confidence} /> },
          { key: 'userName', header: 'Farmer', sortable: true, minWidth: '130px', render: (r) => <>{r.userName}{r.userSuspended && <> <span className="badge badge-red">suspended</span></>}</> },
          { key: 'barangay', header: 'Area', sortable: true, minWidth: '150px', render: (r) => r.barangay
            ? <div className="cell-stack"><span>{r.barangay}</span><span className="cell-sub">{[r.municipality, r.hasLocation ? '' : 'no GPS'].filter(Boolean).join(' · ')}</span></div>
            : <span className="badge badge-red" title="Can't be placed on the map, so it can't be verified">No location</span> },
          { key: 'capturedAt', header: 'Scanned', sortable: true, width: '150px', hideBelow: 'md', render: (r) => <span className="cell-date">{date(r.capturedAt)}</span> },
          { key: 'status', header: 'Status', width: '120px', render: (r) => <span className={`badge badge-dot ${statusBadge(r.status)}`} title={r.verifiedBy ? `by ${r.verifiedBy}` : 'automatic / not reviewed'}>{r.status.toUpperCase()}</span> },
          { key: 'actions', header: '', width: '180px', align: 'right', render: (r) => {
            const locked = rowBusy(r.recordId);
            return (
              <ActionGroup align="end">
                {r.status !== 'verified' && <Button size="sm" variant="primary" icon="check" disabled={!r.barangay || locked} busy={isBusy(`${r.recordId}:verify`)}
                  title={r.barangay ? undefined : "No location: this scan can't be placed on the map"}
                  onClick={wrap(`${r.recordId}:verify`, () => setVerification({ recordId: r.recordId, status: 'verified' }))}>Verify</Button>}
                {r.status !== 'rejected' && <Button size="sm" variant="danger" icon="close" disabled={locked} busy={isBusy(`${r.recordId}:reject`)}
                  onClick={wrap(`${r.recordId}:reject`, () => setVerification({ recordId: r.recordId, status: 'rejected' }))}>Reject</Button>}
              </ActionGroup>
            );
          } },
        ]}
      />
    </>
  );
}

function ConfidenceBar({ value }: { value: number }) {
  const tone = value >= 0.8 ? 'high' : value >= 0.6 ? 'mid' : 'low';
  return (
    <span className={`confidence confidence-${tone}`} title={pct(value)}>
      <span className="confidence-track"><span style={{ width: `${Math.round(value * 100)}%` }} /></span>
      <span className="num">{pct(value)}</span>
    </span>
  );
}

function PostsTab() {
  const posts = useQuery(api.admin.listPosts, {}) as Post[] | undefined;
  const setVerification = useMutation(api.admin.setPostVerification);
  const setModeration = useMutation(api.admin.setPostModeration);
  const { wrap, banner, isBusy, rowBusy } = useAction();

  const scanSignal = (p: Post) => {
    if (!p.linkedScan) return <span className="badge badge-gray">no linked scan</span>;
    const match = p.linkedScan.diseaseCode === p.diseaseTag;
    return (
      <div className="media-cell">
        <ImageThumb src={p.linkedScan.imageUrl} size="sm" alt="Linked scan" onClick={() => {}} />
        <div className="cell-stack">
          <span className="cell-sub">{diseaseLabel(p.linkedScan.diseaseCode)} · <span className="num">{pct(p.linkedScan.confidence)}</span></span>
          <div className="badge-row">
            <span className={`badge ${match ? 'badge-green' : 'badge-red'}`}>{match ? 'matches tag' : 'tag mismatch'}</span>
            <span className={`badge ${statusBadge(p.linkedScan.status)}`}>{p.linkedScan.status}</span>
            {!p.linkedScan.sameAuthor && <span className="badge badge-red">other user's scan</span>}
          </div>
        </div>
      </div>
    );
  };

  return (
    <>
      <div className="card-intro">
        <Note>
          Signs of an authentic post: a photo, a linked scan that matches the disease tag, an author whose scans are
          usually verified, and agreement from other farmers. <strong>Verify</strong> gives the post a verified badge;
          <strong> Hide/Remove</strong> takes it out of the feed (the author still sees it).
        </Note>
      </div>
      {banner}
      <DataTable<Post>
        data={posts ?? []}
        loading={posts === undefined}
        keyField="postId"
        emptyTitle="No posts"
        emptyMessage="No posts yet."
        searchKeys={['title', 'body', 'userName', 'barangay']}
        searchPlaceholder="Search posts"
        rowClassName={(r) => (rowBusy(r.postId) ? 'row-busy' : undefined)}
        columns={[
          { key: 'imageUrl', header: 'Photo', width: '72px', render: (r) => <ImageThumb src={r.imageUrl} size="md" alt={r.title} onClick={() => {}} /> },
          { key: 'title', header: 'Post', minWidth: '240px', render: (r) => (
            <div className="cell-stack post-cell">
              <strong>{r.title}</strong>
              <span className="clamp-2 cell-body">{r.body}</span>
              <div className="cell-meta">
                <span className="badge badge-blue">{diseaseLabel(r.diseaseTag)}</span>
                <span className="cell-sub">{r.barangay}, {r.municipality} · {date(r.createdAt)}</span>
              </div>
            </div>) },
          { key: 'linkedScan', header: 'Backed by scan', minWidth: '210px', hideBelow: 'md', render: scanSignal },
          { key: 'userName', header: 'Author', minWidth: '170px', render: (r) => (
            <div className="cell-stack">
              <span><strong>{r.userName}</strong>{r.userSuspended && <> <span className="badge badge-red">suspended</span></>}</span>
              <span className="cell-sub">{r.authorHistory.scansShared} scans · <span className="text-success">{r.authorHistory.scansVerified} verified</span> · {r.authorHistory.scansRejected} rejected</span>
              <span className="cell-sub">{r.authorHistory.posts} posts · {r.authorHistory.postsRemoved} hidden/removed</span>
            </div>) },
          { key: 'upvotes', header: 'Community', width: '100px', hideBelow: 'lg', render: (r) => (
            <div className="cell-stack cell-sub"><span>▲ {r.upvotes} upvotes</span><span>{r.comments} comments</span></div>) },
          { key: 'verificationStatus', header: 'Status', width: '120px', render: (r) => (
            <div className="badge-col">
              <span className={`badge ${statusBadge(r.verificationStatus)}`}>{r.verificationStatus}</span>
              <span className={`badge ${statusBadge(r.moderationStatus)}`}>{r.moderationStatus}</span>
            </div>) },
          { key: 'actions', header: '', width: '200px', align: 'right', render: (r) => {
            const k = (a: string) => `${r.postId}:${a}`;
            const locked = rowBusy(r.postId);
            return (
              <ActionGroup align="end">
                {r.verificationStatus !== 'verified' && <Button size="sm" variant="primary" disabled={locked} busy={isBusy(k('verify'))} onClick={wrap(k('verify'), () => setVerification({ postId: r.postId, status: 'verified' }))}>Verify</Button>}
                {r.verificationStatus !== 'rejected' && <Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('reject'))} onClick={wrap(k('reject'), () => setVerification({ postId: r.postId, status: 'rejected' }))}>Reject</Button>}
                {r.moderationStatus === 'visible'
                  ? <><Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('hide'))} onClick={wrap(k('hide'), () => setModeration({ postId: r.postId, status: 'hidden' }))}>Hide</Button>
                      <Button size="sm" variant="danger" disabled={locked} busy={isBusy(k('remove'))} onClick={wrap(k('remove'), () => setModeration({ postId: r.postId, status: 'removed' }))}>Remove</Button></>
                  : <Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('restore'))} onClick={wrap(k('restore'), () => setModeration({ postId: r.postId, status: 'visible' }))}>Restore</Button>}
              </ActionGroup>
            );
          } },
        ]}
      />
    </>
  );
}

function CommentsTab() {
  const comments = useQuery(api.admin.listComments, {}) as Comment[] | undefined;
  const setModeration = useMutation(api.admin.setCommentModeration);
  const { wrap, banner, isBusy, rowBusy } = useAction();
  return (
    <>
      {banner}
      <DataTable<Comment>
        data={comments ?? []}
        loading={comments === undefined}
        keyField="commentId"
        emptyTitle="No comments"
        emptyMessage="No comments yet."
        searchKeys={['userName', 'body', 'postTitle']}
        searchPlaceholder="Search comments"
        rowClassName={(r) => (rowBusy(r.commentId) ? 'row-busy' : undefined)}
        columns={[
          { key: 'userName', header: 'Author', sortable: true, width: '160px' },
          { key: 'body', header: 'Comment', minWidth: '240px', render: (r) => (
            <div className="cell-stack">
              <span className="clamp-3 cell-body">{r.body}</span>
              <span className="cell-sub">on &ldquo;{r.postTitle}&rdquo;</span>
            </div>) },
          { key: 'createdAt', header: 'Posted', sortable: true, width: '150px', hideBelow: 'md', render: (r) => <span className="cell-date">{date(r.createdAt)}</span> },
          { key: 'moderationStatus', header: 'Status', width: '110px', render: (r) => <span className={`badge badge-dot ${statusBadge(r.moderationStatus)}`}>{r.moderationStatus}</span> },
          { key: 'actions', header: '', width: '170px', align: 'right', render: (r) => {
            const k = (a: string) => `${r.commentId}:${a}`;
            const locked = rowBusy(r.commentId);
            return (
              <ActionGroup align="end">
                {r.moderationStatus === 'visible'
                  ? <><Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('hide'))} onClick={wrap(k('hide'), () => setModeration({ commentId: r.commentId, status: 'hidden' }))}>Hide</Button>
                      <Button size="sm" variant="danger" disabled={locked} busy={isBusy(k('remove'))} onClick={wrap(k('remove'), () => setModeration({ commentId: r.commentId, status: 'removed' }))}>Remove</Button></>
                  : <Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('restore'))} onClick={wrap(k('restore'), () => setModeration({ commentId: r.commentId, status: 'visible' }))}>Restore</Button>}
              </ActionGroup>
            );
          } },
        ]}
      />
    </>
  );
}

/**
 * Posts and comments farmers flagged, one row per item, most-reported first. Reports never hide
 * anything by themselves: Hide/Remove here (or in the Posts/Comments tabs) closes them as actioned,
 * Dismiss closes them with no change.
 */
function ReportsTab() {
  const [status, setStatus] = useState<'open' | 'resolved'>('open');
  const reports = useQuery(api.admin.listReports, { status }) as Report[] | undefined;
  const resolve = useMutation(api.admin.resolveReports);
  const { wrap, banner, isBusy, rowBusy } = useAction();
  const rows = (reports ?? []).map((r) => ({ ...r, key: `${r.targetType}:${r.targetId}` }));

  return (
    <>
      <div className="card-intro">
        <Note>
          Farmers can report posts and comments. A reported item stays visible to everyone except the farmer who
          reported it until you act. <strong>Hide/Remove</strong> takes it down; <strong>Dismiss</strong> keeps it up.
        </Note>
        <Tabs variant="pill" label="Report status" value={status} onChange={(v) => setStatus(v as typeof status)} tabs={[
          { id: 'open', label: 'Awaiting review' }, { id: 'resolved', label: 'Resolved' }]} />
      </div>
      {banner}
      <DataTable<(typeof rows)[number]>
        data={rows}
        loading={reports === undefined}
        keyField="key"
        emptyTitle={status === 'open' ? 'All caught up' : 'Nothing resolved yet'}
        emptyMessage={status === 'open' ? 'No open reports.' : 'No resolved reports yet.'}
        rowClassName={(r) => (rowBusy(r.key) ? 'row-busy' : undefined)}
        columns={[
          { key: 'imageUrl', header: 'Photo', width: '72px', render: (r) => r.targetType === 'post'
            ? <ImageThumb src={r.imageUrl} size="md" alt={r.postTitle} onClick={() => {}} />
            : <span className="badge badge-gray">comment</span> },
          { key: 'text', header: 'Reported content', minWidth: '240px', render: (r) => (
            <div className="cell-stack">
              <span>
                <span className={`badge ${r.targetType === 'post' ? 'badge-blue' : 'badge-gray'}`}>{r.targetType}</span>{' '}
                {r.targetType === 'post'
                  ? <strong>{r.postTitle}</strong>
                  : <span className="cell-sub">on &ldquo;{r.postTitle}&rdquo;</span>}
              </span>
              <span className="clamp-3 cell-body">{r.text}</span>
            </div>) },
          { key: 'authorName', header: 'Author', width: '140px', hideBelow: 'md' },
          { key: 'reasonCounts', header: 'Reasons', minWidth: '200px', render: (r) => (
            <div className="reason-list">
              {Object.entries(r.reasonCounts).sort((a, b) => b[1] - a[1]).map(([reason, n]) => (
                <span key={reason} className="reason"><span className="reason-count">{n}</span> {REPORT_REASON_LABEL[reason] ?? reason}</span>))}
              {r.notes.map((note, i) => <em key={i} className="reason-note">&ldquo;{note}&rdquo;</em>)}
            </div>) },
          { key: 'reporterCount', header: 'Reporters', sortable: true, width: '100px', align: 'right', hideBelow: 'lg', render: (r) => <strong className="num">{r.reporterCount}</strong> },
          { key: 'lastReportedAt', header: 'Last reported', sortable: true, width: '150px', hideBelow: 'lg', render: (r) => <span className="cell-date">{date(r.lastReportedAt)}</span> },
          { key: 'moderationStatus', header: 'Status', width: '110px', render: (r) => (
            <div className="badge-col">
              <span className={`badge ${statusBadge(r.moderationStatus)}`}>{r.moderationStatus}</span>
              {r.outcome && <span className={`badge ${r.outcome === 'actioned' ? 'badge-red' : 'badge-gray'}`}>{r.outcome}</span>}
            </div>) },
          { key: 'actions', header: '', width: '220px', align: 'right', render: (r) => {
            if (status !== 'open') return <span className="cell-sub">{r.resolvedAt ? date(r.resolvedAt) : '—'}</span>;
            const k = (a: string) => `${r.key}:${a}`;
            const locked = rowBusy(r.key);
            return (
              <ActionGroup align="end">
                <Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('dismiss'))} onClick={wrap(k('dismiss'), () => resolve({ targetType: r.targetType, targetId: r.targetId, outcome: 'dismissed' }))}>Dismiss</Button>
                {r.moderationStatus === 'visible' && <>
                  <Button size="sm" variant="secondary" disabled={locked} busy={isBusy(k('hide'))} onClick={wrap(k('hide'), () => resolve({ targetType: r.targetType, targetId: r.targetId, outcome: 'actioned', moderation: 'hidden' }))}>Hide</Button>
                  <Button size="sm" variant="danger" disabled={locked} busy={isBusy(k('remove'))} onClick={wrap(k('remove'), () => resolve({ targetType: r.targetType, targetId: r.targetId, outcome: 'actioned', moderation: 'removed' }))}>Remove</Button>
                </>}
              </ActionGroup>
            );
          } },
        ]}
      />
    </>
  );
}

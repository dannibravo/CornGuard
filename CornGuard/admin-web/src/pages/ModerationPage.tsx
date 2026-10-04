import { useState } from 'react';
import { useQuery, useMutation } from 'convex/react';
import { api } from '../convex';
import { DataTable } from '../components/DataTable';
import { ImageThumb } from '../components/ImageThumb';
import { Tabs } from '../components/Tabs';
import { date, diseaseLabel, pct, statusBadge } from '../labels';

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

export default function ModerationPage() {
  const [tab, setTab] = useState<'scans' | 'posts' | 'comments'>('scans');
  return (
    <div className="moderation-page">
      <div className="card">
        <div className="card-body">
          <Tabs value={tab} onChange={(v) => setTab(v as typeof tab)} tabs={[
            { id: 'scans', label: 'Leaf scans' }, { id: 'posts', label: 'Posts' }, { id: 'comments', label: 'Comments' }]} />
          {tab === 'scans' && <ScansTab />}
          {tab === 'posts' && <PostsTab />}
          {tab === 'comments' && <CommentsTab />}
        </div>
      </div>
    </div>
  );
}

function useAction() {
  const [error, setError] = useState('');
  const wrap = (fn: () => Promise<unknown>) => async () => {
    setError('');
    try { await fn(); } catch (e: any) { setError(e?.data ?? e?.message ?? 'Action failed'); }
  };
  const banner = error ? <div role="alert" style={{ color: 'var(--danger-red)', margin: '8px 0' }}>{error}</div> : null;
  return { wrap, banner };
}

function ScansTab() {
  const [status, setStatus] = useState<'unverified' | 'verified' | 'rejected' | 'all'>('unverified');
  const scans = useQuery(api.admin.listRecords, status === 'all' ? {} : { status }) as Scan[] | undefined;
  const setVerification = useMutation(api.admin.setRecordVerification);
  const { wrap, banner } = useAction();

  return (
    <>
      <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
        Shared scans count toward outbreaks only when <strong>verified</strong>. Scans ≥ 85 % with a photo are verified
        automatically; check the photo and reject anything that isn't a corn leaf or shows a different disease.
      </p>
      <Tabs value={status} onChange={(v) => setStatus(v as typeof status)} tabs={[
        { id: 'unverified', label: 'Awaiting review' }, { id: 'verified', label: 'Verified' },
        { id: 'rejected', label: 'Rejected' }, { id: 'all', label: 'All' }]} />
      {banner}
      <DataTable<Scan>
        data={scans ?? []}
        loading={scans === undefined}
        keyField="recordId"
        emptyMessage="No scans in this list."
        columns={[
          { key: 'imageUrl', header: 'Photo', width: '80px', render: (r) => <ImageThumb src={r.imageUrl} size="sm" alt={diseaseLabel(r.diseaseCode)} onClick={() => {}} /> },
          { key: 'diseaseCode', header: 'Model says', sortable: true, render: (r) => <strong>{diseaseLabel(r.diseaseCode)}</strong> },
          { key: 'confidence', header: 'Confidence', sortable: true, width: '100px', render: (r) => pct(r.confidence) },
          { key: 'userName', header: 'Farmer', sortable: true, render: (r) => <>{r.userName}{r.userSuspended && <> <span className="badge badge-red">suspended</span></>}</> },
          { key: 'barangay', header: 'Area', sortable: true, render: (r) => `${r.barangay || '—'}${r.municipality ? `, ${r.municipality}` : ''}${r.hasLocation ? '' : ' (no GPS)'}` },
          { key: 'capturedAt', header: 'Scanned', sortable: true, width: '170px', render: (r) => date(r.capturedAt) },
          { key: 'status', header: 'Status', width: '110px', render: (r) => <span className={`badge ${statusBadge(r.status)}`} title={r.verifiedBy ? `by ${r.verifiedBy}` : 'automatic / not reviewed'}>{r.status.toUpperCase()}</span> },
          { key: 'actions', header: 'Actions', width: '170px', render: (r) => (
            <div style={{ display: 'flex', gap: 8 }}>
              {r.status !== 'verified' && <button className="btn btn-sm btn-primary" onClick={wrap(() => setVerification({ recordId: r.recordId, status: 'verified' }))}>Verify</button>}
              {r.status !== 'rejected' && <button className="btn btn-sm btn-danger" onClick={wrap(() => setVerification({ recordId: r.recordId, status: 'rejected' }))}>Reject</button>}
            </div>
          ) },
        ]}
      />
    </>
  );
}

function PostsTab() {
  const posts = useQuery(api.admin.listPosts, {}) as Post[] | undefined;
  const setVerification = useMutation(api.admin.setPostVerification);
  const setModeration = useMutation(api.admin.setPostModeration);
  const { wrap, banner } = useAction();

  const scanSignal = (p: Post) => {
    if (!p.linkedScan) return <span className="badge badge-gray">no linked scan</span>;
    const match = p.linkedScan.diseaseCode === p.diseaseTag;
    return (
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <ImageThumb src={p.linkedScan.imageUrl} size="sm" alt="Linked scan" onClick={() => {}} />
        <div style={{ fontSize: 12 }}>
          {diseaseLabel(p.linkedScan.diseaseCode)} {pct(p.linkedScan.confidence)}<br />
          <span className={`badge ${match ? 'badge-green' : 'badge-red'}`}>{match ? 'matches tag' : 'tag mismatch'}</span>{' '}
          <span className={`badge ${statusBadge(p.linkedScan.status)}`}>{p.linkedScan.status}</span>
          {!p.linkedScan.sameAuthor && <> <span className="badge badge-red">other user's scan</span></>}
        </div>
      </div>
    );
  };

  return (
    <>
      <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
        Signs of an authentic post: a photo, a linked scan that matches the disease tag, an author whose scans are
        usually verified, and agreement from other farmers. <strong>Verify</strong> gives the post a verified badge;
        <strong> Hide/Remove</strong> takes it out of the feed (the author still sees it).
      </p>
      {banner}
      <DataTable<Post>
        data={posts ?? []}
        loading={posts === undefined}
        keyField="postId"
        emptyMessage="No posts yet."
        columns={[
          { key: 'imageUrl', header: 'Photo', width: '80px', render: (r) => <ImageThumb src={r.imageUrl} size="sm" alt={r.title} onClick={() => {}} /> },
          { key: 'title', header: 'Post', render: (r) => (
            <div><strong>{r.title}</strong><br /><span style={{ fontSize: 12 }}>{r.body.slice(0, 140)}</span><br />
              <span className="badge badge-blue">{diseaseLabel(r.diseaseTag)}</span>{' '}
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{r.barangay}, {r.municipality} · {date(r.createdAt)}</span></div>) },
          { key: 'linkedScan', header: 'Backed by scan', width: '230px', render: scanSignal },
          { key: 'userName', header: 'Author', width: '170px', render: (r) => (
            <div style={{ fontSize: 12 }}><strong>{r.userName}</strong>{r.userSuspended && <> <span className="badge badge-red">suspended</span></>}<br />
              {r.authorHistory.scansShared} scans · {r.authorHistory.scansVerified} verified · {r.authorHistory.scansRejected} rejected<br />
              {r.authorHistory.posts} posts · {r.authorHistory.postsRemoved} hidden/removed</div>) },
          { key: 'upvotes', header: 'Community', width: '100px', render: (r) => <span style={{ fontSize: 12 }}>👍 {r.upvotes}<br />💬 {r.comments}</span> },
          { key: 'verificationStatus', header: 'Status', width: '120px', render: (r) => (
            <><span className={`badge ${statusBadge(r.verificationStatus)}`}>{r.verificationStatus}</span><br />
              <span className={`badge ${statusBadge(r.moderationStatus)}`}>{r.moderationStatus}</span></>) },
          { key: 'actions', header: 'Actions', width: '200px', render: (r) => (
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {r.verificationStatus !== 'verified' && <button className="btn btn-sm btn-primary" onClick={wrap(() => setVerification({ postId: r.postId, status: 'verified' }))}>Verify</button>}
              {r.verificationStatus !== 'rejected' && <button className="btn btn-sm btn-secondary" onClick={wrap(() => setVerification({ postId: r.postId, status: 'rejected' }))}>Reject</button>}
              {r.moderationStatus === 'visible'
                ? <><button className="btn btn-sm btn-secondary" onClick={wrap(() => setModeration({ postId: r.postId, status: 'hidden' }))}>Hide</button>
                    <button className="btn btn-sm btn-danger" onClick={wrap(() => setModeration({ postId: r.postId, status: 'removed' }))}>Remove</button></>
                : <button className="btn btn-sm btn-secondary" onClick={wrap(() => setModeration({ postId: r.postId, status: 'visible' }))}>Restore</button>}
            </div>
          ) },
        ]}
      />
    </>
  );
}

function CommentsTab() {
  const comments = useQuery(api.admin.listComments, {}) as Comment[] | undefined;
  const setModeration = useMutation(api.admin.setCommentModeration);
  const { wrap, banner } = useAction();
  return (
    <>
      {banner}
      <DataTable<Comment>
        data={comments ?? []}
        loading={comments === undefined}
        keyField="commentId"
        emptyMessage="No comments yet."
        columns={[
          { key: 'userName', header: 'Author', sortable: true, width: '160px' },
          { key: 'postTitle', header: 'On post', width: '220px' },
          { key: 'body', header: 'Comment', render: (r) => r.body.slice(0, 200) },
          { key: 'createdAt', header: 'Posted', sortable: true, width: '170px', render: (r) => date(r.createdAt) },
          { key: 'moderationStatus', header: 'Status', width: '100px', render: (r) => <span className={`badge ${statusBadge(r.moderationStatus)}`}>{r.moderationStatus}</span> },
          { key: 'actions', header: 'Actions', width: '160px', render: (r) => (
            <div style={{ display: 'flex', gap: 6 }}>
              {r.moderationStatus === 'visible'
                ? <><button className="btn btn-sm btn-secondary" onClick={wrap(() => setModeration({ commentId: r.commentId, status: 'hidden' }))}>Hide</button>
                    <button className="btn btn-sm btn-danger" onClick={wrap(() => setModeration({ commentId: r.commentId, status: 'removed' }))}>Remove</button></>
                : <button className="btn btn-sm btn-secondary" onClick={wrap(() => setModeration({ commentId: r.commentId, status: 'visible' }))}>Restore</button>}
            </div>
          ) },
        ]}
      />
    </>
  );
}

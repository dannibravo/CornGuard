import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from 'convex/react';
import { api } from '../convex';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Icon } from '../components/Icon';
import { Alert, HelpText, PageSection } from '../components/ui';
import { GUIDE_CODES, REVIEW_STATUS_BADGE, REVIEW_STATUS_LABEL, date, diseaseLabel, type GuideReviewStatus } from '../labels';

/** A row of backend diseaseReferences.adminList. */
export type Guide = {
  diseaseCode: string; displayName: string; symptoms: string; causes: string; treatmentSteps: string;
  duration: string; preventionSteps: string; sourceReference: string; contentVersion: string;
  updatedAt: number; reviewStatus: GuideReviewStatus; reviewedBy: string; reviewedAt: number;
  updatedByName: string | null;
};

/**
 * Treatment guides: one per class the model detects. Farmers' apps subscribe to these and keep an
 * offline copy, so a save here reaches them within seconds of their next connection.
 */
export default function TreatmentGuidesPage() {
  const guides = useQuery(api.diseaseReferences.adminList, {}) as Guide[] | undefined;
  const remove = useMutation(api.diseaseReferences.remove);
  const [toDelete, setToDelete] = useState<string | null>(null);
  const [error, setError] = useState('');
  const byCode = new Map((guides ?? []).map((g) => [g.diseaseCode, g]));

  const runDelete = async () => {
    if (!toDelete) return;
    setError('');
    try {
      await remove({ diseaseCode: toDelete });
    } catch (e: any) {
      setError(String(e?.data ?? e?.message ?? 'Delete failed'));
    }
  };

  return (
    <div className="page-stack">
      <PageSection
        title="Guides by disease"
        description="One guide per disease the app can detect. Farmers see it after a scan, even offline."
      >
        <HelpText summary="How guides reach farmers">
          <p>
            Saving a guide publishes it immediately. Farmers' apps update their offline copy the next time they are
            online, so the new text shows on the result screen and in Full Details without an app update. A new install
            that has never been online shows the guides bundled with the app until it connects.
          </p>
          <p>
            Changing a guide's text resets its review to <strong>Unreviewed</strong> unless you re-mark it, so the
            app's review label never vouches for text nobody has checked.
          </p>
        </HelpText>
        {error && <div className="guide-alert"><Alert variant="error" onDismiss={() => setError('')}>{error}</Alert></div>}
        <div className="guide-grid">
          {GUIDE_CODES.map((code) => (
            <GuideCard key={code} code={code} guide={byCode.get(code)} loading={guides === undefined} onDelete={() => setToDelete(code)} />
          ))}
        </div>
      </PageSection>

      <ConfirmDialog
        isOpen={!!toDelete}
        onClose={() => setToDelete(null)}
        onConfirm={runDelete}
        title={`Delete the ${toDelete ? diseaseLabel(toDelete) : ''} guide?`}
        message={"Farmers' apps remove it on their next sync and show “No reference content is available” for this disease until you create a new guide. This can't be undone."}
        confirmText="Delete guide"
        variant="danger"
      />
    </div>
  );
}

function GuideCard({ code, guide, loading, onDelete }: { code: string; guide?: Guide; loading: boolean; onDelete: () => void }) {
  if (loading) {
    return (
      <div className="guide-card">
        <span className="skeleton" style={{ width: '50%', height: 16 }} />
        <span className="skeleton" style={{ width: '80%' }} />
        <span className="skeleton" style={{ width: '65%' }} />
      </div>
    );
  }
  if (!guide) {
    return (
      <div className="guide-card guide-card-missing">
        <div className="guide-card-head">
          <h3 className="guide-card-title">{diseaseLabel(code)}</h3>
          <span className="badge badge-orange">No guide</span>
        </div>
        <p className="guide-card-text">
          Farmers who get this result see no treatment guidance.
        </p>
        <div className="guide-card-actions">
          <Link to={`/guides/${code}`} className="btn btn-primary btn-sm"><Icon name="plus" size={15} /> Create guide</Link>
        </div>
      </div>
    );
  }
  return (
    <div className="guide-card">
      <div className="guide-card-head">
        <h3 className="guide-card-title">{guide.displayName}</h3>
        <span className={`badge ${REVIEW_STATUS_BADGE[guide.reviewStatus]}`}
          title={guide.reviewedBy ? `Reviewed by ${guide.reviewedBy}` : undefined}>
          {REVIEW_STATUS_LABEL[guide.reviewStatus]}
        </span>
      </div>
      <p className="guide-card-text clamp-3">{guide.treatmentSteps}</p>
      <dl className="guide-card-meta">
        <div><dt>Updated</dt><dd>{date(guide.updatedAt)}{guide.updatedByName ? ` · ${guide.updatedByName}` : ''}</dd></div>
        <div><dt>Version</dt><dd><code className="code-chip">{guide.contentVersion}</code></dd></div>
        {guide.reviewStatus === 'expert_verified' && <div><dt>Expert</dt><dd>{guide.reviewedBy}</dd></div>}
      </dl>
      <div className="guide-card-actions">
        <Link to={`/guides/${code}`} className="btn btn-secondary btn-sm"><Icon name="edit" size={15} /> Edit</Link>
        <button type="button" className="btn btn-ghost btn-sm guide-delete" onClick={onDelete}>
          <Icon name="trash" size={15} /> Delete
        </button>
      </div>
    </div>
  );
}

import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from 'convex/react';
import { api } from '../convex';
import { Icon } from '../components/Icon';
import { Alert, Button, PageLoader } from '../components/ui';
import { GUIDE_CODES, REVIEW_STATUS_LABEL, diseaseLabel, type GuideReviewStatus } from '../labels';
import type { Guide } from './TreatmentGuidesPage';

type Content = Pick<Guide, 'displayName' | 'treatmentSteps' | 'causes' | 'duration' | 'preventionSteps' | 'symptoms' | 'sourceReference'>;
type Review = { status: GuideReviewStatus; reviewedBy: string };

/** Same order and titles as the app's Full Details sheet (strings.xml treatment_*_label). */
const SECTIONS: { key: keyof Content; title: string; hint: string; rows: number; required?: boolean }[] = [
  { key: 'treatmentSteps', title: 'Treatment', required: true, rows: 10,
    hint: 'What to spray or do, with rates from the product label. Also shown as the preview on the result screen.' },
  { key: 'causes', title: 'Causes', rows: 5, hint: 'What causes the disease and what conditions help it spread.' },
  { key: 'duration', title: 'Duration', rows: 4, hint: 'How long treatment takes and what recovery to expect.' },
  { key: 'preventionSteps', title: 'Prevention', rows: 6, hint: 'How to keep it from coming back.' },
  { key: 'symptoms', title: 'Symptoms', rows: 3, hint: 'What it looks like on the leaf.' },
];
const MAX_CHARS = 20_000; // backend diseaseReferences MAX_FIELD_CHARS
const EMPTY: Content = { displayName: '', treatmentSteps: '', causes: '', duration: '', preventionSteps: '', symptoms: '', sourceReference: '' };
const CONTENT_KEYS = Object.keys(EMPTY) as (keyof Content)[];
const norm = (s: string) => s.replace(/\r\n?/g, '\n').trim();

export default function TreatmentGuideEditorPage() {
  const { code = '' } = useParams();
  const guides = useQuery(api.diseaseReferences.adminList, {}) as Guide[] | undefined;
  if (!(GUIDE_CODES as readonly string[]).includes(code)) return <Navigate to="/guides" replace />;
  if (guides === undefined) return <PageLoader label="Loading guide…" />;
  // Keyed so switching guides starts a fresh form.
  return <GuideForm key={code} code={code} initial={guides.find((g) => g.diseaseCode === code) ?? null} live={guides} />;
}

function GuideForm({ code, initial, live }: { code: string; initial: Guide | null; live: Guide[] }) {
  const navigate = useNavigate();
  const create = useMutation(api.diseaseReferences.create);
  const update = useMutation(api.diseaseReferences.update);
  const isNew = initial === null;

  const original = useRef<{ content: Content; review: Review }>({
    content: initial ? pick(initial) : { ...EMPTY, displayName: diseaseLabel(code) },
    review: initial ? { status: initial.reviewStatus, reviewedBy: initial.reviewedBy } : { status: 'unreviewed', reviewedBy: '' },
  });
  const [content, setContent] = useState<Content>(original.current.content);
  // null = not touched: the server keeps the review if the text is unchanged, resets it if not.
  const [reviewChoice, setReviewChoice] = useState<Review | null>(isNew ? original.current.review : null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [showErrors, setShowErrors] = useState(false);

  const contentChanged = CONTENT_KEYS.some((k) => norm(content[k]) !== norm(original.current.content[k]));
  const effectiveReview: Review = reviewChoice ?? (contentChanged ? { status: 'unreviewed', reviewedBy: '' } : original.current.review);
  const autoReset = !isNew && reviewChoice === null && contentChanged && original.current.review.status !== 'unreviewed';
  const dirty = contentChanged || (reviewChoice !== null && !isNew &&
    (reviewChoice.status !== original.current.review.status || reviewChoice.reviewedBy.trim() !== original.current.review.reviewedBy));

  // Someone else saved this guide (or deleted it) while it was open here.
  const current = live.find((g) => g.diseaseCode === code) ?? null;
  const changedElsewhere = !saving && (initial ? current?.contentVersion !== initial.contentVersion || !current : !!current);

  const errors = useMemo(() => {
    const e: Partial<Record<keyof Content | 'reviewedBy', string>> = {};
    if (!norm(content.displayName)) e.displayName = 'Give the guide a name.';
    if (!norm(content.treatmentSteps)) e.treatmentSteps = 'Treatment is required: it is what farmers see first.';
    for (const k of CONTENT_KEYS) if (content[k].length > MAX_CHARS) e[k] = `Too long (max ${MAX_CHARS.toLocaleString()} characters).`;
    if (effectiveReview.status === 'expert_verified' && !effectiveReview.reviewedBy.trim()) e.reviewedBy = "Enter the expert's name.";
    return e;
  }, [content, effectiveReview]);
  const hasErrors = Object.keys(errors).length > 0;

  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const set = (key: keyof Content) => (e: { target: { value: string } }) => setContent((c) => ({ ...c, [key]: e.target.value }));

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setShowErrors(true);
    if (hasErrors) return;
    setSaving(true);
    setError('');
    try {
      if (isNew) await create({ diseaseCode: code, content, review: effectiveReview });
      else await update({ diseaseCode: code, content, ...(reviewChoice ? { review: reviewChoice } : {}) });
      navigate('/guides');
    } catch (err: any) {
      setError(String(err?.data ?? err?.message ?? 'Save failed'));
      setSaving(false);
    }
  };

  const cancel = () => {
    if (dirty && !window.confirm('Discard your changes to this guide?')) return;
    navigate('/guides');
  };

  const fieldError = (k: keyof Content | 'reviewedBy') => (showErrors ? errors[k] : undefined);

  return (
    <form className="guide-editor" onSubmit={save} noValidate>
      <div className="guide-editor-bar">
        <Link to="/guides" className="link-arrow" onClick={(e) => { if (dirty && !window.confirm('Discard your changes to this guide?')) e.preventDefault(); }}>
          <Icon name="arrowLeft" size={14} /> All guides
        </Link>
        <div className="guide-editor-heading">
          <h2>{isNew ? `New guide: ${diseaseLabel(code)}` : diseaseLabel(code)}</h2>
          {!isNew && initial && <span className="cell-sub">Version <code className="code-chip">{initial.contentVersion}</code></span>}
        </div>
        <div className="action-group action-group-end">
          <Button variant="secondary" onClick={cancel} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="primary" busy={saving} disabled={!isNew && !dirty}>
            {isNew ? 'Publish guide' : 'Save changes'}
          </Button>
        </div>
      </div>

      {changedElsewhere && (
        <Alert variant="warning" title={current ? 'Someone else changed this guide' : initial ? 'This guide was deleted' : 'This guide was just created'}>
          {current
            ? 'Saving will replace their version with yours. Reload the page to see their changes first.'
            : 'Saving will create it again with your text.'}
        </Alert>
      )}
      {error && <Alert variant="error" onDismiss={() => setError('')}>{error}</Alert>}
      {showErrors && hasErrors && <Alert variant="error">Fix the highlighted fields before saving.</Alert>}

      <div className="guide-editor-layout">
        <div className="guide-editor-fields">
          <section className="card section">
            <div className="card-body form-stack">
              <Field id="displayName" label="Name shown to farmers" required error={fieldError('displayName')}>
                <input id="displayName" className="form-input" value={content.displayName} onChange={set('displayName')} maxLength={200} />
              </Field>
              {SECTIONS.map((s) => (
                <Field key={s.key} id={s.key} label={s.title} required={s.required} hint={s.hint} error={fieldError(s.key)}
                  count={content[s.key].length}>
                  <textarea id={s.key} className="form-textarea" value={content[s.key]} onChange={set(s.key)}
                    rows={Math.min(24, Math.max(s.rows, content[s.key].split('\n').length + 1))} spellCheck />
                </Field>
              ))}
              <p className="field-hint">
                Plain text: start a line with "•" or "1." for lists, end a line with ":" for a small heading, and leave a blank
                line between parts. The preview shows it the way the app does.
              </p>
            </div>
          </section>

          <section className="card section">
            <header className="card-header"><div className="card-heading"><h2 className="card-title">Source and review</h2>
              <p className="card-description">Shown under the guide in the app. A review label is only shown with a source.</p></div></header>
            <div className="card-body form-stack">
              <Field id="sourceReference" label="Source" hint="Where the advice comes from (e.g. CPN 2026, FPA registry), so it can be traced."
                error={fieldError('sourceReference')}>
                <textarea id="sourceReference" className="form-textarea" rows={2} value={content.sourceReference} onChange={set('sourceReference')} />
              </Field>
              <div className="form-row">
                <Field id="reviewStatus" label="Review">
                  <select id="reviewStatus" className="form-select" value={effectiveReview.status}
                    onChange={(e) => setReviewChoice({ status: e.target.value as GuideReviewStatus, reviewedBy: effectiveReview.reviewedBy })}>
                    {(Object.keys(REVIEW_STATUS_LABEL) as GuideReviewStatus[]).map((s) => <option key={s} value={s}>{REVIEW_STATUS_LABEL[s]}</option>)}
                  </select>
                </Field>
                {effectiveReview.status !== 'unreviewed' && (
                  <Field id="reviewedBy" label={effectiveReview.status === 'expert_verified' ? "Expert's name and role" : 'Reviewed by'}
                    required={effectiveReview.status === 'expert_verified'} error={fieldError('reviewedBy')}>
                    <input id="reviewedBy" className="form-input" value={effectiveReview.reviewedBy}
                      placeholder={effectiveReview.status === 'expert_verified' ? 'e.g. Juan Dela Cruz, DA agronomist' : 'Team or person'}
                      onChange={(e) => setReviewChoice({ status: effectiveReview.status, reviewedBy: e.target.value })} />
                  </Field>
                )}
              </div>
              {autoReset && (
                <Alert variant="info">
                  You changed the text, so the review resets to <strong>Unreviewed</strong> on save. If this version has been
                  checked, choose its review again above.
                </Alert>
              )}
            </div>
          </section>
        </div>

        <aside className="guide-preview" aria-label="Preview in the app">
          <div className="guide-preview-label"><Icon name="phone" size={15} /> Preview: Full Details in the app</div>
          <PhonePreview content={content} review={effectiveReview}
            reviewedAt={reviewChoice === null && !contentChanged && initial?.reviewedAt ? initial.reviewedAt : Date.now()} />
        </aside>
      </div>
    </form>
  );
}

function Field({ id, label, hint, error, required, count, children }: {
  id: string; label: string; hint?: string; error?: string; required?: boolean; count?: number; children: ReactNode;
}) {
  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      <div className="field-label-row">
        <label htmlFor={id} className="form-label">{label}{required && <span className="field-required" aria-hidden="true"> *</span>}</label>
        {count !== undefined && count > MAX_CHARS * 0.8 && <span className="field-count">{count.toLocaleString()} / {MAX_CHARS.toLocaleString()}</span>}
      </div>
      {children}
      {error ? <p className="field-error" role="alert">{error}</p> : hint && <p className="field-hint">{hint}</p>}
    </div>
  );
}

/** Mirrors the app's FullDetailsBottomSheet: section order, review label, source and disclaimer. */
function PhonePreview({ content, review, reviewedAt }: { content: Content; review: Review; reviewedAt: number }) {
  const today = new Date(reviewedAt).toLocaleDateString();
  // ReviewStatus.reviewLabelFor: no label without a source; expert tier needs a name.
  const label = review.status === 'unreviewed' || !norm(content.sourceReference) ? null
    : review.status === 'expert_verified' && review.reviewedBy.trim()
      ? `Verified by an agricultural expert · ${review.reviewedBy.trim()}, ${today}`
      : `Reviewed against published sources · ${today}`;
  const expert = review.status === 'expert_verified' && !!review.reviewedBy.trim() && !!label;
  const sections = SECTIONS.filter((s) => norm(content[s.key]));
  return (
    <div className="phone">
      <div className="phone-sheet">
        <div className="phone-handle" />
        <h3 className="phone-title">{norm(content.displayName) || 'Untitled guide'}</h3>
        {sections.length === 0 && <p className="phone-empty">No reference content is available for this disease on this device yet.</p>}
        {sections.map((s) => (
          <div key={s.key} className="phone-section">
            <h4>{s.title}</h4>
            <p>{norm(content[s.key])}</p>
            {s.key === 'treatmentSteps' && label && <p className={`phone-review${expert ? ' expert' : ''}`}>{label}</p>}
          </div>
        ))}
        {norm(content.sourceReference) && <p className="phone-source">Source: {norm(content.sourceReference)}</p>}
        {!expert && (
          <p className="phone-disclaimer">
            This guidance was checked against published research and Philippine pesticide registrations, but has not yet
            been reviewed by an agricultural expert. Confirm with your local DA office or agricultural technician before
            applying any treatment, and always follow the product label.
          </p>
        )}
      </div>
    </div>
  );
}

const pick = (g: Guide): Content => ({
  displayName: g.displayName, treatmentSteps: g.treatmentSteps, causes: g.causes, duration: g.duration,
  preventionSteps: g.preventionSteps, symptoms: g.symptoms, sourceReference: g.sourceReference,
});

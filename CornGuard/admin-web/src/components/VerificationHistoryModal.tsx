import { useQuery } from 'convex/react';
import { api } from '../convex';
import { date, statusBadge } from '../labels';
import { Modal } from './Modal';
import { Note, Spinner } from './ui';

type HistoryEvent = {
  eventId: string; at: number; fromStatus: string | null; toStatus: string;
  reason: 'shared' | 'posted' | 'auto_verified' | 'admin_review'; actorName: string | null;
};

const REASON_LABEL: Record<HistoryEvent['reason'], string> = {
  shared: 'Shared by the farmer (waits for review)',
  posted: 'Posted by the farmer (waits for review)',
  auto_verified: 'Verified automatically (≥ 60 % confidence, photo, recent)',
  admin_review: 'Reviewed by an admin',
};

/**
 * The append-only verification trail of one scan or post (backend admin.verificationHistory).
 * Read-only: nothing here can change or remove an entry.
 */
export function VerificationHistoryModal({ target, onClose }: {
  target: { targetType: 'diagnosisRecord' | 'communityPost'; targetId: string; title: string } | null;
  onClose: () => void;
}) {
  const events = useQuery(
    api.admin.verificationHistory,
    target ? { targetType: target.targetType, targetId: target.targetId } : 'skip',
  ) as HistoryEvent[] | undefined;

  return (
    <Modal isOpen={!!target} onClose={onClose} title={target ? `Verification history · ${target.title}` : ''}>
      {events === undefined ? (
        <Spinner label="Loading history" />
      ) : events.length === 0 ? (
        <Note>No recorded changes. This item was shared before the history was kept; its current status is shown in the list.</Note>
      ) : (
        <ol className="history-list">
          {events.map((e) => (
            <li key={e.eventId} className="history-item">
              <span className="history-when">{date(e.at)}</span>
              <span className="history-change">
                {e.fromStatus && <><span className={`badge ${statusBadge(e.fromStatus)}`}>{e.fromStatus.toUpperCase()}</span> → </>}
                <span className={`badge ${statusBadge(e.toStatus)}`}>{e.toStatus.toUpperCase()}</span>
              </span>
              <span className="history-who">
                {REASON_LABEL[e.reason]}{e.actorName ? ` · ${e.actorName}` : ''}
              </span>
            </li>
          ))}
        </ol>
      )}
    </Modal>
  );
}

import { useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useDialog } from './useDialog';
import { Icon } from './Icon';
import { Button } from './ui';

interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  /** May return a promise: the confirm button then shows a spinner and the dialog closes once it settles. */
  onConfirm: () => void | Promise<unknown>;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'primary';
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger',
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const [busy, setBusy] = useState(false);
  const titleId = useId();
  const messageId = useId();
  const busyRef = useRef(false);
  busyRef.current = busy;

  // Cancel gets initial focus, so Enter never confirms a destructive action by accident.
  useDialog(isOpen, onClose, ref, { initialFocusRef: cancelRef, canClose: () => !busyRef.current });

  if (!isOpen) return null;

  const confirm = async () => {
    setBusy(true);
    try {
      await onConfirm();
    } finally {
      setBusy(false);
      onClose();
    }
  };

  return createPortal(
    <div className="overlay" onClick={() => { if (!busy) onClose(); }}>
      <div
        ref={ref}
        className="modal modal-sm dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={messageId}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="dialog-main">
          <span className={`dialog-icon dialog-icon-${variant}`} aria-hidden="true">
            <Icon name={variant === 'danger' ? 'warning' : 'info'} size={22} />
          </span>
          <div>
            <h2 id={titleId} className="dialog-title">{title}</h2>
            <p id={messageId} className="dialog-message">{message}</p>
          </div>
        </div>
        <div className="modal-footer">
          <button ref={cancelRef} type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>{cancelText}</button>
          <Button variant={variant === 'danger' ? 'danger' : 'primary'} busy={busy} onClick={confirm}>{confirmText}</Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

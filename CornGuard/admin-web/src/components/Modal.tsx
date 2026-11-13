import { useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useDialog } from './useDialog';
import { Icon } from './Icon';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'md' | 'lg';
}

/** Generic modal: centred card on desktop, full-height sheet on phones. */
export function Modal({ isOpen, onClose, title, children, footer, size = 'md' }: ModalProps) {
  const ref = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useDialog(isOpen, onClose, ref);
  if (!isOpen) return null;

  return createPortal(
    <div className="overlay" onClick={onClose}>
      <div
        ref={ref}
        className={`modal modal-${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="modal-header">
          <h2 className="modal-title" id={titleId}>{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close">
            <Icon name="close" size={18} />
          </button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-footer">{footer}</footer>}
      </div>
    </div>,
    document.body,
  );
}

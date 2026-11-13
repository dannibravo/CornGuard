import { useEffect, useRef, type RefObject } from 'react';

/**
 * Body scroll lock shared by every overlay (dialogs, lightbox, mobile nav drawer). Counted, so a
 * dialog opened over the drawer doesn't unlock the page when it closes.
 */
let lockCount = 0;
export function lockBodyScroll(): () => void {
  if (lockCount++ === 0) document.body.style.overflow = 'hidden';
  let released = false;
  return () => {
    if (released) return;
    released = true;
    if (--lockCount === 0) document.body.style.overflow = '';
  };
}

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Modal behaviour for an overlay: Escape closes, Tab is trapped inside `containerRef`, the page
 * behind can't scroll, and focus returns to whatever was focused before it opened.
 *
 * `onClose` is read through a ref, so callers can pass an inline arrow without the effect
 * re-running (and stealing focus back to the first button) on every parent render.
 */
export function useDialog(
  isOpen: boolean,
  onClose: () => void,
  containerRef: RefObject<HTMLElement>,
  options: { initialFocusRef?: RefObject<HTMLElement>; canClose?: () => boolean } = {},
) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const canCloseRef = useRef(options.canClose);
  canCloseRef.current = options.canClose;
  const initialFocusRef = options.initialFocusRef;

  useEffect(() => {
    if (!isOpen) return;
    const previous = document.activeElement as HTMLElement | null;
    const release = lockBodyScroll();

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (canCloseRef.current && !canCloseRef.current()) return;
        e.stopPropagation();
        onCloseRef.current();
        return;
      }
      if (e.key !== 'Tab') return;
      const focusable = containerRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE);
      if (!focusable || focusable.length === 0) { e.preventDefault(); return; }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);

    const target = initialFocusRef?.current ?? containerRef.current?.querySelector<HTMLElement>(FOCUSABLE) ?? containerRef.current;
    target?.focus();

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      release();
      previous?.focus?.();
    };
  }, [isOpen, containerRef, initialFocusRef]);
}

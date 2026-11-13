import { useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useDialog } from './useDialog';
import { Icon } from './Icon';

interface ImageThumbProps {
  src: string | null;
  alt?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  /** When set, the thumbnail becomes a button that opens the full-size image. */
  onClick?: () => void;
}

const SIZE_CLASS = { sm: 'img-thumb-sm', md: 'img-thumb-md', lg: 'img-thumb-lg' };

export function ImageThumb({ src, alt = 'Image', size = 'md', className = '', onClick }: ImageThumbProps) {
  const [showModal, setShowModal] = useState(false);
  const [imgError, setImgError] = useState(false);

  if (!src || imgError) {
    return (
      <span className={`img-thumb img-placeholder ${SIZE_CLASS[size]} ${className}`} role="img" aria-label={imgError ? `${alt} (failed to load)` : `${alt} (no photo)`}>
        <Icon name={imgError ? 'imageOff' : 'image'} size={size === 'sm' ? 16 : 20} />
      </span>
    );
  }

  const img = (
    <img
      className={`img-thumb ${SIZE_CLASS[size]} ${className}`}
      src={src}
      alt={alt}
      onError={() => setImgError(true)}
      loading="lazy"
    />
  );

  if (!onClick) return img;

  return (
    <>
      <button type="button" className="thumb-btn" onClick={() => { setShowModal(true); onClick(); }} aria-label={`View ${alt} full size`}>
        {img}
      </button>
      {showModal && <Lightbox src={src} alt={alt} onClose={() => setShowModal(false)} />}
    </>
  );
}

function Lightbox({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useDialog(true, onClose, ref);
  return createPortal(
    <div ref={ref} className="lightbox" onClick={onClose} role="dialog" aria-modal="true" aria-label={`${alt} (full size)`}>
      <button type="button" className="lightbox-close" onClick={onClose} aria-label="Close">
        <Icon name="close" size={22} />
      </button>
      <img src={src} alt={alt} onClick={(e) => e.stopPropagation()} />
    </div>,
    document.body,
  );
}

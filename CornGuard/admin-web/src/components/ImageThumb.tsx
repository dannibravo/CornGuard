import { useState } from 'react';

interface ImageThumbProps {
  src: string | null;
  alt?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  onClick?: () => void;
}

export function ImageThumb({ src, alt = 'Image', size = 'md', className = '', onClick }: ImageThumbProps) {
  const [showModal, setShowModal] = useState(false);
  const [imgError, setImgError] = useState(false);

  const sizeClasses = {
    sm: 'img-thumb-sm',
    md: 'img-thumb',
    lg: 'img-thumb-lg',
  };

  if (!src) {
    return (
      <div className={`img-placeholder ${sizeClasses[size]} ${className}`} aria-label={alt}>
        <span aria-hidden="true">🖼️</span>
      </div>
    );
  }

  if (imgError) {
    return (
      <div className={`img-placeholder ${sizeClasses[size]} ${className}`} aria-label={alt}>
        <span aria-hidden="true">❌</span>
      </div>
    );
  }

  return (
    <>
      <img
        className={`img-thumb ${sizeClasses[size]} ${className}`}
        src={src}
        alt={alt}
        onError={() => setImgError(true)}
        onClick={onClick ? () => setShowModal(true) : undefined}
        style={{ cursor: onClick ? 'zoom-in' : 'default' }}
        loading="lazy"
      />
      {onClick && showModal && (
        <div className="image-modal-overlay" onClick={() => setShowModal(false)} role="dialog" aria-modal="true" aria-label={`${alt} (full size)`}>
          <img src={src} alt={alt} onClick={(e) => e.stopPropagation()} />
        </div>
      )}
    </>
  );
}
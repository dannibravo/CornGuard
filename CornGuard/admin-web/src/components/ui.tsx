import { useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/** Small presentational primitives, so pages don't hand-roll inline styles for the same things. */

export function Spinner({ size = 'md', label }: { size?: 'sm' | 'md' | 'lg'; label?: string }) {
  return <span className={`spinner spinner-${size}`} role={label ? 'status' : undefined} aria-label={label} />;
}

/** Full-area loading state for a page or route. */
export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="page-loader" role="status" aria-live="polite">
      <Spinner size="lg" />
      <span>{label}</span>
    </div>
  );
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md';
  busy?: boolean;
  icon?: IconName;
  block?: boolean;
};

/** Button with a built-in pending state: shows a spinner and blocks clicks while `busy`. */
export function Button({ variant = 'secondary', size = 'md', busy = false, icon, block, className = '', children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      className={`btn btn-${variant}${size === 'sm' ? ' btn-sm' : ''}${block ? ' btn-full' : ''}${busy ? ' is-busy' : ''} ${className}`}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
    >
      {busy ? <Spinner size="sm" /> : icon ? <Icon name={icon} size={size === 'sm' ? 15 : 17} /> : null}
      {children}
    </button>
  );
}

export function ActionGroup({ children, align = 'start' }: { children: ReactNode; align?: 'start' | 'end' }) {
  return <div className={`action-group${align === 'end' ? ' action-group-end' : ''}`}>{children}</div>;
}

const ALERT_ICON: Record<'error' | 'warning' | 'info' | 'success', IconName> = {
  error: 'warning', warning: 'warning', info: 'info', success: 'checkCircle',
};

export function Alert({ variant = 'info', title, children, onDismiss }: {
  variant?: 'error' | 'warning' | 'info' | 'success'; title?: ReactNode; children?: ReactNode; onDismiss?: () => void;
}) {
  return (
    <div className={`alert alert-${variant}`} role={variant === 'error' ? 'alert' : 'status'}>
      <Icon name={ALERT_ICON[variant]} size={18} className="alert-icon" />
      <div className="alert-content">
        {title && <div className="alert-title">{title}</div>}
        {children && <div className="alert-body">{children}</div>}
      </div>
      {onDismiss && (
        <button type="button" className="icon-btn icon-btn-sm alert-dismiss" onClick={onDismiss} aria-label="Dismiss">
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  );
}

/** A titled card. `flush` drops the body padding (for tables that run edge to edge). */
export function PageSection({ title, description, actions, children, tone, flush, className = '', id }: {
  title?: ReactNode; description?: ReactNode; actions?: ReactNode; children?: ReactNode;
  tone?: 'warning' | 'danger'; flush?: boolean; className?: string; id?: string;
}) {
  return (
    <section className={`card section${tone ? ` card-${tone}` : ''} ${className}`} aria-labelledby={title && id ? `${id}-title` : undefined} id={id}>
      {(title || actions) && (
        <header className="card-header">
          <div className="card-heading">
            {title && <h2 className="card-title" id={id ? `${id}-title` : undefined}>{title}</h2>}
            {description && <p className="card-description">{description}</p>}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      <div className={`card-body${flush ? ' card-body-flush' : ''}`}>{children}</div>
    </section>
  );
}

/** Muted explanatory paragraph. */
export function Note({ children }: { children: ReactNode }) {
  return <p className="note">{children}</p>;
}

/** Collapsible "how this works" explainer. Long help text stays one click away instead of pushing content down. */
export function HelpText({ summary = 'How this works', children, defaultOpen = false }: { summary?: ReactNode; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <details className="help" open={open} onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary>
        <Icon name="help" size={16} />
        <span>{summary}</span>
        <Icon name="chevronRight" size={16} className="help-chevron" />
      </summary>
      <div className="help-body">{children}</div>
    </details>
  );
}

/** Two-letter initials avatar. */
export function Avatar({ name, size = 'md' }: { name: string | null | undefined; size?: 'sm' | 'md' }) {
  const initials = (name ?? '?').split(/[\s@._-]+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('') || '?';
  return <span className={`avatar avatar-${size}`} aria-hidden="true">{initials}</span>;
}

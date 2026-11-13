import { useRef, type KeyboardEvent } from 'react';

export interface Tab {
  id: string;
  label: string;
  count?: number;
}

interface TabsProps {
  value: string;
  onChange: (value: string) => void;
  tabs: Tab[];
  className?: string;
  /** 'pill' = compact secondary filter row; default = primary segmented tabs. */
  variant?: 'default' | 'pill';
  label?: string;
}

/** Segmented tabs. Scrolls sideways instead of overflowing on narrow screens; ←/→/Home/End move between tabs. */
export function Tabs({ value, onChange, tabs, className = '', variant = 'default', label = 'Content sections' }: TabsProps) {
  const listRef = useRef<HTMLDivElement>(null);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const i = tabs.findIndex((t) => t.id === value);
    let next = -1;
    if (e.key === 'ArrowRight') next = (i + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (i - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next < 0) return;
    e.preventDefault();
    onChange(tabs[next].id);
    listRef.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
  };

  return (
    <div
      ref={listRef}
      className={`tabs${variant === 'pill' ? ' tabs-pill' : ''} ${className}`}
      role="tablist"
      aria-label={label}
      onKeyDown={onKeyDown}
    >
      {tabs.map((tab) => {
        const selected = value === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            id={`tab-${tab.id}`}
            className={`tab${selected ? ' active' : ''}`}
            onClick={() => onChange(tab.id)}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span className="tab-count" aria-label={`${tab.count} items`}>{tab.count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}

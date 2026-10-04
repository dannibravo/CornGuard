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
}

export function Tabs({ value, onChange, tabs, className = '' }: TabsProps) {
  return (
    <div className={`tabs ${className}`} role="tablist" aria-label="Content sections">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          role="tab"
          aria-selected={value === tab.id}
          aria-controls={`panel-${tab.id}`}
          id={`tab-${tab.id}`}
          className={`tab ${value === tab.id ? 'active' : ''}`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
          {tab.count !== undefined && (
            <span className="tab-count" aria-label={`${tab.count} items`}>{tab.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
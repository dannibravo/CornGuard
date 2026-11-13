import { useState, useMemo, type ReactNode } from 'react';
import { Icon } from './Icon';

export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  sortable?: boolean;
  width?: string;
  minWidth?: string;
  align?: 'left' | 'right' | 'center';
  /** Hide this (secondary) column on narrow screens: 'md' below 768px, 'lg' below 1024px, 'xl' below 1280px. */
  hideBelow?: 'md' | 'lg' | 'xl';
  nowrap?: boolean;
}

interface DataTableProps<T> {
  data: T[];
  columns: Column<T>[];
  keyField: keyof T | ((row: T) => string);
  loading?: boolean;
  emptyMessage?: string;
  emptyTitle?: string;
  onRowClick?: (row: T) => void;
  rowClassName?: (row: T) => string | undefined;
  className?: string;
  /** Adds a search box that filters the loaded rows on these fields (client-side only). */
  searchKeys?: (keyof T)[];
  searchPlaceholder?: string;
  /** Extra controls shown in the toolbar beside the search box (e.g. filter chips). */
  toolbar?: ReactNode;
}

const cellClass = <T,>(col: Column<T>) =>
  [col.align && col.align !== 'left' ? `align-${col.align}` : '', col.hideBelow ? `hide-${col.hideBelow}` : '', col.nowrap ? 'nowrap' : '']
    .filter(Boolean).join(' ') || undefined;

export function DataTable<T extends Record<string, any>>({
  data,
  columns,
  keyField,
  loading = false,
  emptyMessage = 'No data available',
  emptyTitle = 'Nothing here',
  onRowClick,
  rowClassName,
  className = '',
  searchKeys,
  searchPlaceholder = 'Search…',
  toolbar,
}: DataTableProps<T>) {
  const [sortConfig, setSortConfig] = useState<{ key: string; direction: 'asc' | 'desc' } | null>(null);
  const [query, setQuery] = useState('');

  const getRowKey = (row: T) => typeof keyField === 'function' ? keyField(row) : String(row[keyField]);

  const filteredData = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q || !searchKeys?.length) return data;
    return data.filter((row) => searchKeys.some((k) => String(row[k] ?? '').toLowerCase().includes(q)));
  }, [data, query, searchKeys]);

  const sortedData = useMemo(() => {
    if (!sortConfig) return filteredData;
    return [...filteredData].sort((a, b) => {
      const aVal = a[sortConfig.key];
      const bVal = b[sortConfig.key];
      if (aVal < bVal) return sortConfig.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
  }, [filteredData, sortConfig]);

  const handleSort = (key: string) => {
    setSortConfig(prev => {
      if (prev?.key === key && prev.direction === 'asc') return { key, direction: 'desc' };
      return { key, direction: 'asc' };
    });
  };

  const hasToolbar = !!searchKeys?.length || !!toolbar;
  const toolbarEl = hasToolbar && (
    <div className="table-toolbar">
      {!!searchKeys?.length && (
        <label className="search-field">
          <Icon name="search" size={16} />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
          />
        </label>
      )}
      {toolbar && <div className="table-toolbar-extra">{toolbar}</div>}
    </div>
  );

  // An empty emptyMessage means the caller only renders the table when it has rows.
  if (!loading && data.length === 0 && emptyMessage === '') return null;

  let body: ReactNode;
  if (loading) {
    body = (
      <tbody aria-busy="true">
        {Array.from({ length: 5 }, (_, i) => (
          <tr key={i} className="skeleton-row">
            {columns.map((col) => <td key={col.key} className={cellClass(col)}><span className="skeleton" /></td>)}
          </tr>
        ))}
      </tbody>
    );
  } else if (sortedData.length === 0) {
    const filtered = data.length > 0;
    body = (
      <tbody>
        <tr className="empty-row">
          <td colSpan={columns.length}>
            <div className="empty-state">
              <span className="empty-icon" aria-hidden="true"><Icon name={filtered ? 'search' : 'inbox'} size={28} /></span>
              <div className="empty-title">{filtered ? 'No matches' : emptyTitle}</div>
              <div className="empty-desc">{filtered ? `Nothing matches “${query.trim()}”.` : emptyMessage}</div>
            </div>
          </td>
        </tr>
      </tbody>
    );
  } else {
    body = (
      <tbody>
        {sortedData.map((row) => (
          <tr
            key={getRowKey(row)}
            className={[onRowClick ? 'clickable' : '', rowClassName?.(row) ?? ''].filter(Boolean).join(' ') || undefined}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            tabIndex={onRowClick ? 0 : undefined}
            onKeyDown={onRowClick ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onRowClick(row); } } : undefined}
          >
            {columns.map((col) => (
              <td key={col.key} className={cellClass(col)}>
                {col.render ? col.render(row) : String(row[col.key] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    );
  }

  return (
    <div className={`data-table-container ${className}`}>
      {toolbarEl}
      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((col) => {
                const sorted = sortConfig?.key === col.key ? sortConfig.direction : null;
                return (
                  <th
                    key={col.key}
                    style={{ width: col.width, minWidth: col.minWidth }}
                    className={cellClass(col)}
                    scope="col"
                    aria-sort={sorted ? (sorted === 'asc' ? 'ascending' : 'descending') : undefined}
                  >
                    {col.sortable ? (
                      <button type="button" className={`th-sort${sorted ? ' is-sorted' : ''}`} onClick={() => handleSort(col.key)}>
                        <span>{col.header}</span>
                        <Icon name={sorted === 'asc' ? 'sortUp' : sorted === 'desc' ? 'sortDown' : 'sort'} size={14} className="sort-icon" />
                      </button>
                    ) : (
                      col.header && <span className="th-label">{col.header}</span>
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          {body}
        </table>
      </div>
    </div>
  );
}

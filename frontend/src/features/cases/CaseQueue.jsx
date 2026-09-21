import { useState } from 'react';
import { useGetCasesQuery, useGetQueueSummaryQuery } from '../../api/apiSlice';

const STATUSES = ['', 'OPEN', 'IN_REVIEW', 'CLOSED'];

export default function CaseQueue({ onSelectCase }) {
  const [status, setStatus] = useState('');
  const [cursor, setCursor] = useState(null);
  const summaryQuery = useGetQueueSummaryQuery();
  const casesQuery = useGetCasesQuery({ status: status || undefined, cursor: cursor || undefined, limit: 25 });

  const summary = summaryQuery.data;
  const items = casesQuery.data?.items ?? [];

  function changeStatus(next) {
    setStatus(next);
    setCursor(null);
  }

  return (
    <section className="card">
      <h2>Analyst queue</h2>

      {summary && (
        <div className="kv-row">
          <span className="pill">{summary.openCount} open</span>
          <span className="pill">{summary.inReviewCount} in review</span>
          <span className="pill">{summary.closedCount} closed</span>
        </div>
      )}

      <div className="filters">
        {STATUSES.map((s) => (
          <button
            key={s || 'ALL'}
            className={s === status ? '' : 'secondary'}
            onClick={() => changeStatus(s)}
          >
            {s || 'ALL'}
          </button>
        ))}
      </div>

      {casesQuery.isLoading && <p>Loading…</p>}
      {casesQuery.error && <p className="bad">Could not load the queue ({casesQuery.error.status ?? 'network'}).</p>}

      {items.length > 0 && (
        <table className="queue-table">
          <thead>
            <tr>
              <th>Priority</th>
              <th>Action</th>
              <th>Fraud p</th>
              <th>Status</th>
              <th>Ring</th>
              <th>Opened</th>
            </tr>
          </thead>
          <tbody>
            {items.map((c) => (
              <tr key={c.caseId} onClick={() => onSelectCase(c.caseId)}>
                <td>{c.priority}</td>
                <td>{c.action}</td>
                <td>{c.fraudProbability != null ? c.fraudProbability.toFixed(4) : '—'}</td>
                <td>{c.status}</td>
                <td>{c.ringId ? <span className="pill bad">ring</span> : '—'}</td>
                <td>{new Date(c.openedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {items.length === 0 && !casesQuery.isLoading && <p>No cases match this filter.</p>}

      <div className="filters">
        {casesQuery.data?.nextCursor && (
          <button className="secondary" onClick={() => setCursor(casesQuery.data.nextCursor)}>
            Load more
          </button>
        )}
        {cursor && (
          <button className="secondary" onClick={() => setCursor(null)}>
            Back to start
          </button>
        )}
      </div>
    </section>
  );
}

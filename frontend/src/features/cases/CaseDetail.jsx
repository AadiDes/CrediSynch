import { useState } from 'react';
import { useGetCaseDetailQuery, usePostCaseLabelMutation } from '../../api/apiSlice';

const LABELS = ['FRAUD', 'LEGITIMATE', 'UNCERTAIN'];

export default function CaseDetail({ caseId, onBack }) {
  const { data: c, isLoading, error } = useGetCaseDetailQuery(caseId);
  const [postLabel, { isLoading: isLabelling }] = usePostCaseLabelMutation();
  const [label, setLabel] = useState('FRAUD');
  const [note, setNote] = useState('');
  const [labelResult, setLabelResult] = useState(null);

  async function submitLabel() {
    setLabelResult(null);
    try {
      await postLabel({ caseId, label, note }).unwrap();
      setLabelResult('ok');
      setNote('');
    } catch {
      setLabelResult('error');
    }
  }

  return (
    <section className="card">
      <button className="secondary" onClick={onBack}>
        ← Back to queue
      </button>

      {isLoading && <p>Loading…</p>}
      {error && <p className="bad">Could not load this case ({error.status ?? 'network'}).</p>}

      {c && (
        <>
          <h2>Case {c.caseId}</h2>
          <div className="kv">
            <span>Action</span>
            <span>{c.action}</span>
          </div>
          <div className="kv">
            <span>Status</span>
            <span>{c.status}</span>
          </div>
          <div className="kv">
            <span>Fraud probability</span>
            <span>{c.fraudProbability != null ? c.fraudProbability.toFixed(6) : '—'}</span>
          </div>
          <div className="kv">
            <span>Opened</span>
            <span>{new Date(c.openedAt).toLocaleString()}</span>
          </div>

          <h3>Reason codes</h3>
          {c.reasonCodes?.length ? (
            <table className="queue-table">
              <thead>
                <tr>
                  <th>Feature</th>
                  <th>Contribution</th>
                  <th>Direction</th>
                </tr>
              </thead>
              <tbody>
                {c.reasonCodes.map((rc) => (
                  <tr key={rc.code}>
                    <td>{rc.feature}</td>
                    <td>{rc.contribution.toFixed(4)}</td>
                    <td className={rc.direction === 'INCREASES_RISK' ? 'bad' : 'ok'}>{rc.direction}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p>No reason codes recorded.</p>
          )}

          <h3>Graph evidence</h3>
          <p>
            {c.linkedApplications?.length
              ? `Shares a device, phone, email, address or bank account with ${c.linkedApplications.length} other application(s).`
              : 'No shared identities with other applications.'}
          </p>
          {c.ring && (
            <p className="bad">
              Part of a detected ring: {c.ring.size} applications ({c.ring.algorithm}).
            </p>
          )}

          <h3>Analyst brief</h3>
          <p className="muted">
            {c.brief ?? 'No brief available — LLM analyst-brief generation is not configured for this deployment yet.'}
          </p>

          <h3>Similar cases</h3>
          <p>{c.similarCases?.length ? `${c.similarCases.length} similar case(s) found.` : 'None found.'}</p>

          <h3>Record a label</h3>
          <div className="filters">
            <select value={label} onChange={(e) => setLabel(e.target.value)}>
              {LABELS.map((l) => (
                <option key={l} value={l}>
                  {l}
                </option>
              ))}
            </select>
            <input
              type="text"
              placeholder="Note (optional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            <button onClick={submitLabel} disabled={isLabelling}>
              {isLabelling ? 'Saving…' : 'Save label'}
            </button>
          </div>
          {labelResult === 'ok' && <p className="ok">Label saved.</p>}
          {labelResult === 'error' && <p className="bad">Could not save the label.</p>}
        </>
      )}
    </section>
  );
}

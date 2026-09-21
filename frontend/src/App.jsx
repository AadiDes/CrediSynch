import { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useGetMeQuery, useGetPlatformStatusQuery } from './api/apiSlice';
import { sessionEstablished, selectSession } from './features/session/sessionSlice';
import CaseQueue from './features/cases/CaseQueue';
import CaseDetail from './features/cases/CaseDetail';
import keycloak from './keycloak';

function RoleGatedCard({ title, query, requiredRole }) {
  const { data, error, isLoading } = query;
  const forbidden = error?.status === 403;
  return (
    <section className="card">
      <h2>{title}</h2>
      {isLoading && <p>Loading…</p>}
      {forbidden && (
        <p className="bad">
          403 Forbidden — this endpoint requires the {requiredRole} role. Authorisation is enforced
          server side, not hidden in the UI.
        </p>
      )}
      {error && !forbidden && <p className="bad">Request failed ({error.status ?? 'network'}).</p>}
      {data && <pre>{JSON.stringify(data, null, 2)}</pre>}
    </section>
  );
}

export default function App() {
  const dispatch = useDispatch();
  const session = useSelector(selectSession);
  const meQuery = useGetMeQuery();
  const platformQuery = useGetPlatformStatusQuery();
  const [selectedCaseId, setSelectedCaseId] = useState(null);
  const isAnalyst = session.roles.includes('ANALYST');

  useEffect(() => {
    if (meQuery.data) {
      dispatch(sessionEstablished(meQuery.data));
    }
  }, [meQuery.data, dispatch]);

  return (
    <div className="shell">
      <header>
        <h1>
          Credi<span>Synch</span> — fraud decisioning console
        </h1>
        <div>
          <span className="pill">{session.username ?? '…'}</span>
          {session.roles.map((role) => (
            <span className="pill" key={role}>
              {role}
            </span>
          ))}
          <button className="secondary" onClick={() => keycloak.logout()}>
            Sign out
          </button>
        </div>
      </header>

      <div className="grid">
        <section className="card">
          <h2>Session</h2>
          <div className="kv">
            <span>Identity provider</span>
            <span>{meQuery.data?.issuer ?? '—'}</span>
          </div>
          <div className="kv">
            <span>Authentication level (acr)</span>
            <span>{meQuery.data?.authLevel ?? '—'}</span>
          </div>
          <div className="kv">
            <span>Token transport</span>
            <span className="ok">Bearer, in memory only</span>
          </div>
        </section>

        <RoleGatedCard title="Platform status (ADMIN)" query={platformQuery} requiredRole="ADMIN" />
      </div>

      {isAnalyst && (
        <div className="grid single">
          {selectedCaseId ? (
            <CaseDetail caseId={selectedCaseId} onBack={() => setSelectedCaseId(null)} />
          ) : (
            <CaseQueue onSelectCase={setSelectedCaseId} />
          )}
        </div>
      )}
    </div>
  );
}

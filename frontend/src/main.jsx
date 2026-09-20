import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import App from './App.jsx';
import { store } from './app/store';
import { initKeycloak } from './keycloak';
import './styles.css';

initKeycloak()
  .then((authenticated) => {
    if (!authenticated) {
      document.getElementById('root').innerHTML = '<p style="padding:24px">Not authenticated.</p>';
      return;
    }
    ReactDOM.createRoot(document.getElementById('root')).render(
      <React.StrictMode>
        <Provider store={store}>
          <App />
        </Provider>
      </React.StrictMode>,
    );
  })
  .catch((error) => {
    // Explicit failure path: an identity provider outage must not render a blank page.
    document.getElementById('root').innerHTML =
      `<p style="padding:24px">Sign-in is unavailable (${error?.message ?? 'identity provider unreachable'}). ` +
      'Check that Keycloak is running on port 8081.</p>';
  });

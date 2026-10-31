import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from 'react-oidc-context';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { WebStorageStateStore } from 'oidc-client-ts';
import { theme } from './theme';
import App from './App';

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchInterval: 15_000, staleTime: 5_000, retry: 1 } },
});

// Keycloak (OIDC, authorization code + PKCE). The issuer is the browser-facing Keycloak URL.
const oidc = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/democorp',
  client_id: 'warroom-ui',
  redirect_uri: window.location.origin + '/',
  post_logout_redirect_uri: window.location.origin + '/',
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  onSigninCallback: () => window.history.replaceState({}, document.title, window.location.pathname),
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider {...oidc}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </AuthProvider>
  </React.StrictMode>,
);

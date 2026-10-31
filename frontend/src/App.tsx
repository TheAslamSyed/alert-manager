import { useEffect, type ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { NavLink, Route, Routes } from 'react-router-dom';
import { Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import { rolesFromToken, setAccessToken } from './api';
import { useLiveEvents } from './live';
import CommandCenter from './pages/CommandCenter';
import IncidentDetail from './pages/IncidentDetail';
import Approvals from './pages/Approvals';
import Communications from './pages/Communications';
import Knowledge from './pages/Knowledge';
import Evaluation from './pages/Evaluation';

const NAV = [
  { to: '/', label: 'Command Center' },
  { to: '/approvals', label: 'Approvals' },
  { to: '/communications', label: 'Communications' },
  { to: '/knowledge', label: 'Knowledge Base' },
  { to: '/evaluation', label: 'Evaluation' },
];

export default function App() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  setAccessToken(token);
  useEffect(() => setAccessToken(token), [token]);
  const live = useLiveEvents(auth.isAuthenticated);

  if (auth.isLoading) {
    return <Centered><CircularProgress /></Centered>;
  }
  if (auth.error) {
    return <Centered><Typography color="error">Login error: {auth.error.message}</Typography>
      <Button onClick={() => auth.signinRedirect()}>Try again</Button></Centered>;
  }
  if (!auth.isAuthenticated) {
    return (
      <Centered>
        <Typography variant="h1">War Room</Typography>
        <Typography color="text.secondary">DemoCorp Bank incident command center</Typography>
        <Button variant="contained" onClick={() => auth.signinRedirect()}>Sign in with Keycloak</Button>
      </Centered>
    );
  }

  const roles = rolesFromToken(token);
  const name = auth.user?.profile.preferred_username ?? auth.user?.profile.name;

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', minHeight: '100vh' }}>
      <Box component="nav" aria-label="Main navigation"
           sx={{ flex: '1 1 220px', maxWidth: { md: 240 }, p: 2, bgcolor: 'background.paper', borderRight: 1, borderColor: 'divider',
                 display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        <Box sx={{ px: 1.5, pb: 2 }}>
          <Typography sx={{ fontSize: 18, fontWeight: 700 }}>War Room</Typography>
          <Typography variant="caption" color="text.secondary">DemoCorp Bank · {live.connected ? 'live' : 'connecting…'}</Typography>
        </Box>
        {NAV.map((n) => (
          <Box key={n.to} component={NavLink} to={n.to} end={n.to === '/'}
               sx={{ px: 1.5, py: 1.2, borderRadius: 2, color: 'text.secondary', textDecoration: 'none', fontSize: 14, fontWeight: 500,
                     '&.active': { bgcolor: '#1F2A3A', color: 'text.primary' }, '&:hover': { color: 'text.primary' } }}>
            {n.label}
          </Box>
        ))}
        <Box sx={{ mt: 'auto', pt: 2, px: 1.5, borderTop: 1, borderColor: 'divider' }}>
          <Typography sx={{ fontSize: 14, fontWeight: 500 }}>{name}</Typography>
          <Typography variant="caption" color="text.secondary">{roles.filter((r) => r === r.toUpperCase() && !r.includes('-')).join(' · ')}</Typography>
          <Box><Button size="small" onClick={() => auth.signoutRedirect()}>Sign out</Button></Box>
        </Box>
      </Box>
      <Box component="main" sx={{ flex: '999 1 560px', minWidth: 0, p: { xs: 2, md: 3 } }}>
        <Routes>
          <Route path="/" element={<CommandCenter roles={roles} live={live.events} />} />
          <Route path="/incidents/:id" element={<IncidentDetail roles={roles} user={String(name)} />} />
          <Route path="/approvals" element={<Approvals roles={roles} user={String(name)} />} />
          <Route path="/communications" element={<Communications roles={roles} />} />
          <Route path="/knowledge" element={<Knowledge roles={roles} />} />
          <Route path="/evaluation" element={<Evaluation />} />
        </Routes>
      </Box>
    </Box>
  );
}

function Centered({ children }: { children: ReactNode }) {
  return <Stack sx={{ minHeight: '100vh', alignItems: 'center', justifyContent: 'center', gap: 2 }}>{children}</Stack>;
}

import { createTheme } from '@mui/material/styles';

/** Dark "command center" theme from the prototype. Colours chosen to pass 4.5:1 contrast. */
export const theme = createTheme({
  palette: {
    mode: 'dark',
    background: { default: '#0E1217', paper: '#151B23' },
    primary: { main: '#58A6FF', contrastText: '#0E1217' },
    success: { main: '#6EE7B7' },
    warning: { main: '#FFC46B' },
    error: { main: '#FFA3A3' },
    info: { main: '#8CC4FF' },
    text: { primary: '#E6EDF3', secondary: '#9BA8B5' },
    divider: '#2A3441',
  },
  typography: {
    fontFamily: 'Roboto, system-ui, sans-serif',
    h1: { fontSize: 26, fontWeight: 500 },
    h2: { fontSize: 16, fontWeight: 500 },
  },
  shape: { borderRadius: 10 },
  components: {
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none', border: '1px solid #2A3441' } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { textTransform: 'none', minHeight: 40 } } },
    MuiTab: { styleOverrides: { root: { textTransform: 'none' } } },
  },
});

export const mono = { fontFamily: '"Roboto Mono", monospace' };

export const severityColor: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
  SEV1: 'error', SEV2: 'warning', SEV3: 'info', SEV4: 'default',
};

export const statusColor: Record<string, 'error' | 'warning' | 'info' | 'success' | 'default' | 'primary'> = {
  OPEN: 'error', INVESTIGATING: 'warning', IDENTIFIED: 'warning', MITIGATING: 'info',
  MITIGATED: 'primary', RESOLVED: 'success', CLOSED_FALSE_POSITIVE: 'default',
  PROPOSED: 'default', PENDING_APPROVAL: 'warning', APPROVED: 'info', EXECUTING: 'info',
  SUCCEEDED: 'success', FAILED_VERIFICATION: 'error', NO_EFFECT: 'error', ERROR: 'error', REJECTED: 'default',
  DRAFT: 'warning', SENT: 'success', SUPERSEDED: 'default', IN_REVIEW: 'warning', PUBLISHED: 'success',
};

export const label = (s?: string | null) =>
  !s ? '' : (s.charAt(0) + s.slice(1).toLowerCase()).replace(/_/g, ' ');

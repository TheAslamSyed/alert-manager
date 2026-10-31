import type { ReactNode } from 'react';
import { Alert, Box, Chip, LinearProgress, Paper, Stack, Typography } from '@mui/material';
import { label, mono, severityColor, statusColor } from '../theme';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 2, alignItems: 'flex-end', justifyContent: 'space-between', mb: 2.5 }}>
      <Box>
        <Typography variant="h1" component="h1">{title}</Typography>
        {subtitle && <Typography color="text.secondary" sx={{ mt: 0.5, fontSize: 14 }}>{subtitle}</Typography>}
      </Box>
      {actions}
    </Stack>
  );
}

export function Panel({ title, right, children, sx }: { title?: string; right?: ReactNode; children: ReactNode; sx?: object }) {
  return (
    <Paper sx={{ display: 'flex', flexDirection: 'column', minWidth: 0, ...sx }}>
      {title && (
        <Stack direction="row" sx={{ px: 2.5, py: 1.75, borderBottom: 1, borderColor: 'divider', justifyContent: 'space-between', alignItems: 'center', gap: 1 }}>
          <Typography variant="h2" component="h2">{title}</Typography>
          {right}
        </Stack>
      )}
      <Box sx={{ p: title ? 0 : 2.5 }}>{children}</Box>
    </Paper>
  );
}

export function StatusChip({ value }: { value?: string | null }) {
  if (!value) return null;
  return <Chip size="small" variant="outlined" color={statusColor[value] ?? 'default'} label={label(value)} />;
}

export function SeverityChip({ value }: { value?: string }) {
  if (!value) return null;
  return <Chip size="small" color={severityColor[value] ?? 'default'} label={value} sx={{ fontWeight: 700 }} />;
}

export function Kpi({ label: l, value, note }: { label: string; value: ReactNode; note?: string }) {
  return (
    <Paper sx={{ flex: '1 1 170px', p: 2 }}>
      <Typography variant="caption" color="text.secondary">{l}</Typography>
      <Typography sx={{ fontSize: 28, fontWeight: 500 }}>{value}</Typography>
      {note && <Typography variant="caption" color="text.secondary">{note}</Typography>}
    </Paper>
  );
}

export function ConfidenceBar({ value, color = 'primary' }: { value: number; color?: 'primary' | 'warning' | 'success' }) {
  return <LinearProgress variant="determinate" value={Math.round(value * 100)} color={color}
                         aria-label={`${Math.round(value * 100)} percent`} sx={{ height: 8, borderRadius: 4 }} />;
}

export function Mono({ children }: { children: ReactNode }) {
  return <Box component="span" sx={{ ...mono, fontSize: 13 }}>{children}</Box>;
}

export function CodeBlock({ children }: { children: ReactNode }) {
  return (
    <Box component="pre" sx={{ ...mono, fontSize: 12, m: 0, p: 1.5, borderRadius: 2, bgcolor: '#0E1217', border: 1,
                               borderColor: 'divider', whiteSpace: 'pre-wrap', overflowX: 'auto', color: '#C9D3DD' }}>
      {children}
    </Box>
  );
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null;
  return <Alert severity="error" sx={{ my: 1 }}>{error instanceof Error ? error.message : String(error)}</Alert>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <Typography color="text.secondary" sx={{ p: 2.5, fontSize: 14 }}>{children}</Typography>;
}

export const time = (iso?: string | null) => (iso ? new Date(iso).toLocaleTimeString() : '');
export const dateTime = (iso?: string | null) => (iso ? new Date(iso).toLocaleString() : '');
export const pct = (v?: number | null) => (v === undefined || v === null ? '–' : `${Math.round(v * 100)}%`);

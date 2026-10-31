import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Box, Button, FormControl, InputLabel, Link, MenuItem, Select, Stack, Typography } from '@mui/material';
import { api } from '../api';
import type { LiveEvent } from '../live';
import { Empty, ErrorText, Kpi, Mono, PageHeader, Panel, SeverityChip, StatusChip, pct, time } from '../components/ui';

const OPEN = ['OPEN', 'INVESTIGATING', 'IDENTIFIED', 'MITIGATING', 'MITIGATED'];

export default function CommandCenter({ roles, live }: { roles: string[]; live: LiveEvent[] }) {
  const incidents = useQuery({ queryKey: ['incidents'], queryFn: () => api.get<any[]>('/incidents?limit=50') });
  const actions = useQuery({ queryKey: ['remediations', 'all'], queryFn: () => api.get<any[]>('/remediations') });
  const scenarios = useQuery({ queryKey: ['scenarios'], queryFn: () => api.get<any[]>('/demo/scenarios') });
  const [scenario, setScenario] = useState('FIN-01');
  const start = useMutation({ mutationFn: () => api.post(`/demo/scenarios/${scenario}/start`) });
  const stop = useMutation({ mutationFn: () => api.post('/demo/scenarios/stop') });

  const list = incidents.data ?? [];
  const open = list.filter((i) => OPEN.includes(i.status));
  const waiting = (actions.data ?? []).filter((a) => ['PROPOSED', 'PENDING_APPROVAL'].includes(a.status)).length;
  const canRun = roles.includes('RESPONDER') || roles.includes('APPROVER') || roles.includes('ADMIN');

  return (
    <>
      <PageHeader title="Command Center" subtitle="Every DemoCorp Bank incident, investigated live by the agents"
        actions={canRun && (
          <Stack direction="row" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <FormControl size="small" sx={{ minWidth: 300 }}>
              <InputLabel id="scn">Demo scenario</InputLabel>
              <Select labelId="scn" label="Demo scenario" value={scenario} onChange={(e) => setScenario(e.target.value)}>
                {(scenarios.data ?? []).map((s) => <MenuItem key={s.code} value={s.code}>{s.code} · {s.title}</MenuItem>)}
              </Select>
            </FormControl>
            <Button variant="contained" onClick={() => start.mutate()} disabled={start.isPending}>Run scenario</Button>
            <Button variant="outlined" onClick={() => stop.mutate()}>Stop</Button>
          </Stack>
        )} />
      <ErrorText error={incidents.error || start.error} />
      {start.isSuccess && <Typography variant="caption" color="success.main">Scenario started: alerts usually arrive within a minute.</Typography>}

      <Stack direction="row" sx={{ gap: 2, flexWrap: 'wrap', my: 2 }}>
        <Kpi label="Open incidents" value={open.length} note={`${list.length} in the list`} />
        <Kpi label="Active SEV1" value={open.filter((i) => i.severity === 'SEV1').length} />
        <Kpi label="Fixes waiting for a decision" value={waiting} />
        <Kpi label="Root cause found" value={open.filter((i) => i.rcaSummary).length} note="of the open incidents" />
      </Stack>

      <Panel title="Incidents" right={<Typography variant="caption" color="text.secondary">Updates live</Typography>}>
        <Box sx={{ overflowX: 'auto' }}>
          <Box sx={{ minWidth: 900 }}>
            {list.length === 0 && <Empty>No incidents yet. Run a demo scenario to start one.</Empty>}
            {list.map((i) => (
              <Box key={i.id} sx={{ display: 'grid', gridTemplateColumns: '100px minmax(220px,2fr) 170px 70px 150px 80px minmax(200px,1.6fr)',
                                    gap: 1.5, alignItems: 'center', px: 2.5, py: 1.25, borderTop: 1, borderColor: 'divider', fontSize: 14 }}>
                <Link component={RouterLink} to={`/incidents/${i.id}`}><Mono>{i.id}</Mono></Link>
                <Typography sx={{ fontSize: 14 }}>{i.title}</Typography>
                <Mono>{i.services.join(', ')}</Mono>
                <SeverityChip value={i.severity} />
                <StatusChip value={i.status} />
                <Typography variant="caption" color="text.secondary">{time(i.openedAt)}</Typography>
                <Typography sx={{ fontSize: 13 }}>{i.rcaSummary ? `${i.rcaSummary} · ${pct(i.rcaConfidence)}` : '—'}</Typography>
              </Box>
            ))}
          </Box>
        </Box>
      </Panel>

      <Box sx={{ mt: 2 }}>
        <Panel title="Live activity" right={<Typography variant="caption" color="text.secondary">Server-sent events</Typography>}>
          {live.length === 0 && <Empty>Waiting for events…</Empty>}
          {live.map((e, n) => (
            <Stack key={n} direction="row" sx={{ gap: 2, px: 2.5, py: 1, borderTop: 1, borderColor: 'divider' }}>
              <Mono>{time(e.at)}</Mono>
              <Box>
                <Typography sx={{ fontSize: 12, color: 'info.main', fontWeight: 500 }}>{e.incidentId} · {e.status?.toLowerCase()}</Typography>
                <Typography sx={{ fontSize: 14 }}>{e.message}</Typography>
              </Box>
            </Stack>
          ))}
        </Panel>
      </Box>
    </>
  );
}

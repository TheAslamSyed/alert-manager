import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Stack, Typography } from '@mui/material';
import { api } from '../api';
import { Empty, ErrorText, Kpi, Mono, PageHeader, Panel, pct } from '../components/ui';

/** Deliverable: MTTR evaluation over the practice incidents with known answers. */
export default function Evaluation() {
  const report = useQuery({ queryKey: ['evaluation'], queryFn: () => api.get('/demo/evaluation/report'), refetchInterval: 10_000 });
  const run = useMutation({ mutationFn: () => api.post('/demo/evaluation/run', {}), onSuccess: () => report.refetch() });
  const r: any = report.data;
  const max = Math.max(1, ...(r?.results ?? []).map((x: any) => Math.max(x.manualMttrMinutes ?? 0, x.agentMttrMinutes ?? 0)));

  return (
    <>
      <PageHeader title="Evaluation · MTTR reduction"
        subtitle="Runs every DemoCorp practice incident and compares the agents' answer with the known root cause"
        actions={<Button variant="contained" onClick={() => run.mutate()} disabled={r?.status === 'RUNNING' || run.isPending}>Run evaluation</Button>} />
      <ErrorText error={run.error || report.error} />
      <Alert severity="info" sx={{ mb: 2 }}>
        Agent MTTR = measured time to diagnosis + assumed approval and fix time. Manual MTTR = time-to-resolve of the most
        similar past incident in the knowledge base. A full run takes about 4 minutes per scenario.
      </Alert>
      {!r && <Empty>No evaluation run yet.</Empty>}
      {r && (
        <>
          <Typography color="text.secondary" sx={{ mb: 1 }}>Status: {r.status}{r.active && ` · running ${r.active}`}</Typography>
          <Stack direction="row" sx={{ gap: 2, flexWrap: 'wrap', mb: 2 }}>
            <Kpi label="Correct cause ranked #1" value={r.summary.top1Accuracy} note={`top-3: ${r.summary.top3Accuracy}`} />
            <Kpi label="Correct fix proposed" value={r.summary.correctFixProposed} />
            <Kpi label="Average time to diagnosis" value={`${r.summary.avgTimeToDiagnosisSeconds} s`} />
            <Kpi label="MTTR: manual → agents" value={`${r.summary.avgManualMttrMinutes} → ${r.summary.avgAgentMttrMinutes} min`} note={`reduction ${r.summary.mttrReduction}`} />
          </Stack>
          <Panel title="Results by scenario">
            <Box sx={{ overflowX: 'auto' }}><Box sx={{ minWidth: 1000 }}>
              {r.results.map((x: any) => (
                <Box key={x.code} sx={{ display: 'grid', gridTemplateColumns: '70px minmax(200px,1.3fr) 170px 70px 70px 90px minmax(260px,1.5fr)',
                                        gap: 1.5, alignItems: 'center', px: 2.5, py: 1.25, borderTop: 1, borderColor: 'divider', fontSize: 14 }}>
                  <Mono>{x.code}</Mono>
                  <Box><Typography sx={{ fontSize: 14 }}>{x.title}</Typography><Typography variant="caption" color="text.secondary">expected {x.expected}</Typography></Box>
                  <Typography sx={{ fontSize: 13 }}>{x.topCause ?? '—'}</Typography>
                  <Typography sx={{ color: x.top1 ? 'success.main' : x.top3 ? 'warning.main' : 'error.main', fontWeight: 500 }}>{x.top1 ? 'Yes' : x.top3 ? 'Top 3' : 'No'}</Typography>
                  <Typography>{pct(x.confidence)}</Typography>
                  <Mono>{x.diagnoseSeconds ?? '–'} s</Mono>
                  <Box>
                    <Bar value={x.manualMttrMinutes} max={max} color="#5B6775" label={`${x.manualMttrMinutes} min manual`} />
                    <Bar value={x.agentMttrMinutes ?? 0} max={max} color="#58A6FF" label={`${x.agentMttrMinutes ?? '–'} min agents`} />
                  </Box>
                </Box>
              ))}
            </Box></Box>
          </Panel>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Markdown report: <a href="#" onClick={async (e) => { e.preventDefault(); const md = await api.get<string>('/demo/evaluation/report.md');
              const w = window.open(); if (w) { w.document.body.innerText = md; } }}>open</a>
          </Typography>
        </>
      )}
    </>
  );
}

function Bar({ value, max, color, label }: { value: number; max: number; color: string; label: string }) {
  return (
    <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
      <Box aria-hidden sx={{ flex: 1, height: 8, borderRadius: 1, bgcolor: '#1C2430' }}>
        <Box sx={{ width: `${Math.round((value / max) * 100)}%`, height: 8, borderRadius: 1, bgcolor: color }} />
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 110 }}>{label}</Typography>
    </Stack>
  );
}

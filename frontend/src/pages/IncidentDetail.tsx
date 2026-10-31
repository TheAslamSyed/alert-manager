import { useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, Link, Stack, Tab, Tabs, Typography } from '@mui/material';
import { api } from '../api';
import { label } from '../theme';
import CommsPanel from '../components/CommsPanel';
import FixesPanel from '../components/FixesPanel';
import { CodeBlock, ConfidenceBar, Empty, ErrorText, Mono, PageHeader, Panel, SeverityChip, StatusChip, dateTime, pct, time } from '../components/ui';

const STEPS = ['OPEN', 'INVESTIGATING', 'IDENTIFIED', 'MITIGATING', 'MITIGATED', 'RESOLVED'];
const FACTORS: Record<string, string> = {
  evidenceSupport: 'Evidence support', sourceDiversity: 'Source diversity', temporalPrecedence: 'Cause before symptom',
  historicalMatch: 'Match with past incident', modelAssessment: 'Model assessment',
};

export default function IncidentDetail({ roles, user }: { roles: string[]; user: string }) {
  const { id = '' } = useParams();
  const qc = useQueryClient();
  const [tab, setTab] = useState(0);
  const detail = useQuery({ queryKey: ['incident', id], queryFn: () => api.get(`/incidents/${id}`) });
  const inv = useQuery({ queryKey: ['incident', id, 'investigation'], queryFn: () => api.get(`/incidents/${id}/investigation`) });
  const move = useMutation({
    mutationFn: (status: string) => api.post(`/incidents/${id}/status`, { status, note: `by ${user}` }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['incident', id] }),
  });

  if (detail.isLoading) return <Empty>Loading…</Empty>;
  if (detail.error) return <ErrorText error={detail.error} />;
  const d: any = detail.data;
  const i = d.incident;
  const current = STEPS.indexOf(i.status);
  const evidence: any[] = (inv.data?.findings ?? []).flatMap((f: any) => f.finding?.evidence ?? []);
  const byId = Object.fromEntries(evidence.map((e) => [e.id, e]));
  const rca = inv.data?.rca ?? {};
  const canAct = roles.includes('RESPONDER') || roles.includes('APPROVER');

  return (
    <>
      <Link component={RouterLink} to="/" sx={{ fontSize: 13 }}>Back to Command Center</Link>
      <PageHeader title={i.title}
        subtitle={<><Mono>{i.id}</Mono> · {i.services.join(', ')} · opened {dateTime(i.openedAt)}</>}
        actions={<Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}><SeverityChip value={i.severity} /><StatusChip value={i.status} /></Stack>} />

      <Stack direction="row" component="ol" aria-label="Incident status" sx={{ listStyle: 'none', p: 0, m: 0, mb: 2, gap: 1, flexWrap: 'wrap' }}>
        {i.status === 'CLOSED_FALSE_POSITIVE'
          ? <Chip component="li" label="Closed as false alarm" />
          : STEPS.map((s, n) => (
            <Chip component="li" key={s} label={label(s)} color={n === current ? 'primary' : n < current ? 'success' : 'default'}
                  variant={n === current ? 'filled' : 'outlined'} />
          ))}
      </Stack>

      {canAct && d.allowedNextStatuses.some((s: string) => ['RESOLVED', 'CLOSED_FALSE_POSITIVE'].includes(s)) && (
        <Stack direction="row" sx={{ gap: 1, mb: 2, flexWrap: 'wrap' }}>
          {d.allowedNextStatuses.includes('RESOLVED') && <Button variant="contained" color="success" onClick={() => move.mutate('RESOLVED')}>Mark resolved</Button>}
          {d.allowedNextStatuses.includes('CLOSED_FALSE_POSITIVE') && <Button variant="outlined" onClick={() => move.mutate('CLOSED_FALSE_POSITIVE')}>Close as false alarm</Button>}
        </Stack>
      )}
      <ErrorText error={move.error} />

      <Tabs value={tab} onChange={(_e, v) => setTab(v)} variant="scrollable" sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Root cause" /><Tab label={`Evidence (${evidence.length})`} /><Tab label="Fixes" />
        <Tab label="Communications" /><Tab label="Timeline" /><Tab label="Audit" />
      </Tabs>

      {tab === 0 && (
        <Stack sx={{ gap: 2 }}>
          <Panel title="Root cause analysis" right={<Typography variant="caption" color="text.secondary">
            {rca.method ? `${rca.method === 'LLM' ? `LLM (${rca.model}) + rules` : 'rules only (LLM unavailable)'} · every claim cites evidence` : 'waiting for the RCA agent…'}</Typography>}>
            {(rca.hypotheses ?? []).length === 0 && <Empty>The agents are still investigating.</Empty>}
            {(rca.hypotheses ?? []).map((h: any) => (
              <Box key={h.rank} sx={{ px: 2.5, py: 2, borderTop: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 2 }}>
                  <Typography sx={{ fontWeight: 500 }}>#{h.rank} {h.title} <Typography component="span" variant="caption" color="text.secondary">({h.category})</Typography></Typography>
                  <Typography sx={{ fontSize: 20, fontWeight: 500, color: h.rank === 1 ? 'warning.main' : 'info.main' }}>{pct(h.confidence)}</Typography>
                </Stack>
                <ConfidenceBar value={h.confidence} color={h.rank === 1 ? 'warning' : 'primary'} />
                <Typography sx={{ fontSize: 14, color: '#C9D3DD' }}>{h.explanation}</Typography>
                <Stack direction="row" sx={{ gap: 0.75, flexWrap: 'wrap', alignItems: 'center' }}>
                  <Typography variant="caption" color="text.secondary">Evidence</Typography>
                  {h.evidenceIds.map((e: string) => <Chip key={e} size="small" color="info" variant="outlined" label={e} title={byId[e]?.title} />)}
                </Stack>
                {h.rank === 1 && h.confidenceFactors && (
                  <Box sx={{ mt: 1, p: 1.5, borderRadius: 2, bgcolor: '#121820' }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 500, mb: 1 }}>How the {pct(h.confidence)} was calculated</Typography>
                    {Object.entries(h.confidenceFactors).map(([k, v]: any) => (
                      <Box key={k} sx={{ display: 'grid', gridTemplateColumns: '200px 1fr 44px', gap: 1.5, alignItems: 'center', fontSize: 13, mb: 0.5 }}>
                        <Typography variant="caption" color="text.secondary">{FACTORS[k] ?? k}</Typography>
                        <ConfidenceBar value={v} />
                        <Typography sx={{ fontSize: 13, textAlign: 'right' }}>{Number(v).toFixed(2)}</Typography>
                      </Box>
                    ))}
                    <Typography variant="caption" color="text.secondary">0.35·support + 0.15·diversity + 0.15·timing + 0.15·history + 0.20·model</Typography>
                  </Box>
                )}
              </Box>
            ))}
            {(rca.gaps ?? []).length > 0 && <Alert severity="warning" sx={{ m: 2 }}>Gaps: {rca.gaps.join(' · ')}</Alert>}
          </Panel>
          <Panel title="Agents">
            {(inv.data?.findings ?? []).map((f: any, n: number) => (
              <Stack key={n} direction="row" sx={{ px: 2.5, py: 1.25, borderTop: 1, borderColor: 'divider', justifyContent: 'space-between', gap: 2 }}>
                <Box><Typography sx={{ fontSize: 14 }}>{f.agent}</Typography><Typography variant="caption" color="text.secondary">{f.summary}</Typography></Box>
                <Chip size="small" color={f.status === 'DONE' ? 'success' : 'warning'} variant="outlined" label={`${label(f.status)} · ${Math.round((f.finding?.durationMs ?? 0) / 100) / 10}s`} />
              </Stack>
            ))}
          </Panel>
        </Stack>
      )}

      {tab === 1 && (
        <Panel title="Evidence board" right={<Typography variant="caption" color="text.secondary">Each item shows the exact query used</Typography>}>
          {evidence.length === 0 && <Empty>No evidence yet.</Empty>}
          {evidence.map((e) => (
            <Box key={e.id} sx={{ px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', gap: 0.75 }}>
              <Stack direction="row" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Mono>{e.id}</Mono><Chip size="small" label={e.source} /><Typography variant="caption" color="text.secondary">{e.agent}</Typography>
              </Stack>
              <Typography sx={{ fontSize: 14 }}>{e.title}</Typography>
              <Typography variant="caption" color="text.secondary">{e.detail}</Typography>
              <CodeBlock>{e.query}</CodeBlock>
            </Box>
          ))}
        </Panel>
      )}

      {tab === 2 && <FixesPanel incidentId={id} roles={roles} user={user} />}
      {tab === 3 && <CommsPanel incidentId={id} roles={roles} />}

      {tab === 4 && (
        <Panel title="Timeline">
          {d.correlationNotes.length > 0 && (
            <Box sx={{ px: 2.5, py: 1.5 }}>{d.correlationNotes.map((n: string) => <Typography key={n} variant="body2" color="text.secondary">• {n}</Typography>)}</Box>
          )}
          {d.timeline.map((t: any, n: number) => (
            <Stack key={n} direction="row" sx={{ gap: 2, px: 2.5, py: 1, borderTop: 1, borderColor: 'divider' }}>
              <Mono>{time(t.at)}</Mono>
              <Typography sx={{ fontSize: 14, flex: 1 }}>{t.text}</Typography>
              <Typography variant="caption" color="text.secondary">{t.actor}</Typography>
            </Stack>
          ))}
        </Panel>
      )}

      {tab === 5 && <AuditTab incidentId={id} />}
    </>
  );
}

function AuditTab({ incidentId }: { incidentId: string }) {
  const records = useQuery({ queryKey: ['audit', incidentId], queryFn: () => api.get<any[]>(`/audit?incidentId=${incidentId}`) });
  const verify = useMutation({ mutationFn: () => api.get('/audit/verify') });
  return (
    <Panel title="Audit trail" right={<Button variant="outlined" size="small" onClick={() => verify.mutate()}>Verify chain</Button>}>
      {verify.data && (
        <Alert severity={verify.data.valid ? 'success' : 'error'} sx={{ m: 2 }}>
          {verify.data.valid ? `Hash chain verified · ${verify.data.records} records · head ${String(verify.data.lastHash).slice(0, 12)}…`
            : `Chain broken at record ${verify.data.brokenAt}`}
        </Alert>
      )}
      <ErrorText error={records.error || verify.error} />
      {(records.data ?? []).map((r) => (
        <Stack key={r.seq} direction="row" sx={{ gap: 2, px: 2.5, py: 1, borderTop: 1, borderColor: 'divider', flexWrap: 'wrap' }}>
          <Mono>#{r.seq}</Mono>
          <Typography sx={{ fontSize: 14, minWidth: 200 }}>{r.type}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ minWidth: 140 }}>{r.producer}</Typography>
          <Mono>{String(r.hash).slice(0, 16)}…</Mono>
        </Stack>
      ))}
    </Panel>
  );
}

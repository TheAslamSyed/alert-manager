import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, Stack, TextField, Typography } from '@mui/material';
import { api } from '../api';
import { CodeBlock, Empty, ErrorText, Mono, Panel, StatusChip, dateTime } from './ui';

/**
 * FR-06 / FR-08: proposed fixes and the approval workflow.
 * Buttons only show for the right roles; remediation-service enforces every rule again.
 */
export default function FixesPanel({ incidentId, roles, user }: { incidentId?: string; roles: string[]; user: string }) {
  const qc = useQueryClient();
  const path = incidentId ? `/remediations?incidentId=${incidentId}` : '/remediations';
  const actions = useQuery({ queryKey: ['remediations', incidentId ?? 'all'], queryFn: () => api.get<any[]>(path) });
  const plan = useQuery({
    queryKey: ['plan', incidentId], enabled: !!incidentId, retry: false,
    queryFn: () => api.get(`/remediations/plans/${incidentId}`),
  });
  const [reason, setReason] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<Record<string, string>>({});
  const act = useMutation({
    mutationFn: ({ id, op }: { id: string; op: string }) =>
      op === 'dry-run' ? api.post(`/remediations/${id}/dry-run`) : api.post(`/remediations/${id}/${op}`, { reason: reason[id] }),
    onSuccess: (data: any, v) => {
      if (v.op === 'dry-run') setPreview((p) => ({ ...p, [v.id]: data.preview }));
      qc.invalidateQueries({ queryKey: ['remediations'] });
    },
  });
  const isResponder = roles.includes('RESPONDER') || roles.includes('APPROVER');
  const isApprover = roles.includes('APPROVER') && !roles.includes('ADMIN');
  const list = (actions.data ?? []).filter((a) => incidentId || ['PROPOSED', 'PENDING_APPROVAL', 'APPROVED', 'EXECUTING'].includes(a.status));

  return (
    <Panel title={incidentId ? 'Proposed fixes' : 'Waiting for a decision'} right={<Typography variant="caption" color="text.secondary">Nothing runs until an approver says yes</Typography>}>
      <ErrorText error={act.error} />
      {plan.data && (
        <Box sx={{ px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          {plan.data.closeAsFalsePositive && <Alert severity="info" sx={{ mb: 1 }}>The planner recommends closing this incident as a false alarm.</Alert>}
          <Typography variant="caption" color="text.secondary">{plan.data.rationale}</Typography>
          {plan.data.preventive?.length > 0 && (
            <Box sx={{ mt: 1 }}>
              <Typography sx={{ fontSize: 13, fontWeight: 500 }}>Preventive actions (follow-up)</Typography>
              <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 14, color: '#C9D3DD' }}>
                {plan.data.preventive.map((p: string) => <li key={p}>{p}</li>)}
              </Box>
            </Box>
          )}
        </Box>
      )}
      {list.length === 0 && <Empty>No fixes waiting.</Empty>}
      {list.map((a) => (
        <Box key={a.id} sx={{ px: 2.5, py: 2, borderTop: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Stack direction="row" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Mono>{a.id} · {a.action}</Mono>
            <Chip size="small" label={`${a.risk} risk`} color={a.risk === 'HIGH' ? 'error' : a.risk === 'MEDIUM' ? 'warning' : 'info'} />
            <StatusChip value={a.status} />
            {!incidentId && <Mono>{a.incidentId}</Mono>}
          </Stack>
          <Typography sx={{ fontWeight: 500 }}>{a.description} on {a.target}</Typography>
          <Typography variant="body2" color="text.secondary">{a.reason}</Typography>
          <Typography variant="caption" color="text.secondary">
            Proposed by {a.proposedBy}{a.submittedBy && ` · sent for approval by ${a.submittedBy}`}
            {a.approvals?.length > 0 && ` · approved by ${a.approvals.join(', ')}`} · needs {a.approvalsNeeded} approver(s) · {dateTime(a.updatedAt)}
          </Typography>
          {a.resultMessage && <Alert severity={a.status === 'SUCCEEDED' ? 'success' : 'warning'}>{a.resultMessage}{a.verification && <><br /><Mono>{a.verification}</Mono></>}</Alert>}
          {preview[a.id] && <CodeBlock>DRY RUN  {preview[a.id]}</CodeBlock>}
          {['PROPOSED', 'PENDING_APPROVAL'].includes(a.status) && (
            <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
              {isResponder && <Button variant="outlined" onClick={() => act.mutate({ id: a.id, op: 'dry-run' })}>Dry run</Button>}
              {a.status === 'PROPOSED' && isResponder && (
                <Button variant="contained" onClick={() => act.mutate({ id: a.id, op: 'submit' })}>Send for approval</Button>
              )}
              {a.status === 'PENDING_APPROVAL' && isApprover && (
                <>
                  <TextField size="small" label="Reason (audited)" value={reason[a.id] ?? ''}
                             onChange={(e) => setReason((r) => ({ ...r, [a.id]: e.target.value }))} sx={{ minWidth: 260 }} />
                  <Button variant="contained" color="success" disabled={a.submittedBy === user || a.approvals?.includes(user)}
                          onClick={() => act.mutate({ id: a.id, op: 'approve' })}>Approve and run</Button>
                </>
              )}
              {isResponder && <Button color="error" onClick={() => act.mutate({ id: a.id, op: 'reject' })}>Reject</Button>}
              {a.status === 'PENDING_APPROVAL' && a.submittedBy === user && (
                <Typography variant="caption" color="warning.main">You sent this for approval, so someone else must approve it.</Typography>
              )}
            </Stack>
          )}
        </Box>
      ))}
    </Panel>
  );
}

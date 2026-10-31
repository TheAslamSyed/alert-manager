import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Chip, Stack, TextField, Typography } from '@mui/material';
import { api } from '../api';
import { Empty, ErrorText, Panel, StatusChip, dateTime } from './ui';

/** FR-07: drafts per audience. Responders can edit; only approvers can send. */
export default function CommsPanel({ incidentId, roles }: { incidentId: string; roles: string[] }) {
  const qc = useQueryClient();
  const drafts = useQuery({ queryKey: ['comms', incidentId], queryFn: () => api.get<any[]>(`/incidents/${incidentId}/comms`) });
  const [editing, setEditing] = useState<Record<string, { subject: string; body: string }>>({});
  const save = useMutation({
    mutationFn: ({ id, subject, body }: { id: string; subject: string; body: string }) => api.put(`/comms/${id}`, { subject, body }),
    onSuccess: (_d, v) => { setEditing((e) => { const n = { ...e }; delete n[v.id]; return n; }); qc.invalidateQueries({ queryKey: ['comms'] }); },
  });
  const send = useMutation({
    mutationFn: (id: string) => api.post(`/comms/${id}/send`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comms'] }),
  });
  const canEdit = roles.includes('RESPONDER') || roles.includes('APPROVER');
  const canSend = roles.includes('APPROVER');
  const current = (drafts.data ?? []).filter((d) => d.status === 'DRAFT');
  const sent = (drafts.data ?? []).filter((d) => d.status === 'SENT');

  return (
    <Stack sx={{ gap: 2 }}>
      <ErrorText error={save.error || send.error} />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
        {current.length === 0 && <Panel sx={{ flex: 1 }}><Empty>No drafts waiting. The communication agent writes them when the root cause is found and when the status changes.</Empty></Panel>}
        {current.map((d) => {
          const e = editing[d.id];
          return (
            <Panel key={d.id} title={d.audience.charAt(0) + d.audience.slice(1).toLowerCase()} sx={{ flex: '1 1 300px' }}
                   right={<Stack direction="row" sx={{ gap: 0.5 }}><Chip size="small" label={d.method.toLowerCase()} /><StatusChip value={d.status} /></Stack>}>
              <Box sx={{ p: 2.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{d.channel} · trigger {d.trigger.toLowerCase()}</Typography>
                {e ? (
                  <>
                    <TextField size="small" label="Subject" value={e.subject} onChange={(ev) => setEditing((x) => ({ ...x, [d.id]: { ...e, subject: ev.target.value } }))} />
                    <TextField multiline minRows={5} label="Message" value={e.body} onChange={(ev) => setEditing((x) => ({ ...x, [d.id]: { ...e, body: ev.target.value } }))} />
                  </>
                ) : (
                  <>
                    <Typography sx={{ fontWeight: 500 }}>{d.subject}</Typography>
                    <Typography sx={{ fontSize: 14, whiteSpace: 'pre-line', color: '#C9D3DD' }}>{d.body}</Typography>
                  </>
                )}
                <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap' }}>
                  {canSend && !e && <Button variant="contained" onClick={() => send.mutate(d.id)}>Approve and send</Button>}
                  {canEdit && !e && <Button variant="outlined" onClick={() => setEditing((x) => ({ ...x, [d.id]: { subject: d.subject, body: d.body } }))}>Edit</Button>}
                  {e && <Button variant="contained" onClick={() => save.mutate({ id: d.id, ...e })}>Save</Button>}
                  {e && <Button onClick={() => setEditing((x) => { const n = { ...x }; delete n[d.id]; return n; })}>Cancel</Button>}
                  {!canSend && <Typography variant="caption" color="text.secondary">An approver sends messages.</Typography>}
                </Stack>
              </Box>
            </Panel>
          );
        })}
      </Box>
      <Panel title="Sent updates" right={<Typography variant="caption" color="text.secondary">Every send is in the audit log</Typography>}>
        {sent.length === 0 && <Empty>Nothing sent yet.</Empty>}
        {sent.map((d) => (
          <Stack key={d.id} direction="row" sx={{ px: 2.5, py: 1.25, borderTop: 1, borderColor: 'divider', gap: 2, flexWrap: 'wrap', fontSize: 14 }}>
            <Typography variant="caption" color="text.secondary" sx={{ minWidth: 150 }}>{dateTime(d.sentAt)}</Typography>
            <Typography sx={{ minWidth: 110, fontSize: 14 }}>{d.audience.toLowerCase()}</Typography>
            <Typography sx={{ flex: 1, fontSize: 14 }}>{d.subject}</Typography>
            <Typography variant="caption" color="text.secondary">sent by {d.sentBy}</Typography>
          </Stack>
        ))}
      </Panel>
    </Stack>
  );
}

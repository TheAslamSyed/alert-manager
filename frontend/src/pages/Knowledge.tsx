import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, Stack, TextField, Typography } from '@mui/material';
import { api } from '../api';
import { Empty, ErrorText, Mono, PageHeader, Panel, StatusChip } from '../components/ui';

/** FR-05 search and FR-10 postmortem review. */
export default function Knowledge({ roles }: { roles: string[] }) {
  const qc = useQueryClient();
  const [q, setQ] = useState('transfers failing, connection pool full after release');
  const [query, setQuery] = useState(q);
  const search = useQuery({ queryKey: ['kb-search', query], queryFn: () => api.get(`/knowledge/search?q=${encodeURIComponent(query)}&k=5`) });
  const drafts = useQuery({ queryKey: ['kb-drafts'], queryFn: () => api.get<any[]>('/knowledge/articles?status=IN_REVIEW') });
  const publish = useMutation({
    mutationFn: (id: string) => api.post(`/knowledge/articles/${id}/publish`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['kb-drafts'] }),
  });
  const canPublish = roles.includes('RESPONDER') || roles.includes('APPROVER');

  return (
    <>
      <PageHeader title="Knowledge base" subtitle="Past incidents, runbooks and reviewed postmortems · search by meaning and keywords together" />
      <Stack component="form" direction="row" sx={{ gap: 1, mb: 2, flexWrap: 'wrap' }}
             onSubmit={(e) => { e.preventDefault(); setQuery(q); }}>
        <TextField label="Search past incidents and runbooks" value={q} onChange={(e) => setQ(e.target.value)} sx={{ flex: '1 1 420px' }} />
        <Button type="submit" variant="contained">Search</Button>
      </Stack>
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Panel title="Similar incidents" sx={{ flex: '1 1 440px' }}
               right={search.data?.degraded && <Chip size="small" color="warning" label="keyword-only (embeddings down)" />}>
          <ErrorText error={search.error} />
          {(search.data?.hits ?? []).length === 0 && <Empty>No matches.</Empty>}
          {(search.data?.hits ?? []).map((h: any) => (
            <Box key={h.articleId} sx={{ px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1 }}>
                <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}><Mono>{h.ref}</Mono><Chip size="small" label={h.kind.replace('_', ' ').toLowerCase()} /></Stack>
                <Typography sx={{ fontWeight: 500, color: h.similarity >= 0.75 ? 'success.main' : 'warning.main' }}>{Math.round(h.similarity * 100)}% · {h.matchedBy}</Typography>
              </Stack>
              <Typography sx={{ fontSize: 14, fontWeight: 500 }}>{h.title}</Typography>
              <Typography variant="body2" color="text.secondary">Fix: {h.resolution}</Typography>
              {h.mttrMinutes && <Typography variant="caption" color="text.secondary">Resolved in {h.mttrMinutes} min</Typography>}
            </Box>
          ))}
        </Panel>
        <Panel title="Postmortems waiting for review" sx={{ flex: '1.2 1 520px' }}>
          <ErrorText error={publish.error} />
          {(drafts.data ?? []).length === 0 && <Empty>No drafts. One is written automatically when an incident is resolved.</Empty>}
          {(drafts.data ?? []).map((a) => (
            <Box key={a.id} sx={{ px: 2.5, py: 2, borderTop: 1, borderColor: 'divider' }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1, mb: 1 }}>
                <Typography sx={{ fontWeight: 500 }}>{a.title}</Typography><StatusChip value={a.status} />
              </Stack>
              <Typography sx={{ fontSize: 14, whiteSpace: 'pre-line', color: '#C9D3DD' }}>{a.body?.replace(/^## /gm, '')}</Typography>
              {canPublish && <Button sx={{ mt: 1.5 }} variant="contained" onClick={() => publish.mutate(a.id)}>Publish to knowledge base</Button>}
            </Box>
          ))}
          {publish.isSuccess && <Alert severity="success" sx={{ m: 2 }}>Published. It becomes searchable within 30 seconds.</Alert>}
        </Panel>
      </Box>
    </>
  );
}

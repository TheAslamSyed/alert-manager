import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { FormControl, InputLabel, MenuItem, Select } from '@mui/material';
import { api } from '../api';
import CommsPanel from '../components/CommsPanel';
import { Empty, PageHeader } from '../components/ui';

/** FR-07: stakeholder drafts for executives, engineers and customers. */
export default function Communications({ roles }: { roles: string[] }) {
  const incidents = useQuery({ queryKey: ['incidents'], queryFn: () => api.get<any[]>('/incidents?limit=50') });
  const [id, setId] = useState('');
  useEffect(() => {
    if (!id && incidents.data?.length) setId(incidents.data[0].id);
  }, [id, incidents.data]);

  return (
    <>
      <PageHeader title="Communications" subtitle="Drafts written by the communication agent. Nothing is sent until an approver sends it."
        actions={
          <FormControl size="small" sx={{ minWidth: 320 }}>
            <InputLabel id="inc">Incident</InputLabel>
            <Select labelId="inc" label="Incident" value={id} onChange={(e) => setId(e.target.value)}>
              {(incidents.data ?? []).map((i) => <MenuItem key={i.id} value={i.id}>{i.id} · {i.title}</MenuItem>)}
            </Select>
          </FormControl>
        } />
      {id ? <CommsPanel incidentId={id} roles={roles} /> : <Empty>No incidents yet.</Empty>}
    </>
  );
}

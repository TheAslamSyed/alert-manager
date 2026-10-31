import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { getAccessToken } from './api';

export type LiveEvent = {
  type: string;
  incidentId: string;
  status: string;
  severity: string;
  title: string;
  message: string;
  at: string;
};

/**
 * Subscribes to incident-service's SSE stream (via the gateway) with the login token,
 * keeps the last events for the activity feed and refreshes the affected queries.
 */
export function useLiveEvents(enabled: boolean) {
  const queryClient = useQueryClient();
  const [events, setEvents] = useState<LiveEvent[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!enabled) return;
    const ctrl = new AbortController();
    fetchEventSource('/api/incidents/stream', {
      signal: ctrl.signal,
      headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` },
      openWhenHidden: true,
      onopen: async () => setConnected(true),
      onmessage: (msg) => {
        if (!msg.data || msg.event === 'connected') return;
        try {
          const e = JSON.parse(msg.data) as LiveEvent;
          setEvents((prev) => [e, ...prev].slice(0, 30));
          queryClient.invalidateQueries({ queryKey: ['incidents'] });
          queryClient.invalidateQueries({ queryKey: ['incident', e.incidentId] });
          queryClient.invalidateQueries({ queryKey: ['remediations'] });
          queryClient.invalidateQueries({ queryKey: ['comms'] });
        } catch { /* keep-alive or non-JSON */ }
      },
      onclose: () => setConnected(false),
      onerror: () => {
        setConnected(false);
        return 5000; // retry after 5 s
      },
    });
    return () => ctrl.abort();
  }, [enabled, queryClient]);

  return { events, connected };
}

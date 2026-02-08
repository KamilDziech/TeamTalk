/**
 * useClients Hook
 *
 * Custom hook for fetching clients from Supabase
 */

import { useState, useEffect } from 'react';
import { supabase } from '@/api/supabaseClient';
import type { Client } from '@/types';

export const useClients = () => {
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchClients();
  }, []);

  const fetchClients = async () => {
    try {
      setLoading(true);
      setError(null);

      // Fetch only clients that have at least one completed call
      // This ensures Historia shows only clients with call history (completed calls)
      const { data: completedCallLogs, error: callLogsError } = await supabase
        .from('call_logs')
        .select('client_id')
        .eq('status', 'completed')
        .not('client_id', 'is', null);

      if (callLogsError) {
        throw callLogsError;
      }

      // Get unique client IDs
      const clientIds = [...new Set(
        completedCallLogs?.map((log) => log.client_id).filter((id): id is string => id !== null) || []
      )];

      console.log('👥 Historia: Found', clientIds.length, 'clients with completed calls');

      if (clientIds.length === 0) {
        setClients([]);
        return;
      }

      // Fetch client details
      const { data, error: fetchError } = await supabase
        .from('clients')
        .select('*')
        .in('id', clientIds)
        .order('created_at', { ascending: false });

      if (fetchError) {
        throw fetchError;
      }

      setClients(data || []);
    } catch (err) {
      console.error('Error fetching clients:', err);
      setError(err instanceof Error ? err.message : 'Nieznany błąd');
    } finally {
      setLoading(false);
    }
  };

  const deleteClient = async (clientId: string): Promise<boolean> => {
    try {
      const { error: deleteError } = await supabase
        .from('clients')
        .delete()
        .eq('id', clientId);

      if (deleteError) {
        throw deleteError;
      }

      // Remove from local state
      setClients((prev) => prev.filter((c) => c.id !== clientId));
      return true;
    } catch (err) {
      console.error('Error deleting client:', err);
      setError(err instanceof Error ? err.message : 'Błąd usuwania');
      return false;
    }
  };

  const updateClient = async (
    clientId: string,
    updates: { address?: string | null; notes?: string | null }
  ): Promise<Client | null> => {
    try {
      const { data, error: updateError } = await supabase
        .from('clients')
        .update(updates)
        .eq('id', clientId)
        .select()
        .single();

      if (updateError) {
        throw updateError;
      }

      // Update local state
      setClients((prev) =>
        prev.map((c) => (c.id === clientId ? { ...c, ...data } : c))
      );

      return data;
    } catch (err) {
      console.error('Error updating client:', err);
      setError(err instanceof Error ? err.message : 'Błąd aktualizacji');
      return null;
    }
  };

  return { clients, loading, error, refetch: fetchClients, deleteClient, updateClient }
};

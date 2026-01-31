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

      const { data, error: fetchError } = await supabase
        .from('clients')
        .select('*')
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

  return { clients, loading, error, refetch: fetchClients, deleteClient }
};

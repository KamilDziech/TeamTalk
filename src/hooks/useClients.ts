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

  return { clients, loading, error, refetch: fetchClients };
};

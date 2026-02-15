/**
 * AdminService
 *
 * Handles admin-related functionality:
 * - Check if current user is admin
 * - Admin users' calls are not scanned
 * - Admin can monitor app without affecting client data
 */

import { supabase } from '@/api/supabaseClient';
import AsyncStorage from '@react-native-async-storage/async-storage';

const ADMIN_CACHE_KEY = 'admin_status_cache';
const CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

interface AdminCache {
  isAdmin: boolean;
  userId: string;
  timestamp: number;
}

class AdminService {
  private cache: AdminCache | null = null;

  /**
   * Check if current user is an admin
   * Uses cache to avoid repeated database queries
   */
  async isCurrentUserAdmin(): Promise<boolean> {
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return false;

      // Check cache first
      if (this.cache &&
          this.cache.userId === user.id &&
          Date.now() - this.cache.timestamp < CACHE_DURATION_MS) {
        return this.cache.isAdmin;
      }

      // Check AsyncStorage cache
      const stored = await AsyncStorage.getItem(ADMIN_CACHE_KEY);
      if (stored) {
        const parsed: AdminCache = JSON.parse(stored);
        if (parsed.userId === user.id &&
            Date.now() - parsed.timestamp < CACHE_DURATION_MS) {
          this.cache = parsed;
          return parsed.isAdmin;
        }
      }

      // Fetch from database
      const { data: profile, error } = await supabase
        .from('profiles')
        .select('is_admin')
        .eq('id', user.id)
        .single();

      if (error) {
        console.error('Error fetching admin status:', error);
        return false;
      }

      const isAdmin = profile?.is_admin === true;

      // Update cache
      this.cache = {
        isAdmin,
        userId: user.id,
        timestamp: Date.now(),
      };
      await AsyncStorage.setItem(ADMIN_CACHE_KEY, JSON.stringify(this.cache));

      return isAdmin;
    } catch (error) {
      console.error('Error in isCurrentUserAdmin:', error);
      return false;
    }
  }

  /**
   * Clear admin cache (call on logout)
   */
  async clearCache(): Promise<void> {
    this.cache = null;
    await AsyncStorage.removeItem(ADMIN_CACHE_KEY);
  }

  /**
   * Get admin user IDs (for filtering queries)
   * Returns list of user IDs that are admins
   */
  async getAdminUserIds(): Promise<string[]> {
    try {
      const { data, error } = await supabase
        .from('profiles')
        .select('id')
        .eq('is_admin', true);

      if (error) {
        console.error('Error fetching admin user IDs:', error);
        return [];
      }

      return data?.map(p => p.id) || [];
    } catch (error) {
      console.error('Error in getAdminUserIds:', error);
      return [];
    }
  }
}

export const adminService = new AdminService();

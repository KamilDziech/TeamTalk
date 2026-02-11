/**
 * Supabase Client Configuration
 *
 * Initializes and exports a configured Supabase client instance
 * Uses environment variables for secure credential management
 *
 * Environment variables required:
 * - SUPABASE_URL: Your Supabase project URL
 * - SUPABASE_ANON_KEY: Your Supabase anonymous key
 */

import { createClient } from '@supabase/supabase-js';
import Constants from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { Database } from '@/types';

// Retrieve environment variables
export const supabaseUrl = Constants.expoConfig?.extra?.supabaseUrl || process.env.SUPABASE_URL;
const supabaseAnonKey = Constants.expoConfig?.extra?.supabaseAnonKey || process.env.SUPABASE_ANON_KEY;

// Debug logging
console.log('🔧 Supabase Config:', {
  urlSet: !!supabaseUrl,
  keySet: !!supabaseAnonKey,
  urlPreview: supabaseUrl ? supabaseUrl.substring(0, 30) + '...' : 'NOT SET',
});

// Validate environment variables
if (!supabaseUrl) {
  console.error('❌ SUPABASE_URL is not defined!');
  throw new Error(
    'SUPABASE_URL is not defined. Please set it in your .env file or app.config.js'
  );
}

if (!supabaseAnonKey) {
  console.error('❌ SUPABASE_ANON_KEY is not defined!');
  throw new Error(
    'SUPABASE_ANON_KEY is not defined. Please set it in your .env file or app.config.js'
  );
}

/**
 * Supabase client instance
 * Configured with proper TypeScript types and auth settings
 * Uses AsyncStorage for session persistence across app restarts
 */
export const supabase = createClient<Database>(supabaseUrl, supabaseAnonKey, {
  auth: {
    // Use AsyncStorage for session persistence
    storage: AsyncStorage,
    // Auto refresh token before it expires
    autoRefreshToken: true,
    // Persist session in async storage
    persistSession: true,
    // Detect session from URL (useful for magic links)
    detectSessionInUrl: false,
  },
  // Realtime configuration
  realtime: {
    // Reconnect automatically on connection loss
    params: {
      eventsPerSecond: 10,
    },
  },
});

/**
 * Helper function to check if Supabase client is properly initialized
 */
export const isSupabaseConfigured = (): boolean => {
  return !!(supabaseUrl && supabaseAnonKey);
};

/**
 * Get the current session
 */
export const getCurrentSession = async () => {
  const { data, error } = await supabase.auth.getSession();
  if (error) {
    console.error('Error getting session:', error);
    return null;
  }
  return data.session;
};

/**
 * Sign in with email and password
 */
export const signIn = async (email: string, password: string) => {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password,
  });
  if (error) {
    throw error;
  }
  return data;
};

/**
 * Sign up with email, password, and display name
 */
export const signUp = async (email: string, password: string, displayName: string) => {
  const { data, error } = await supabase.auth.signUp({
    email,
    password,
    options: {
      data: {
        display_name: displayName,
      },
    },
  });
  if (error) {
    throw error;
  }
  return data;
};

/**
 * Sign out current user and clear session
 */
export const signOut = async () => {
  const { error } = await supabase.auth.signOut();
  if (error) {
    throw error;
  }
};

/**
 * Get current user
 */
export const getCurrentUser = async () => {
  const { data: { user }, error } = await supabase.auth.getUser();
  if (error) {
    console.error('Error getting user:', error);
    return null;
  }
  return user;
};

/**
 * Get user profile from profiles table
 */
export const getUserProfile = async (userId: string) => {
  const { data, error } = await supabase
    .from('profiles')
    .select('*')
    .eq('id', userId)
    .single();

  if (error) {
    console.error('Error getting profile:', error);
    return null;
  }
  return data;
};

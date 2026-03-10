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

// Debug logging (only in development)
if (__DEV__) {
  console.log('🔧 Supabase Config:', {
    urlSet: !!supabaseUrl,
    keySet: !!supabaseAnonKey,
    urlPreview: supabaseUrl ? supabaseUrl.substring(0, 30) + '...' : 'NOT SET',
  });
}

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

// Custom fetch wrapper: logs HTTP-level details and forces new TCP connections.
// 'Connection: close' prevents OkHttp from reusing stale pooled connections that
// the server has silently closed after ~1h idle — without this, REST requests can
// hang for 30s+ until OkHttp finally detects the broken connection.
const customFetch = async (url: RequestInfo | URL, options?: RequestInit): Promise<Response> => {
  const urlStr =
    typeof url === 'string' ? url :
    url instanceof URL ? url.toString() :
    (url as Request).url;
  const path = urlStr.replace(supabaseUrl ?? '', '').split('?')[0].substring(0, 60);
  const method = options?.method ?? 'GET';
  const t0 = Date.now();
  try {
    // options.headers may be a Headers instance, plain object, or array of tuples.
    // Using the Headers constructor handles all cases correctly without losing entries.
    const mergedHeaders = new Headers(options?.headers);
    mergedHeaders.set('Connection', 'close');

    if (__DEV__) {
      const isAuthEndpoint = urlStr.includes('/auth/');
      const hasAuthHeader = mergedHeaders.has('Authorization');
      // For non-auth REST requests missing an Authorization header, warn loudly —
      // this is the smoking gun for "fetches hang because token not yet available".
      if (!isAuthEndpoint && !hasAuthHeader) {
        console.warn(`🌐 fetch → ${method} ${path} — ⚠️ BRAK TOKENU AUTH (brak nagłówka Authorization)`);
      } else {
        console.log(`🌐 fetch → ${method} ${path}${isAuthEndpoint ? ' [auth]' : ''}`);
      }
    }

    const response = await fetch(url, {
      ...options,
      headers: mergedHeaders,
    });
    if (__DEV__) console.log(`🌐 fetch ← ${response.status} ${path} (${Date.now() - t0}ms)`);
    return response;
  } catch (error: any) {
    if (__DEV__) console.error(`🌐 fetch ✗ ${path} (${Date.now() - t0}ms): ${error?.name} — ${error?.message}`);
    throw error;
  }
};

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
  global: {
    // Use custom fetch to force new TCP connections and log HTTP details
    fetch: customFetch,
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
    if (__DEV__) console.error('Error getting session:', error);
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
    if (__DEV__) console.error('Error getting user:', error);
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
    if (__DEV__) console.error('Error getting profile:', error);
    return null;
  }
  return data;
};

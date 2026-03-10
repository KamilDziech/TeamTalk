/**
 * AuthContext
 *
 * Provides authentication state and user profile across the app.
 * Handles session persistence and auto-login.
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { Session, User } from '@supabase/supabase-js';
import { supabase, signOut as supabaseSignOut } from '@/api/supabaseClient';
import type { Profile } from '@/types';

interface AuthContextType {
  session: Session | null;
  user: User | null;
  profile: Profile | null;
  loading: boolean;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    console.log('🔄 AuthContext: Starting session initialization...');

    // Do NOT call supabase.auth.getSession() here.
    //
    // Root cause of the infinite-loading bug:
    // When the JWT is expired on cold start, getSession() triggers a network
    // refresh internally. The SDK creates an internal _refreshingDeferred promise.
    // After the refresh completes, TOKEN_REFRESHED fires via onAuthStateChange —
    // but the _refreshingDeferred is never resolved (Supabase JS SDK v2 bug).
    // Any subsequent supabase.from(...).select(...) call also calls getSession()
    // internally and queues behind the orphaned deferred → all REST requests hang
    // forever → Android kills the process after ~60s (signal 9).
    //
    // Fix: Let onAuthStateChange handle everything. The SDK fires:
    //   INITIAL_SESSION  — token was already valid (fast path, <10ms)
    //   TOKEN_REFRESHED  — token was expired and has been refreshed (~700ms)
    //   (no event)       — no session at all → INITIAL_SESSION with null session
    // In all cases the SDK's internal deferred resolves correctly because no
    // external getSession() call is competing with the internal refresh.

    let sessionResolved = false;
    const initT0 = Date.now();

    // Safety net: in case onAuthStateChange never fires (e.g. network completely dead)
    const safetyTimeout = setTimeout(() => {
      if (!sessionResolved) {
        console.warn(`⚠️ AuthContext: SAFETY_TIMEOUT after 10s — setLoading(false)`);
        sessionResolved = true;
        setLoading(false);
      }
    }, 10000);

    // Listen for auth changes — this is the sole source of truth for session state
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      async (event, session) => {
        const tokenExpiry = session?.expires_at
          ? new Date(session.expires_at * 1000).toISOString()
          : 'brak';
        const expiresInMin = session?.expires_at
          ? Math.round((session.expires_at * 1000 - Date.now()) / 60000)
          : null;
        console.log(
          `🔑 Auth state changed: ${event} | token wygasa: ${tokenExpiry}${expiresInMin !== null ? ` (za ${expiresInMin} min)` : ''}`
        );
        setSession(session);
        setUser(session?.user ?? null);

        // Unblock loading on any event that signals the auth state is settled.
        // TOKEN_REFRESHED is now safe to use here because we are NOT calling
        // getSession() ourselves — so the SDK's internal deferred resolves
        // cleanly before REST requests begin.
        if (event === 'INITIAL_SESSION' || event === 'TOKEN_REFRESHED' || event === 'SIGNED_IN') {
          if (!sessionResolved) {
            console.log(`✅ AuthContext: setLoading(false) via ${event} (t=${Date.now() - initT0}ms)`);
            sessionResolved = true;
            clearTimeout(safetyTimeout);
            setLoading(false);
          }
        }

        if (session?.user) {
          // Do NOT await fetchProfile here. The onAuthStateChange callback is
          // awaited by the SDK (_notifyAllSubscribers awaits x.callback). Any
          // Supabase REST call inside fetchProfile internally calls getSession(),
          // which waits for refreshingDeferred to resolve. But refreshingDeferred
          // is only resolved AFTER _notifyAllSubscribers returns — so awaiting
          // fetchProfile here creates a circular deadlock on TOKEN_REFRESHED.
          // Fire-and-forget: fetchProfile runs after the callback returns and
          // refreshingDeferred is resolved.
          fetchProfile(session.user.id).catch(err => console.error('Error fetching profile:', err));
        } else {
          setProfile(null);
        }
      }
    );

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  const fetchProfile = async (userId: string) => {
    try {
      // Wait a bit for trigger to complete
      await new Promise(resolve => setTimeout(resolve, 500));

      const { data, error } = await supabase
        .from('profiles')
        .select('*')
        .eq('id', userId)
        .single();

      if (error) {
        // Profile might not exist yet (trigger hasn't run)
        console.log('Profile not found, will be created by trigger or manually');

        // Get current user to access metadata
        const { data: { user: currentUser } } = await supabase.auth.getUser();
        const displayName = currentUser?.user_metadata?.display_name || 'Użytkownik';

        // Try to create profile manually
        const { data: newProfile, error: insertError } = await supabase
          .from('profiles')
          .upsert({
            id: userId,
            display_name: displayName,
            email: currentUser?.email || null
          } as any)
          .select()
          .single();

        if (!insertError && newProfile) {
          setProfile(newProfile);
        } else {
          console.error('Error creating profile:', insertError);
        }
        return;
      }

      setProfile(data);
    } catch (error) {
      console.error('Error fetching profile:', error);
    }
  };

  const refreshProfile = async () => {
    if (user) {
      await fetchProfile(user.id);
    }
  };

  const signOut = async () => {
    try {
      await supabaseSignOut();
      setSession(null);
      setUser(null);
      setProfile(null);
    } catch (error) {
      console.error('Error signing out:', error);
      throw error;
    }
  };

  return (
    <AuthContext.Provider
      value={{
        session,
        user,
        profile,
        loading,
        signOut,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

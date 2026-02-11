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

    // Safety timeout - if session check takes too long, stop loading anyway
    const safetyTimeout = setTimeout(() => {
      console.warn('⚠️ AuthContext: Session check timed out after 10s');
      if (loading) {
        setLoading(false);
      }
    }, 10000);

    // Get initial session
    supabase.auth.getSession()
      .then(({ data: { session } }) => {
        console.log('✅ AuthContext: getSession completed, session:', session ? 'exists' : 'null');
        setSession(session);
        setUser(session?.user ?? null);
        if (session?.user) {
          fetchProfile(session.user.id);
        }
      })
      .catch((error) => {
        console.error('❌ AuthContext: Error getting session:', error);
      })
      .finally(() => {
        console.log('✅ AuthContext: Setting loading to false');
        clearTimeout(safetyTimeout);
        setLoading(false);
      });

    // Listen for auth changes
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      async (event, session) => {
        console.log('Auth state changed:', event);
        setSession(session);
        setUser(session?.user ?? null);

        if (session?.user) {
          await fetchProfile(session.user.id);
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

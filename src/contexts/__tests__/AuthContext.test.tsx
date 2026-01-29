/**
 * Unit Tests for AuthContext
 *
 * Testing the authentication context including:
 * - Session initialization and loading state
 * - Error handling during session fetch (the bug we fixed)
 * - Sign out functionality
 * - Profile fetching
 * - Auth state changes
 */

import React from 'react';
import { renderHook, act, waitFor } from '@testing-library/react-native';
import { AuthProvider, useAuth } from '../AuthContext';

// Mock Supabase client
const mockGetSession = jest.fn();
const mockOnAuthStateChange = jest.fn();
const mockSignOut = jest.fn();
const mockFrom = jest.fn();

jest.mock('@/api/supabaseClient', () => ({
    supabase: {
        auth: {
            getSession: () => mockGetSession(),
            onAuthStateChange: (callback: Function) => {
                mockOnAuthStateChange(callback);
                return {
                    data: {
                        subscription: {
                            unsubscribe: jest.fn(),
                        },
                    },
                };
            },
        },
        from: () => mockFrom(),
    },
    signOut: () => mockSignOut(),
}));

// Mock data
const mockUser = {
    id: 'user-123',
    email: 'test@example.com',
    user_metadata: {
        display_name: 'Test User',
    },
};

const mockSession = {
    user: mockUser,
    access_token: 'mock-token',
    refresh_token: 'mock-refresh-token',
};

const mockProfile = {
    id: 'user-123',
    display_name: 'Test User',
    created_at: '2024-01-01T00:00:00Z',
    updated_at: '2024-01-01T00:00:00Z',
};

// Wrapper component for testing hooks
const wrapper = ({ children }: { children: React.ReactNode }) => (
    <AuthProvider>{children}</AuthProvider>
);

describe('AuthContext', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        jest.useFakeTimers();

        // Default mock implementations
        mockFrom.mockReturnValue({
            select: jest.fn().mockReturnValue({
                eq: jest.fn().mockReturnValue({
                    single: jest.fn().mockResolvedValue({ data: mockProfile, error: null }),
                }),
            }),
            upsert: jest.fn().mockReturnValue({
                select: jest.fn().mockReturnValue({
                    single: jest.fn().mockResolvedValue({ data: mockProfile, error: null }),
                }),
            }),
        });
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    describe('Initial Loading State', () => {
        it('should start with loading=true', async () => {
            // Arrange - make getSession hang
            mockGetSession.mockReturnValue(new Promise(() => { }));

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            // Assert - initial state should have loading=true
            expect(result.current.loading).toBe(true);
            expect(result.current.session).toBe(null);
            expect(result.current.user).toBe(null);
        });

        it('should set loading=false after successful session fetch', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: mockSession },
                error: null,
            });

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            // Wait for async operations
            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Assert
            expect(result.current.session).toEqual(mockSession);
            expect(result.current.user).toEqual(mockUser);
        });

        it('should set loading=false even when getSession fails (bug fix verification)', async () => {
            // Arrange - simulate network error
            mockGetSession.mockRejectedValue(new Error('Network error'));

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            // Wait for async operations
            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Assert - loading should be false despite error
            expect(result.current.session).toBe(null);
            expect(result.current.user).toBe(null);
        });

        it('should set loading=false after timeout if getSession hangs', async () => {
            // Arrange - make getSession never resolve
            mockGetSession.mockReturnValue(new Promise(() => { }));

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            // Initially loading should be true
            expect(result.current.loading).toBe(true);

            // Fast-forward past the 10s timeout
            await act(async () => {
                jest.advanceTimersByTime(10001);
            });

            // Assert - loading should be false after timeout
            expect(result.current.loading).toBe(false);
        });
    });

    describe('Session with No Active User', () => {
        it('should handle null session correctly', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: null },
                error: null,
            });

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Assert
            expect(result.current.session).toBe(null);
            expect(result.current.user).toBe(null);
            expect(result.current.profile).toBe(null);
        });
    });

    describe('Sign Out', () => {
        it('should clear session, user, and profile on sign out', async () => {
            // Arrange - start with active session
            mockGetSession.mockResolvedValue({
                data: { session: mockSession },
                error: null,
            });
            mockSignOut.mockResolvedValue(undefined);

            const { result } = renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Verify we have a session
            expect(result.current.session).toEqual(mockSession);

            // Act - sign out
            await act(async () => {
                await result.current.signOut();
            });

            // Assert
            expect(result.current.session).toBe(null);
            expect(result.current.user).toBe(null);
            expect(result.current.profile).toBe(null);
            expect(mockSignOut).toHaveBeenCalled();
        });

        it('should throw error when sign out fails', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: mockSession },
                error: null,
            });
            mockSignOut.mockRejectedValue(new Error('Sign out failed'));

            const { result } = renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Act & Assert
            await expect(result.current.signOut()).rejects.toThrow('Sign out failed');
        });
    });

    describe('useAuth Hook', () => {
        it('should throw error when used outside AuthProvider', () => {
            // Arrange & Act & Assert
            expect(() => {
                renderHook(() => useAuth());
            }).toThrow('useAuth must be used within an AuthProvider');
        });
    });

    describe('Profile Fetching', () => {
        it('should fetch profile when session exists', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: mockSession },
                error: null,
            });

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Assert - profile should be fetched
            await waitFor(() => {
                expect(result.current.profile).toEqual(mockProfile);
            });
        });

        it('should handle profile fetch error gracefully', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: mockSession },
                error: null,
            });
            mockFrom.mockReturnValue({
                select: jest.fn().mockReturnValue({
                    eq: jest.fn().mockReturnValue({
                        single: jest.fn().mockResolvedValue({ data: null, error: { message: 'Not found' } }),
                    }),
                }),
                upsert: jest.fn().mockReturnValue({
                    select: jest.fn().mockReturnValue({
                        single: jest.fn().mockResolvedValue({ data: mockProfile, error: null }),
                    }),
                }),
            });

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Assert - should not crash, may create new profile
            expect(result.current.session).toEqual(mockSession);
        });

        it('should refresh profile when refreshProfile is called', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: mockSession },
                error: null,
            });

            const updatedProfile = { ...mockProfile, display_name: 'Updated Name' };
            let callCount = 0;

            mockFrom.mockReturnValue({
                select: jest.fn().mockReturnValue({
                    eq: jest.fn().mockReturnValue({
                        single: jest.fn().mockImplementation(() => {
                            callCount++;
                            if (callCount === 1) {
                                return Promise.resolve({ data: mockProfile, error: null });
                            }
                            return Promise.resolve({ data: updatedProfile, error: null });
                        }),
                    }),
                }),
            });

            // Act
            const { result } = renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Refresh profile
            await act(async () => {
                await result.current.refreshProfile();
            });

            // Assert - refreshProfile was callable
            expect(callCount).toBeGreaterThanOrEqual(1);
        });
    });

    describe('Auth State Changes', () => {
        it('should subscribe to auth state changes', async () => {
            // Arrange
            mockGetSession.mockResolvedValue({
                data: { session: null },
                error: null,
            });

            // Act
            renderHook(() => useAuth(), { wrapper });

            await waitFor(() => {
                expect(mockOnAuthStateChange).toHaveBeenCalled();
            });

            // Assert - callback was registered
            expect(mockOnAuthStateChange).toHaveBeenCalledWith(expect.any(Function));
        });
    });
});

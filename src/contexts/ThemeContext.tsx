/**
 * ThemeContext
 *
 * Global theme context providing light/dark mode support.
 * Persists user preference to AsyncStorage.
 * Supports 'light', 'dark', and 'system' modes.
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Theme mode types
export type ThemeMode = 'light' | 'dark' | 'system';

// Color palette interface
export interface ThemeColors {
    // Primary
    primary: string;
    primaryLight: string;
    primaryDark: string;

    // Backgrounds
    background: string;
    surface: string;
    white: string;

    // Borders
    border: string;
    borderLight: string;

    // Text
    textPrimary: string;
    textSecondary: string;
    textTertiary: string;
    textInverse: string;

    // Status
    success: string;
    successLight: string;
    warning: string;
    warningLight: string;
    error: string;
    errorLight: string;
    info: string;
    infoLight: string;

    // Status dots
    dotRed: string;
    dotYellow: string;
    dotGreen: string;
    dotBlue: string;
}

// Light theme colors
const lightColors: ThemeColors = {
    // Primary
    primary: '#3B82F6',
    primaryLight: '#DBEAFE',
    primaryDark: '#1D4ED8',

    // Backgrounds
    background: '#FFFFFF',
    surface: '#F5F5F5',
    white: '#FFFFFF',

    // Borders
    border: '#E0E0E0',
    borderLight: '#F3F4F6',

    // Text
    textPrimary: '#000000',
    textSecondary: '#666666',
    textTertiary: '#9CA3AF',
    textInverse: '#FFFFFF',

    // Status
    success: '#10B981',
    successLight: '#D1FAE5',
    warning: '#F59E0B',
    warningLight: '#FEF3C7',
    error: '#EF4444',
    errorLight: '#FEE2E2',
    info: '#3B82F6',
    infoLight: '#DBEAFE',

    // Status dots
    dotRed: '#EF4444',
    dotYellow: '#F59E0B',
    dotGreen: '#10B981',
    dotBlue: '#3B82F6',
};

// Dark theme colors
const darkColors: ThemeColors = {
    // Primary (same accent color)
    primary: '#3B82F6',
    primaryLight: '#1E3A5F',
    primaryDark: '#60A5FA',

    // Backgrounds
    background: '#121212',
    surface: '#1E1E1E',
    white: '#1E1E1E',

    // Borders
    border: '#333333',
    borderLight: '#2A2A2A',

    // Text
    textPrimary: '#FFFFFF',
    textSecondary: '#AAAAAA',
    textTertiary: '#777777',
    textInverse: '#000000',

    // Status
    success: '#10B981',
    successLight: '#064E3B',
    warning: '#F59E0B',
    warningLight: '#78350F',
    error: '#EF4444',
    errorLight: '#7F1D1D',
    info: '#3B82F6',
    infoLight: '#1E3A5F',

    // Status dots
    dotRed: '#EF4444',
    dotYellow: '#F59E0B',
    dotGreen: '#10B981',
    dotBlue: '#3B82F6',
};

// Theme context interface
interface ThemeContextType {
    mode: ThemeMode;
    isDark: boolean;
    colors: ThemeColors;
    setMode: (mode: ThemeMode) => void;
}

// AsyncStorage key
const THEME_STORAGE_KEY = '@teamtalk_theme_mode';

// Create context
const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

// Theme provider props
interface ThemeProviderProps {
    children: ReactNode;
}

/**
 * ThemeProvider
 *
 * Wraps the app and provides theme context.
 */
export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children }) => {
    const systemColorScheme = useColorScheme();
    const [mode, setModeState] = useState<ThemeMode>('system');
    const [isLoaded, setIsLoaded] = useState(false);

    // Load saved theme preference on mount
    useEffect(() => {
        loadThemePreference();
    }, []);

    const loadThemePreference = async () => {
        try {
            const savedMode = await AsyncStorage.getItem(THEME_STORAGE_KEY);
            if (savedMode && ['light', 'dark', 'system'].includes(savedMode)) {
                setModeState(savedMode as ThemeMode);
            }
        } catch (error) {
            console.warn('Failed to load theme preference:', error);
        } finally {
            setIsLoaded(true);
        }
    };

    // Set and persist theme mode
    const setMode = async (newMode: ThemeMode) => {
        setModeState(newMode);
        try {
            await AsyncStorage.setItem(THEME_STORAGE_KEY, newMode);
            console.log('🎨 Theme preference saved:', newMode);
        } catch (error) {
            console.warn('Failed to save theme preference:', error);
        }
    };

    // Determine if dark mode should be active
    const isDark = mode === 'dark' || (mode === 'system' && systemColorScheme === 'dark');

    // Get current color palette
    const colors = isDark ? darkColors : lightColors;

    const value: ThemeContextType = {
        mode,
        isDark,
        colors,
        setMode,
    };

    // Don't render until theme is loaded to prevent flash
    if (!isLoaded) {
        return null;
    }

    return (
        <ThemeContext.Provider value={value}>
            {children}
        </ThemeContext.Provider>
    );
};

/**
 * useTheme hook
 *
 * Access theme context from any component.
 */
export const useTheme = (): ThemeContextType => {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within a ThemeProvider');
    }
    return context;
};

// Export color palettes for direct access if needed
export { lightColors, darkColors };

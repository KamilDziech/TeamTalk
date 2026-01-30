/**
 * Theme - Centralized design tokens and styles
 *
 * Clean & Modern SaaS Design System
 */

import { StyleSheet } from 'react-native';

// Color Palette
export const colors = {
  // Primary
  primary: '#3B82F6',        // Blue - main actions
  primaryLight: '#DBEAFE',   // Light blue for backgrounds
  primaryDark: '#1D4ED8',    // Dark blue for pressed states

  // Neutrals
  white: '#FFFFFF',
  background: '#F3F4F6',     // Light gray background
  surface: '#FFFFFF',        // Card backgrounds
  border: '#E5E7EB',         // Subtle borders
  borderLight: '#F3F4F6',    // Very light borders

  // Text
  textPrimary: '#111827',    // Almost black
  textSecondary: '#6B7280',  // Gray
  textTertiary: '#9CA3AF',   // Light gray
  textInverse: '#FFFFFF',    // White text on dark bg

  // Status Colors (used sparingly)
  success: '#10B981',        // Green
  successLight: '#D1FAE5',
  warning: '#F59E0B',        // Amber/Yellow
  warningLight: '#FEF3C7',
  error: '#EF4444',          // Red
  errorLight: '#FEE2E2',
  info: '#3B82F6',           // Blue
  infoLight: '#DBEAFE',

  // Status dots
  dotRed: '#EF4444',
  dotYellow: '#F59E0B',
  dotGreen: '#10B981',
  dotBlue: '#3B82F6',
};

// Spacing
export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
};

// Border Radius
export const radius = {
  sm: 6,
  md: 10,
  lg: 14,
  xl: 18,
  xxl: 24,
  full: 9999,
};

// Alias for backward compatibility
export const borderRadius = radius;

// Shadows
export const shadows = {
  none: {
    shadowColor: 'transparent',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0,
    shadowRadius: 0,
    elevation: 0,
  },
  sm: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  md: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 2,
  },
  lg: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
};

// Typography
export const typography = {
  // Sizes
  xs: 11,
  sm: 13,
  base: 15,
  lg: 17,
  xl: 20,
  xxl: 24,
  xxxl: 32,

  // Weights
  normal: '400' as const,
  medium: '500' as const,
  semibold: '600' as const,
  bold: '700' as const,
};

// Common Styles
export const commonStyles = StyleSheet.create({
  // Containers
  screen: {
    flex: 1,
    backgroundColor: colors.background,
  },
  screenWhite: {
    flex: 1,
    backgroundColor: colors.white,
  },
  container: {
    padding: spacing.lg,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  // Cards
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    padding: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  cardCompact: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },

  // Headers
  lightHeader: {
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },

  // Buttons
  buttonPrimary: {
    backgroundColor: colors.primary,
    borderRadius: radius.lg,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xl,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
  },
  buttonPrimaryText: {
    color: colors.textInverse,
    fontSize: typography.base,
    fontWeight: typography.semibold,
  },
  buttonSecondary: {
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xl,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    borderWidth: 1,
    borderColor: colors.border,
  },
  buttonSecondaryText: {
    color: colors.textPrimary,
    fontSize: typography.base,
    fontWeight: typography.semibold,
  },
  buttonSmall: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
  },

  // Status Badges
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusBadge: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    gap: spacing.xs,
  },

  // Inputs
  input: {
    height: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    paddingHorizontal: spacing.lg,
    fontSize: typography.base,
    color: colors.textPrimary,
    backgroundColor: colors.white,
  },
  inputFocused: {
    borderColor: colors.primary,
  },
  inputError: {
    borderColor: colors.error,
    backgroundColor: colors.errorLight,
  },

  // Dividers
  divider: {
    height: 1,
    backgroundColor: colors.border,
  },
  dividerLight: {
    height: 1,
    backgroundColor: colors.borderLight,
  },

  // Row layouts
  row: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
  },
  rowBetween: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    justifyContent: 'space-between' as const,
  },

  // Text styles
  heading: {
    fontSize: typography.xl,
    fontWeight: typography.bold,
    color: colors.textPrimary,
  },
  subheading: {
    fontSize: typography.lg,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
  },
  body: {
    fontSize: typography.base,
    color: colors.textPrimary,
    lineHeight: 22,
  },
  bodySecondary: {
    fontSize: typography.base,
    color: colors.textSecondary,
    lineHeight: 22,
  },
  caption: {
    fontSize: typography.sm,
    color: colors.textTertiary,
  },
  label: {
    fontSize: typography.sm,
    fontWeight: typography.medium,
    color: colors.textSecondary,
    marginBottom: spacing.sm,
  },

  // Empty states
  emptyState: {
    alignItems: 'center' as const,
    paddingVertical: spacing.xxxl,
    paddingHorizontal: spacing.xl,
  },
  emptyStateIcon: {
    fontSize: 48,
    marginBottom: spacing.lg,
  },
  emptyStateTitle: {
    fontSize: typography.lg,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textAlign: 'center' as const,
  },
  emptyStateText: {
    fontSize: typography.base,
    color: colors.textSecondary,
    textAlign: 'center' as const,
    lineHeight: 22,
  },
});

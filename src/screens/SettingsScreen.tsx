/**
 * SettingsScreen
 *
 * Settings tab with theme selection, Dual SIM configuration,
 * push notification toggle, and logout option.
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Alert,
  StyleSheet,
  Switch,
  ActivityIndicator,
  ScrollView,
  Platform,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import Constants from 'expo-constants';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme, ThemeMode } from '@/contexts/ThemeContext';
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { spacing, radius, typography } from '@/styles/theme';
import { simDetectionService } from '@/services/SimDetectionService';

interface ThemeOption {
  mode: ThemeMode;
  label: string;
  icon: string;
}

const THEME_OPTIONS: ThemeOption[] = [
  { mode: 'system', label: 'Systemowy', icon: '🖥️' },
  { mode: 'light', label: 'Jasny', icon: '☀️' },
  { mode: 'dark', label: 'Ciemny', icon: '🌙' },
];

export const SettingsScreen: React.FC = () => {
  const { signOut, profile } = useAuth();
  const { colors, mode, setMode, isDark } = useTheme();
  const styles = createStyles(colors);
  const { notificationsEnabled, toggleNotifications } = usePushNotifications();

  // Dual SIM state
  const [dualSimEnabled, setDualSimEnabled] = useState(false);
  const [detectedSims, setDetectedSims] = useState<{ id: string; displayName: string }[]>([]);
  const [selectedSimId, setSelectedSimId] = useState<string | null>(null);
  const [isLoadingSims, setIsLoadingSims] = useState(false);

  useEffect(() => {
    if (Platform.OS === 'android') {
      loadSimInfo();
    }
  }, []);

  const loadSimInfo = async () => {
    setIsLoadingSims(true);
    try {
      const enabled = await simDetectionService.isDualSimEnabled();
      const businessSimId = await simDetectionService.getBusinessSimId();
      let sims = await simDetectionService.getDetectedSims();

      // If dual SIM is enabled but we have dummy SIM IDs, refresh detection
      if (enabled && sims.some(s => s.id.startsWith('sim_'))) {
        console.log('📱 Detected dummy SIM IDs, refreshing from call log...');
        sims = await simDetectionService.refreshSimDetection();

        // If we got real phoneAccountIds, reset the business SIM selection
        // since the old selection (sim_1/sim_2) is no longer valid
        if (sims.length > 0 && !sims.some(s => s.id.startsWith('sim_'))) {
          await simDetectionService.resetSimSelection();
          setSelectedSimId(null);
        }
      }

      setDualSimEnabled(enabled);
      if (!sims.some(s => s.id.startsWith('sim_'))) {
        // Only keep business SIM ID if it's not a dummy ID
        setSelectedSimId(businessSimId?.startsWith('sim_') ? null : businessSimId);
      } else {
        setSelectedSimId(businessSimId);
      }
      setDetectedSims(sims);
    } catch (error) {
      console.error('Error loading SIM info:', error);
    } finally {
      setIsLoadingSims(false);
    }
  };

  const handleDualSimToggle = async (enabled: boolean) => {
    setDualSimEnabled(enabled);
    await simDetectionService.setDualSimEnabled(enabled);
    if (enabled) {
      // Refresh SIM detection from call log to get real phoneAccountIds
      setIsLoadingSims(true);
      const sims = await simDetectionService.refreshSimDetection();
      setDetectedSims(sims);
      setIsLoadingSims(false);
    } else {
      setSelectedSimId(null);
      await simDetectionService.resetSimSelection();
    }
  };

  const handleSimSelect = async (simId: string) => {
    await simDetectionService.setBusinessSimId(simId);
    setSelectedSimId(simId);
  };

  const handleLogout = () => {
    Alert.alert(
      'Wylogowanie',
      `Czy na pewno chcesz się wylogować${profile ? `, ${profile.display_name}` : ''}?`,
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Wyloguj',
          style: 'destructive',
          onPress: async () => {
            try {
              await signOut();
            } catch (error) {
              Alert.alert('Błąd', 'Nie udało się wylogować. Spróbuj ponownie.');
            }
          },
        },
      ]
    );
  };

  const handleToggleNotifications = async () => {
    const newState = await toggleNotifications();
    if (newState !== null) {
      console.log('📱 Push notifications:', newState ? 'enabled' : 'disabled');
    }
  };

  const handleThemeChange = (newMode: ThemeMode) => {
    setMode(newMode);
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.background} />

      {/* Header */}
      <View style={styles.headerContainer}>
        <Text style={styles.headerTitle}>Ustawienia</Text>
        {profile && (
          <Text style={styles.headerSubtitle}>{profile.display_name}</Text>
        )}
      </View>

      <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
        {/* Theme Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Motyw Aplikacji</Text>
          <View style={styles.themeOptionsContainer}>
            {THEME_OPTIONS.map((option) => (
              <TouchableOpacity
                key={option.mode}
                style={[
                  styles.themeOption,
                  {
                    backgroundColor: mode === option.mode
                      ? colors.primaryLight
                      : colors.surface,
                    borderColor: mode === option.mode
                      ? colors.primary
                      : colors.border,
                  },
                ]}
                onPress={() => handleThemeChange(option.mode)}
              >
                <Text style={styles.themeIcon}>{option.icon}</Text>
                <Text
                  style={[
                    styles.themeLabel,
                    {
                      color: mode === option.mode
                        ? colors.primary
                        : colors.textPrimary,
                      fontWeight: mode === option.mode ? '600' : '400',
                    },
                  ]}
                >
                  {option.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Dual SIM Section */}
        {Platform.OS === 'android' && (
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <MaterialIcons name="sim-card" size={22} color={colors.textPrimary} />
              <Text style={[styles.sectionTitle, { marginLeft: spacing.sm, marginBottom: 0 }]}>
                Dual SIM
              </Text>
            </View>

            {/* Toggle for dual SIM */}
            <View style={styles.settingsRow}>
              <View style={styles.settingsRowLeft}>
                <Text style={styles.settingsLabel}>Mam dwie karty SIM</Text>
                <Text style={styles.settingsDescription}>
                  Włącz jeśli masz kartę służbową i prywatną
                </Text>
              </View>
              <Switch
                value={dualSimEnabled}
                onValueChange={handleDualSimToggle}
                trackColor={{ false: colors.border, true: colors.primary }}
                thumbColor={colors.surface}
              />
            </View>

            {/* SIM selection - only show when dual SIM is enabled */}
            {dualSimEnabled && (
              <>
                <Text style={styles.simSubtitle}>Która karta jest służbowa?</Text>
                {isLoadingSims ? (
                  <ActivityIndicator size="small" color={colors.primary} style={{ marginVertical: spacing.md }} />
                ) : (
                  <View style={styles.simOptionsContainer}>
                    {(detectedSims.length > 0 ? detectedSims : [
                      { id: 'sim_1', displayName: 'SIM 1' },
                      { id: 'sim_2', displayName: 'SIM 2' },
                    ]).map((sim) => (
                      <TouchableOpacity
                        key={sim.id}
                        style={[
                          styles.simOption,
                          {
                            backgroundColor: selectedSimId === sim.id
                              ? colors.primaryLight
                              : colors.surface,
                            borderColor: selectedSimId === sim.id
                              ? colors.primary
                              : colors.border,
                          },
                        ]}
                        onPress={() => handleSimSelect(sim.id)}
                      >
                        <MaterialIcons
                          name={selectedSimId === sim.id ? 'radio-button-checked' : 'radio-button-unchecked'}
                          size={20}
                          color={selectedSimId === sim.id ? colors.primary : colors.textTertiary}
                        />
                        <Text
                          style={[
                            styles.simOptionLabel,
                            {
                              color: selectedSimId === sim.id
                                ? colors.primary
                                : colors.textPrimary,
                              marginLeft: spacing.sm,
                            },
                          ]}
                        >
                          {sim.displayName}
                        </Text>
                        {selectedSimId === sim.id && (
                          <Text style={styles.simBadge}>Służbowa</Text>
                        )}
                      </TouchableOpacity>
                    ))}
                  </View>
                )}
                <Text style={styles.simHint}>
                  Połączenia z drugiej karty będą ignorowane
                </Text>
              </>
            )}
          </View>
        )}

        {/* Push Notifications */}
        <View style={styles.section}>
          <View style={styles.settingsRow}>
            <View style={styles.settingsRowLeft}>
              <MaterialIcons name="notifications" size={22} color={colors.textPrimary} />
              <Text style={styles.settingsRowText}>Powiadomienia Push</Text>
            </View>
            <Switch
              value={notificationsEnabled}
              onValueChange={handleToggleNotifications}
              trackColor={{ false: colors.border, true: colors.primaryLight }}
              thumbColor={notificationsEnabled ? colors.primary : colors.textTertiary}
            />
          </View>
        </View>

        {/* Logout */}
        <View style={styles.section}>
          <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
            <MaterialIcons name="logout" size={22} color={colors.error} />
            <Text style={styles.logoutText}>Wyloguj</Text>
          </TouchableOpacity>
        </View>

        {/* App Version */}
        <View style={styles.versionContainer}>
          <Text style={styles.versionText}>
            Wersja: {Constants.expoConfig?.extra?.buildTime || 'dev'}
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
    safeArea: {
      flex: 1,
      backgroundColor: colors.background,
      paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0,
    },
    headerContainer: {
      backgroundColor: colors.surface,
      paddingHorizontal: spacing.lg,
      paddingVertical: spacing.lg,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    headerTitle: {
      fontSize: typography.xxl,
      fontWeight: typography.bold,
      color: colors.textPrimary,
    },
    headerSubtitle: {
      fontSize: typography.sm,
      color: colors.textTertiary,
      marginTop: spacing.xs,
    },
    container: {
      flex: 1,
    },
    contentContainer: {
      padding: spacing.lg,
      paddingBottom: spacing.xxxl,
    },
    section: {
      backgroundColor: colors.surface,
      borderRadius: radius.lg,
      padding: spacing.lg,
      marginBottom: spacing.lg,
    },
    sectionHeader: {
      flexDirection: 'row',
      alignItems: 'center',
      marginBottom: spacing.sm,
    },
    sectionTitle: {
      fontSize: typography.sm,
      fontWeight: typography.semibold,
      color: colors.textSecondary,
      marginBottom: spacing.md,
    },
    themeOptionsContainer: {
      flexDirection: 'row',
      justifyContent: 'space-between',
    },
    themeOption: {
      flex: 1,
      alignItems: 'center',
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.sm,
      borderRadius: radius.md,
      borderWidth: 2,
      marginHorizontal: spacing.xs,
    },
    themeIcon: {
      fontSize: 24,
      marginBottom: spacing.xs,
    },
    themeLabel: {
      fontSize: typography.sm,
    },
    simSubtitle: {
      fontSize: typography.sm,
      color: colors.textTertiary,
      marginBottom: spacing.md,
    },
    simOptionsContainer: {
      marginTop: spacing.xs,
    },
    simOption: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.md,
      borderRadius: radius.md,
      borderWidth: 2,
      marginBottom: spacing.sm,
    },
    simOptionTextContainer: {
      flex: 1,
      marginLeft: spacing.sm,
    },
    simOptionLabel: {
      fontSize: typography.base,
      fontWeight: typography.medium,
    },
    simOptionId: {
      fontSize: typography.xs,
      color: colors.textTertiary,
      marginTop: 2,
    },
    simBadge: {
      fontSize: typography.xs,
      paddingHorizontal: spacing.sm,
      paddingVertical: 2,
      borderRadius: radius.sm,
      overflow: 'hidden',
      fontWeight: typography.medium,
      backgroundColor: colors.success,
      color: '#fff',
      marginLeft: 'auto',
    },
    simHint: {
      fontSize: typography.xs,
      color: colors.textTertiary,
      marginTop: spacing.sm,
      fontStyle: 'italic',
    },
    resetSimButton: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      marginTop: spacing.sm,
      paddingVertical: spacing.sm,
    },
    resetSimText: {
      fontSize: typography.sm,
      marginLeft: spacing.xs,
      color: colors.textSecondary,
    },
    settingsRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
    },
    settingsRowLeft: {
      flex: 1,
    },
    settingsLabel: {
      fontSize: typography.base,
      color: colors.textPrimary,
    },
    settingsDescription: {
      fontSize: typography.xs,
      color: colors.textTertiary,
      marginTop: 2,
    },
    settingsRowText: {
      fontSize: typography.base,
      marginLeft: spacing.md,
      color: colors.textPrimary,
    },
    logoutButton: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      paddingVertical: spacing.sm,
    },
    logoutText: {
      fontSize: typography.base,
      marginLeft: spacing.md,
      fontWeight: typography.medium,
      color: colors.error,
    },
    versionContainer: {
      alignItems: 'center',
      paddingVertical: spacing.lg,
      marginTop: spacing.md,
    },
    versionText: {
      fontSize: typography.xs,
      color: colors.textTertiary,
    },
  });

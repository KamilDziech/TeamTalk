/**
 * AuthScreen
 *
 * Login and registration screen for user authentication.
 * Features:
 * - Email/password login
 * - New user registration with display name
 * - Form validation with Polish error messages
 * - Toggle between login and register modes
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { signIn, signUp } from '@/api/supabaseClient';
import { colors, spacing, borderRadius, typography, shadows } from '@/styles/theme';

interface AuthScreenProps {
  onAuthSuccess: () => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = ({ onAuthSuccess }) => {
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string; displayName?: string }>({});

  const validateForm = (): boolean => {
    const newErrors: { email?: string; password?: string; displayName?: string } = {};

    // Email validation
    if (!email.trim()) {
      newErrors.email = 'Adres e-mail jest wymagany';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Nieprawidłowy format adresu e-mail';
    }

    // Password validation
    if (!password) {
      newErrors.password = 'Hasło jest wymagane';
    } else if (password.length < 6) {
      newErrors.password = 'Hasło musi mieć minimum 6 znaków';
    }

    // Display name validation (only for registration)
    if (!isLogin && !displayName.trim()) {
      newErrors.displayName = 'Imię jest wymagane';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const getPolishErrorMessage = (error: Error): string => {
    const message = error.message.toLowerCase();

    if (message.includes('invalid login credentials')) {
      return 'Nieprawidłowy e-mail lub hasło';
    }
    if (message.includes('email not confirmed')) {
      return 'Potwierdź swój adres e-mail przed zalogowaniem';
    }
    if (message.includes('user already registered')) {
      return 'Użytkownik o tym adresie e-mail już istnieje';
    }
    if (message.includes('password')) {
      return 'Hasło jest za słabe. Użyj minimum 6 znaków';
    }
    if (message.includes('rate limit')) {
      return 'Zbyt wiele prób. Spróbuj ponownie za chwilę';
    }
    if (message.includes('network')) {
      return 'Błąd połączenia. Sprawdź połączenie z internetem';
    }

    // Show original error for debugging
    console.error('Auth error:', error.message);
    return `Wystąpił błąd: ${error.message}`;
  };

  const handleSubmit = async () => {
    if (!validateForm()) return;

    setLoading(true);

    try {
      if (isLogin) {
        await signIn(email.trim(), password);
        onAuthSuccess();
      } else {
        const result = await signUp(email.trim(), password, displayName.trim());
        console.log('SignUp result:', JSON.stringify(result, null, 2));

        if (result.user) {
          // With email confirmation disabled, user should be logged in immediately
          onAuthSuccess();
        } else if (result.session) {
          // Session exists, user is authenticated
          onAuthSuccess();
        } else {
          Alert.alert(
            'Rejestracja',
            'Konto zostało utworzone. Spróbuj się zalogować.',
            [{ text: 'OK', onPress: () => setIsLogin(true) }]
          );
        }
      }
    } catch (error) {
      console.error('Auth error:', error);
      const errorMessage = getPolishErrorMessage(error as Error);
      Alert.alert('Błąd', errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const toggleMode = () => {
    setIsLogin(!isLogin);
    setErrors({});
  };

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
        >
          {/* Logo / Title */}
          <View style={styles.header}>
            <Text style={styles.logoEmoji}>📞</Text>
            <Text style={styles.title}>TeamTalk</Text>
            <Text style={styles.subtitle}>
              {isLogin ? 'Zaloguj się do aplikacji' : 'Utwórz nowe konto'}
            </Text>
          </View>

          {/* Form */}
          <View style={styles.form}>
            {/* Display Name (registration only) */}
            {!isLogin && (
              <View style={styles.inputContainer}>
                <Text style={styles.label}>Imię</Text>
                <TextInput
                  style={[styles.input, errors.displayName && styles.inputError]}
                  placeholder="Twoje imię..."
                  placeholderTextColor="#999"
                  value={displayName}
                  onChangeText={setDisplayName}
                  autoCapitalize="words"
                  autoComplete="name"
                />
                {errors.displayName && (
                  <Text style={styles.errorText}>{errors.displayName}</Text>
                )}
              </View>
            )}

            {/* Email */}
            <View style={styles.inputContainer}>
              <Text style={styles.label}>Adres e-mail</Text>
              <TextInput
                style={[styles.input, errors.email && styles.inputError]}
                placeholder="email@przykład.pl"
                placeholderTextColor="#999"
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                autoCapitalize="none"
                autoComplete="email"
                autoCorrect={false}
              />
              {errors.email && (
                <Text style={styles.errorText}>{errors.email}</Text>
              )}
            </View>

            {/* Password */}
            <View style={styles.inputContainer}>
              <Text style={styles.label}>Hasło</Text>
              <TextInput
                style={[styles.input, errors.password && styles.inputError]}
                placeholder="Minimum 6 znaków"
                placeholderTextColor="#999"
                value={password}
                onChangeText={setPassword}
                secureTextEntry
                autoComplete={isLogin ? 'current-password' : 'new-password'}
              />
              {errors.password && (
                <Text style={styles.errorText}>{errors.password}</Text>
              )}
            </View>

            {/* Submit Button */}
            <TouchableOpacity
              style={[styles.submitButton, loading && styles.submitButtonDisabled]}
              onPress={handleSubmit}
              disabled={loading}
            >
              {loading ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.submitButtonText}>
                  {isLogin ? 'Zaloguj się' : 'Zarejestruj się'}
                </Text>
              )}
            </TouchableOpacity>

            {/* Toggle Mode */}
            <View style={styles.toggleContainer}>
              <Text style={styles.toggleText}>
                {isLogin ? 'Nie masz konta?' : 'Masz już konto?'}
              </Text>
              <TouchableOpacity onPress={toggleMode}>
                <Text style={styles.toggleLink}>
                  {isLogin ? 'Zarejestruj się' : 'Zaloguj się'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  // Layout
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  keyboardView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: spacing.xxl,
  },

  // Header
  header: {
    alignItems: 'center',
    marginBottom: spacing.xxxl + spacing.sm,
  },
  logoEmoji: {
    fontSize: 64,
    marginBottom: spacing.lg,
  },
  title: {
    fontSize: typography.xxxl,
    fontWeight: typography.bold,
    color: '#1F2937',
    marginBottom: spacing.sm,
    letterSpacing: 1,
  },
  subtitle: {
    fontSize: typography.base,
    color: colors.textSecondary,
  },

  // Form card
  form: {
    backgroundColor: colors.surface,
    borderRadius: borderRadius.xxl,
    padding: spacing.xxl,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.md,
  },
  inputContainer: {
    marginBottom: spacing.xl,
  },
  label: {
    fontSize: typography.sm,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  input: {
    height: 50,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: borderRadius.lg,
    paddingHorizontal: spacing.lg,
    fontSize: typography.base,
    color: colors.textPrimary,
    backgroundColor: colors.background,
  },
  inputError: {
    borderColor: colors.error,
    backgroundColor: colors.errorLight,
  },
  errorText: {
    color: colors.error,
    fontSize: typography.xs,
    marginTop: spacing.xs,
  },

  // Submit button
  submitButton: {
    backgroundColor: colors.primary,
    height: 50,
    borderRadius: borderRadius.lg,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: spacing.sm,
  },
  submitButtonDisabled: {
    backgroundColor: colors.primaryLight,
  },
  submitButtonText: {
    color: colors.textInverse,
    fontSize: typography.base,
    fontWeight: typography.semibold,
  },

  // Toggle mode
  toggleContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: spacing.xxl,
  },
  toggleText: {
    color: colors.textSecondary,
    fontSize: typography.sm,
  },
  toggleLink: {
    color: colors.primary,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
});

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

    return 'Wystąpił błąd. Spróbuj ponownie';
  };

  const handleSubmit = async () => {
    if (!validateForm()) return;

    setLoading(true);

    try {
      if (isLogin) {
        await signIn(email.trim(), password);
        onAuthSuccess();
      } else {
        const { user } = await signUp(email.trim(), password, displayName.trim());

        if (user) {
          // For Supabase with email confirmation disabled, user is logged in immediately
          // For email confirmation enabled, show message
          if (user.email_confirmed_at) {
            onAuthSuccess();
          } else {
            Alert.alert(
              'Rejestracja zakończona',
              'Sprawdź swoją skrzynkę e-mail i potwierdź konto, aby się zalogować.',
              [{ text: 'OK', onPress: () => setIsLogin(true) }]
            );
          }
        }
      }
    } catch (error) {
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
            <Text style={styles.logo}>📞</Text>
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
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  keyboardView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
  },
  header: {
    alignItems: 'center',
    marginBottom: 40,
  },
  logo: {
    fontSize: 64,
    marginBottom: 16,
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    color: '#333',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
  },
  form: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
  inputContainer: {
    marginBottom: 20,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  input: {
    height: 50,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 10,
    paddingHorizontal: 16,
    fontSize: 16,
    color: '#333',
    backgroundColor: '#fafafa',
  },
  inputError: {
    borderColor: '#F44336',
    backgroundColor: '#FFF5F5',
  },
  errorText: {
    color: '#F44336',
    fontSize: 12,
    marginTop: 4,
  },
  submitButton: {
    backgroundColor: '#007AFF',
    height: 50,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 8,
  },
  submitButtonDisabled: {
    backgroundColor: '#99c9ff',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  toggleContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
    gap: 4,
  },
  toggleText: {
    color: '#666',
    fontSize: 14,
  },
  toggleLink: {
    color: '#007AFF',
    fontSize: 14,
    fontWeight: '600',
  },
});

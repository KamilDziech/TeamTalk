/**
 * DeviceService
 *
 * Manages device registration for push notifications.
 * Handles push token registration, updates, and team notifications.
 */

import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { supabase } from '@/api/supabaseClient';

const USER_NAME_KEY = 'device_user_name';
const PUSH_TOKEN_KEY = 'device_push_token';

export class DeviceService {
  private pushToken: string | null = null;
  private userName: string | null = null;

  /**
   * Initialize device service - request permissions and register token
   */
  async initialize(userName?: string): Promise<boolean> {
    try {
      // Load or set user name
      if (userName) {
        this.userName = userName;
        await AsyncStorage.setItem(USER_NAME_KEY, userName);
      } else {
        this.userName = await AsyncStorage.getItem(USER_NAME_KEY);
      }

      // Request notification permissions
      const hasPermission = await this.requestPermissions();
      if (!hasPermission) {
        console.warn('Push notification permissions not granted');
        return false;
      }

      // Get and register push token
      const token = await this.getPushToken();
      if (token && this.userName) {
        await this.registerDevice(token, this.userName);
        return true;
      }

      return false;
    } catch (error) {
      console.error('Error initializing DeviceService:', error);
      return false;
    }
  }

  /**
   * Request push notification permissions
   */
  async requestPermissions(): Promise<boolean> {
    if (!Device.isDevice) {
      console.warn('Push notifications only work on physical devices');
      return false;
    }

    const { status: existingStatus } = await Notifications.getPermissionsAsync();
    let finalStatus = existingStatus;

    if (existingStatus !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }

    if (finalStatus !== 'granted') {
      console.warn('Push notification permission not granted');
      return false;
    }

    // Configure notification channel for Android
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'default',
        importance: Notifications.AndroidImportance.HIGH,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#007AFF',
      });
    }

    return true;
  }

  /**
   * Get Expo push token
   */
  async getPushToken(): Promise<string | null> {
    try {
      const projectId = Constants.expoConfig?.extra?.eas?.projectId;
      const token = await Notifications.getExpoPushTokenAsync({
        projectId,
      });
      this.pushToken = token.data;
      await AsyncStorage.setItem(PUSH_TOKEN_KEY, token.data);
      console.log('Push token:', token.data);
      return token.data;
    } catch (error) {
      console.error('Error getting push token:', error);
      return null;
    }
  }

  /**
   * Register device with Supabase
   */
  async registerDevice(pushToken: string, userName: string): Promise<boolean> {
    try {
      const deviceInfo = `${Device.brand || 'Unknown'} ${Device.modelName || ''} (${Platform.OS} ${Platform.Version})`;

      // Upsert device - update if token exists, insert if new
      const { error } = await supabase
        .from('devices')
        .upsert(
          {
            push_token: pushToken,
            user_name: userName,
            device_info: deviceInfo,
            last_active_at: new Date().toISOString(),
          },
          {
            onConflict: 'push_token',
          }
        );

      if (error) {
        console.error('Error registering device:', error);
        return false;
      }

      console.log('Device registered successfully');
      return true;
    } catch (error) {
      console.error('Error in registerDevice:', error);
      return false;
    }
  }

  /**
   * Update user name
   */
  async setUserName(userName: string): Promise<void> {
    this.userName = userName;
    await AsyncStorage.setItem(USER_NAME_KEY, userName);

    // Update in database if we have a token
    const token = await AsyncStorage.getItem(PUSH_TOKEN_KEY);
    if (token) {
      await this.registerDevice(token, userName);
    }
  }

  /**
   * Get current user name
   */
  async getUserName(): Promise<string | null> {
    if (this.userName) return this.userName;
    return AsyncStorage.getItem(USER_NAME_KEY);
  }

  /**
   * Send push notification to all team members (except sender)
   */
  async notifyTeam(
    title: string,
    body: string,
    data?: Record<string, unknown>
  ): Promise<void> {
    try {
      const currentToken = await AsyncStorage.getItem(PUSH_TOKEN_KEY);

      // Get all devices except current one
      const { data: devices, error } = await supabase
        .from('devices')
        .select('push_token')
        .neq('push_token', currentToken || '');

      if (error) {
        console.error('Error fetching devices:', error);
        return;
      }

      if (!devices || devices.length === 0) {
        console.log('No other devices to notify');
        return;
      }

      // Send notifications via Expo Push API
      const messages = devices.map((device) => ({
        to: device.push_token,
        sound: 'default' as const,
        title,
        body,
        data: data || {},
      }));

      const response = await fetch('https://exp.host/--/api/v2/push/send', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(messages),
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error('Error sending push notifications:', errorText);
      } else {
        console.log(`Sent notifications to ${devices.length} devices`);
      }
    } catch (error) {
      console.error('Error in notifyTeam:', error);
    }
  }

  /**
   * Send notification about new voice report
   */
  async notifyNewVoiceReport(clientName: string): Promise<void> {
    const userName = await this.getUserName();
    const displayName = userName || 'Ktoś';

    await this.notifyTeam(
      '📝 Nowa notatka',
      `${displayName} dodał notatkę do rozmowy z ${clientName}`,
      { type: 'voice_report', clientName }
    );
  }

  /**
   * Update last active timestamp
   */
  async updateLastActive(): Promise<void> {
    const token = await AsyncStorage.getItem(PUSH_TOKEN_KEY);
    if (!token) return;

    await supabase
      .from('devices')
      .update({ last_active_at: new Date().toISOString() })
      .eq('push_token', token);
  }
}

// Export singleton instance
export const deviceService = new DeviceService();

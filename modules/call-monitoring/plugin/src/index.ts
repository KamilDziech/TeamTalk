/**
 * Expo Config Plugin for Call Monitoring Module
 *
 * Adds required Android permissions and configuration
 */

import {
  ConfigPlugin,
  AndroidConfig,
  withAndroidManifest,
} from '@expo/config-plugins';

const withCallMonitoringPermissions: ConfigPlugin = (config) => {
  return withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults.manifest;

    // Add required permissions
    if (!androidManifest.$) {
      androidManifest.$ = {};
    }

    if (!androidManifest['uses-permission']) {
      androidManifest['uses-permission'] = [];
    }

    const permissions = [
      'android.permission.READ_PHONE_STATE',
      'android.permission.READ_CALL_LOG',
      'android.permission.POST_NOTIFICATIONS',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_PHONE_CALL',
    ];

    permissions.forEach((permission) => {
      if (
        !androidManifest['uses-permission']?.find(
          (p: any) => p.$['android:name'] === permission
        )
      ) {
        androidManifest['uses-permission']?.push({
          $: { 'android:name': permission },
        });
      }
    });

    return config;
  });
};

export default withCallMonitoringPermissions;

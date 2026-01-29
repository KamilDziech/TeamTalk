/**
 * Expo Config Plugin for Android Permissions
 *
 * Adds required permissions for TeamTalk:
 * - READ_CALL_LOG: access to call history
 * - POST_NOTIFICATIONS: local notifications (Android 13+)
 */

const { withAndroidManifest } = require('@expo/config-plugins');

module.exports = function withAndroidCallLogPermissions(config) {
  return withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults.manifest;

    // Ensure uses-permission array exists
    if (!androidManifest['uses-permission']) {
      androidManifest['uses-permission'] = [];
    }

    // Permissions to add
    const permissions = [
      'android.permission.READ_CALL_LOG',
      'android.permission.POST_NOTIFICATIONS',
    ];

    // Add permissions if they don't already exist
    permissions.forEach((permission) => {
      const exists = androidManifest['uses-permission']?.some(
        (p) => p.$['android:name'] === permission
      );

      if (!exists) {
        androidManifest['uses-permission'].push({
          $: { 'android:name': permission },
        });
      }
    });

    return config;
  });
};

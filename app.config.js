/**
 * Expo App Configuration
 *
 * Uses environment variables from EAS for sensitive data.
 * Local development uses .env file, production uses EAS secrets.
 *
 * To set EAS secrets:
 * eas secret:create --name SUPABASE_URL --value "https://xxx.supabase.co" --scope project
 * eas secret:create --name SUPABASE_ANON_KEY --value "xxx" --scope project
 * eas secret:create --name OPENAI_API_KEY --value "sk-xxx" --scope project
 */

require('dotenv').config();

module.exports = {
  expo: {
    name: "TeamTalk",
    slug: "teamtalk",
    version: "1.0.0",
    orientation: "portrait",
    icon: "./assets/icon.png",
    userInterfaceStyle: "light",
    splash: {
      image: "./assets/splash.png",
      resizeMode: "contain",
      backgroundColor: "#2563EB"
    },
    assetBundlePatterns: ["**/*"],
    ios: {
      supportsTablet: false,
      bundleIdentifier: "com.ekotak.teamtalk",
      infoPlist: {
        NSMicrophoneUsageDescription: "TeamTalk potrzebuje dostępu do mikrofonu, aby nagrywać notatki głosowe po rozmowach z klientami.",
        NSContactsUsageDescription: "TeamTalk potrzebuje dostępu do kontaktów, aby importować dane klientów."
      }
    },
    android: {
      package: "com.ekotak.teamtalk",
      versionCode: 1,
      adaptiveIcon: {
        foregroundImage: "./assets/adaptive-icon.png",
        backgroundColor: "#FFFFFF"
      },
      permissions: [
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_CALL_LOG",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.VIBRATE"
      ]
    },
    scheme: "teamtalk",
    plugins: [
      "expo-asset",
      "expo-font",
      "./plugins/withAndroidPermissions",
      [
        "expo-contacts",
        {
          "contactsPermission": "Zezwól aplikacji TeamTalk na dostęp do kontaktów, aby ułatwić dodawanie klientów."
        }
      ],
      [
        "expo-av",
        {
          "microphonePermission": "Zezwól aplikacji TeamTalk na dostęp do mikrofonu, aby nagrywać notatki głosowe."
        }
      ],
      [
        "expo-notifications",
        {
          "icon": "./assets/notification-icon.png",
          "color": "#2563EB"
        }
      ]
    ],
    extra: {
      // API Keys - loaded from environment variables (local .env or EAS secrets)
      supabaseUrl: process.env.SUPABASE_URL,
      supabaseAnonKey: process.env.SUPABASE_ANON_KEY,
      openaiApiKey: process.env.OPENAI_API_KEY,
      // EAS configuration
      eas: {
        projectId: "70a863dc-ad69-43ce-a26e-58c4accd11f7"
      }
    },
    runtimeVersion: {
      policy: "appVersion"
    },
    updates: {
      url: "https://u.expo.dev/70a863dc-ad69-43ce-a26e-58c4accd11f7"
    }
  }
};

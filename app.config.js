require('dotenv').config();

module.exports = {
  expo: {
    name: "TeamTalk",
    slug: "teamtalk",
    version: "1.0.0",
    orientation: "portrait",
    userInterfaceStyle: "light",
    splash: {
      resizeMode: "contain",
      backgroundColor: "#ffffff"
    },
    assetBundlePatterns: [
      "**/*"
    ],
    ios: {
      supportsTablet: true,
      bundleIdentifier: "com.ekotak.teamtalk"
    },
    android: {
      package: "com.ekotak.teamtalk001",
      googleServicesFile: "./google-services.json",
      permissions: [
        "READ_PHONE_STATE",
        "READ_CALL_LOG",
        "READ_CONTACTS",
        "POST_NOTIFICATIONS",
        "RECORD_AUDIO"
      ]
    },
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
        "expo-audio",
        {
          "microphonePermission": "Zezwól aplikacji TeamTalk na dostęp do mikrofonu, aby nagrywać notatki głosowe."
        }
      ]
    ],
    scheme: "teamtalk",
    extra: {
      supabaseUrl: process.env.SUPABASE_URL,
      supabaseAnonKey: process.env.SUPABASE_ANON_KEY,
      openaiApiKey: process.env.OPENAI_API_KEY,
      claudeApiKey: process.env.CLAUDE_API_KEY,
      eas: {
        projectId: "70a863dc-ad69-43ce-a26e-58c4accd11f7"
      }
    }
  }
};

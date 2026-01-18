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
      backgroundColor: "#ffffff"
    },
    assetBundlePatterns: [
      "**/*"
    ],
    ios: {
      supportsTablet: true
    },
    android: {
      package: "com.ekotak.teamtalk",
      adaptiveIcon: {
        foregroundImage: "./assets/adaptive-icon.png",
        backgroundColor: "#ffffff"
      },
      permissions: [
        "READ_PHONE_STATE",
        "READ_CALL_LOG",
        "POST_NOTIFICATIONS"
      ]
    },
    web: {
      favicon: "./assets/favicon.png"
    },
    plugins: [
      "expo-asset",
      "expo-font",
      "./plugins/withAndroidPermissions"
    ],
    scheme: "teamtalk",
    extra: {
      supabaseUrl: process.env.SUPABASE_URL,
      supabaseAnonKey: process.env.SUPABASE_ANON_KEY,
    }
  }
};

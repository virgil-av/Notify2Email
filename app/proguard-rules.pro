# Strip Android Log calls from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
    public static boolean isLoggable(...);
}

# Jakarta Mail / JavaMail
-keep class jakarta.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class jakarta.activation.** { *; }
-keep class com.sun.activation.** { *; }

# Preserve provider configuration files in META-INF
-keepnames class com.sun.mail.smtp.** { *; }
-keepnames class com.sun.mail.imap.** { *; }
-keepnames class com.sun.mail.pop3.** { *; }
-keepnames class com.sun.mail.handlers.** { *; }

# Ignore warnings for missing optional dependencies (like AWT)
-dontwarn java.awt.**
-dontwarn javax.activation.**
-dontwarn javax.security.auth.callback.**
-dontwarn javax.security.sasl.**

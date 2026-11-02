# gomobile: JNI symbol lookup is by class name, so these must never be renamed.
-keep class libmohammad.** { *; }
-keep class go.** { *; }
-dontwarn libmohammad.**

# The bundle is read from assets by name at runtime.
-keep class ir.mohammad.vpn.data.OperatorBundleLoader { *; }

# net.i2p.crypto:eddsa checks for a JDK-internal X509Key class that does not exist on Android.
# The check is guarded at runtime, so the reference only needs to stop being an error.
-dontwarn sun.security.x509.**

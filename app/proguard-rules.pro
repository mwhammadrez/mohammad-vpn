# gomobile: JNI symbol lookup is by class name, so these must never be renamed.
-keep class libmohammad.** { *; }
-keep class go.** { *; }
-dontwarn libmohammad.**

# The bundle is read from assets by name at runtime.
-keep class ir.mohammad.vpn.data.OperatorBundleLoader { *; }

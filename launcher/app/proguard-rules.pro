# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# Explicit ADB app_process entry; its normal app UI never starts this process.
-keep class com.gathin.tvhome.ShellSettingsSession { public static void main(java.lang.String[]); }

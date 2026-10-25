# Room, WorkManager, Compose and AndroidX ship their own consumer rules.
# Receivers and workers are referenced from the manifest / by class name and are kept by AAPT rules.
-keepattributes *Annotation*

# MUX Debloat (root)
Kotlin + Jetpack Compose, MUX/Jarvis HUD look (cyan + amber). Package: com.mux.debloater, minSdk 26.
Build: push to GitHub -> Actions "Build debug APK" -> download artifact. Or `gradle assembleDebug` (JDK 17 + Android SDK 34).
Signed with app/debug.keystore (alias androiddebugkey, pass android).

Modes
- DIRECT DELETE: remount partition rw, tar-backup to /sdcard/MUXDebloat/backup, pm clear, rm -rf the app folder. Frees storage.
  Needs a remountable partition (verity disabled, ext4). Fails on erofs / dynamic read-only partitions (e.g. Android 13 phones).
- SYSTEMLESS: writes a Magisk module (/data/adb/modules/mux_debloat) with .replace dirs, active after reboot. Reversible, no storage freed,
  lost on factory reset.
REMOVED tab restores either kind. RISK-tagged packages ask for extra confirmation. Always keep a stock-image backup.

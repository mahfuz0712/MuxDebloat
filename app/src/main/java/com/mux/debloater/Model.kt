package com.mux.debloater

import java.util.Locale

val APP_ROOTS = setOf(
    "/system/app", "/system/priv-app", "/product/app", "/product/priv-app",
    "/system_ext/app", "/system_ext/priv-app", "/vendor/app", "/odm/app",
    "/system/product/app", "/system/product/priv-app", "/system/system_ext/app",
    "/system/system_ext/priv-app", "/system/vendor/app"
)

fun mountOf(dir: String): String = when {
    dir.startsWith("/product") -> "/product"
    dir.startsWith("/system_ext") -> "/system_ext"
    dir.startsWith("/vendor") -> "/vendor"
    dir.startsWith("/odm") -> "/odm"
    else -> "/system"
}

data class AppItem(
    val pkg: String, val label: String, val dir: String, val isFile: Boolean,
    val size: Long, val risk: Boolean, val updated: Boolean
) {
    val mountPoint: String get() = mountOf(dir)
    val part: String get() = when (mountPoint) {
        "/product" -> "PRODUCT"; "/system_ext" -> "SYS_EXT"; "/vendor" -> "VENDOR"; "/odm" -> "ODM"; else -> "SYSTEM"
    }
}

data class Removed(val pkg: String, val dir: String, val mount: String, val direct: Boolean)

fun fmtSize(b: Long): String =
    if (b >= 1L shl 20) String.format(Locale.US, "%.1f MB", b / 1048576.0) else "${b / 1024} KB"

// Removing these can bootloop the phone or break calls / Wi-Fi / keyboard / Play services.
private val RISKY_EXACT = setOf(
    "android", "com.android.systemui", "com.android.phone", "com.android.settings",
    "com.android.providers.settings", "com.android.providers.telephony", "com.android.providers.contacts",
    "com.android.providers.media", "com.android.providers.media.module", "com.android.providers.downloads",
    "com.android.providers.calendar", "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.android.shell", "com.android.keychain", "com.android.bluetooth", "com.android.nfc",
    "com.android.inputdevices", "com.android.server.telecom", "com.android.permissioncontroller",
    "com.google.android.permissioncontroller", "com.android.networkstack", "com.android.networkstack.inprocess",
    "com.android.captiveportallogin", "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
    "com.android.webview", "com.google.android.webview", "com.android.certinstaller", "com.android.location.fused",
    "com.android.stk", "com.android.mms.service", "com.android.emergency", "com.android.cellbroadcastreceiver",
    "com.android.se", "com.android.storagemanager", "com.android.externalstorage", "com.android.documentsui",
    "com.qualcomm.qti.services.systemhelper", "com.qualcomm.qcrilmsgtunnel", "com.qualcomm.telephony",
    "org.codeaurora.ims", "com.android.carrierconfig", "com.android.modulemetadata",
    "com.google.android.ext.services", "com.google.android.ext.shared", "com.google.android.configupdater"
)
private val RISKY_PREFIX = listOf(
    "com.android.inputmethod", "com.google.android.inputmethod", "com.android.launcher",
    "com.google.android.apps.nexuslauncher", "com.android.theme", "android.overlay", "com.android.internal",
    "com.android.systemui.", "com.android.overlay", "com.android.wifi", "com.android.cts", "com.android.localtransport"
)

fun isRisky(pkg: String): Boolean = pkg in RISKY_EXACT || RISKY_PREFIX.any { pkg.startsWith(it) }

private val BLOAT_PREFIX = listOf(
    "com.facebook.", "com.netflix.", "com.amazon.", "com.linkedin.", "com.spotify.", "com.tiktok.",
    "com.zhiliaoapp.", "com.booking", "com.opera.", "com.ebay.", "com.twitter.", "com.instagram.",
    "com.google.android.videos", "com.google.android.music", "com.google.android.apps.magazines",
    "com.google.android.apps.books", "com.google.android.apps.podcasts", "com.google.android.apps.tachyon",
    "com.google.android.youtube", "com.google.android.apps.docs.editors", "com.google.android.apps.subscriptions",
    "com.google.android.play.games", "com.google.android.apps.youtube.music", "com.google.android.googlequicksearchbox.dummy"
)

fun isBloatPreset(pkg: String): Boolean = !isRisky(pkg) && BLOAT_PREFIX.any { pkg.startsWith(it) }

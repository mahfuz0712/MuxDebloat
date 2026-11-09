package com.mux.debloater

import java.io.File
import com.mux.debloater.Root.q

object Debloater {
    const val BK = "/sdcard/MUXDebloat/backup"
    const val MOD = "/data/adb/modules/mux_debloat"

    /** Turns the path reported by the system into (dir-or-file-to-delete, isFile), or null. */
    fun resolve(p: String): Pair<String, Boolean>? {
        if (p.isBlank() || p.startsWith("/data")) return null
        val f = File(p)
        return if (p.endsWith(".apk")) {
            val par = f.parent ?: return null
            if (par in APP_ROOTS) p to true else par to false
        } else {
            if (p in APP_ROOTS) null else p to false
        }
    }

    /** Original system location of an app that has an update installed in /data. */
    fun hiddenPath(pkg: String): String? {
        val out = Root.sh("dumpsys package $pkg", 30).out
        val i = out.indexOf("Hidden system packages:")
        if (i < 0) return null
        val m = Regex("codePath=(\\S+)").find(out, i) ?: return null
        return m.groupValues[1]
    }

    fun dirSize(path: String): Long {
        val f = File(path)
        return try {
            if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) { 0L }
    }

    private fun mountRw(mp: String, probe: String): Boolean {
        val tries = listOf("mount -o rw,remount $mp", "mount -o rw,remount /", "mount -o rw,remount $mp $mp")
        for (c in tries) {
            Root.sh(c, 30)
            val t = Root.sh("touch ${q("$probe/.mux_rw")} && rm -f ${q("$probe/.mux_rw")}", 20)
            if (t.ok) return true
        }
        return false
    }

    private fun mountRo(mp: String) { Root.sh("mount -o ro,remount $mp", 30) }

    private fun wipeData(pkg: String) {
        Root.sh("pm clear $pkg", 30)
        Root.sh("rm -rf /data/data/$pkg /data/user/0/$pkg /data/user_de/0/$pkg /data/misc/profiles/cur/0/$pkg", 30)
    }

    /** True delete from the system partition (frees storage). Needs a read-write remountable partition. */
    fun deleteDirect(a: AppItem, say: (String) -> Unit): Boolean {
        val parent = File(a.dir).parent ?: return false
        if (!mountRw(a.mountPoint, parent)) {
            say("✖ ${a.pkg}: ${a.mountPoint} cannot be remounted read-write (verity on / erofs / dynamic partitions). Use SYSTEMLESS mode.")
            return false
        }
        val bk = "$BK/${a.pkg}"
        Root.sh("rm -rf ${q(bk)}; mkdir -p ${q(bk)}")
        val tar = Root.sh("tar -cf ${q("$bk/data.tar")} -C ${q(parent)} ${q(File(a.dir).name)}")
        if (!tar.ok || !Root.sh("test -s ${q("$bk/data.tar")}").ok) {
            say("✖ ${a.pkg}: backup failed (${tar.out}); nothing deleted"); mountRo(a.mountPoint); return false
        }
        Root.sh("printf '%s\\n%s\\n' ${q(a.dir)} ${q(a.mountPoint)} > ${q("$bk/.meta")}")
        if (a.updated) Root.sh("pm uninstall ${a.pkg}", 60)
        wipeData(a.pkg)
        Root.sh("rm -rf ${q(a.dir)}", 60)
        val gone = Root.sh("test ! -e ${q(a.dir)}").ok
        mountRo(a.mountPoint)
        say(if (gone) "✔ ${a.pkg} deleted (${fmtSize(a.size)} freed, backup saved)" else "✖ ${a.pkg}: delete failed")
        return gone
    }

    private fun ensureModule() {
        Root.sh("mkdir -p $MOD/system")
        Root.sh("printf 'id=mux_debloat\\nname=MUX Debloat (systemless)\\nversion=1\\nversionCode=1\\nauthor=MUX\\ndescription=Hides debloated system apps\\n' > $MOD/module.prop")
    }

    private fun modPath(dir: String): String =
        "$MOD/system" + (if (dir.startsWith("/system/")) dir.removePrefix("/system") else dir)

    /** Magisk module that hides the app at next boot. Works on read-only / erofs; storage is NOT freed. */
    fun deleteSystemless(a: AppItem, say: (String) -> Unit): Boolean {
        if (!Root.sh("test -d /data/adb/magisk -o -d /data/adb/ksu -o -d /data/adb/modules").ok) {
            say("✖ Magisk/KernelSU modules folder not found - SYSTEMLESS needs Magisk."); return false
        }
        ensureModule()
        val mp = modPath(a.dir)
        val par = File(mp).parent ?: return false
        Root.sh("mkdir -p ${q(par)}")
        val r = if (a.isFile) Root.sh("rm -f ${q(mp)}; mknod ${q(mp)} c 0 0")
                else Root.sh("mkdir -p ${q(mp)} && touch ${q("$mp/.replace")}")
        if (!r.ok) { say("✖ ${a.pkg}: ${r.out}"); return false }
        Root.sh("echo ${q("${a.pkg}|${a.dir}")} >> $MOD/mux_removed.txt")
        wipeData(a.pkg)
        say("✔ ${a.pkg} hidden by module (active after reboot)")
        return true
    }

    fun loadRemoved(): List<Removed> {
        val out = mutableListOf<Removed>()
        Root.sh("ls -1 ${q(BK)} 2>/dev/null").out.lines().filter { it.isNotBlank() }.forEach { pkg ->
            val m = Root.sh("cat ${q("$BK/$pkg/.meta")} 2>/dev/null").out.lines()
            if (m.size >= 2) out += Removed(pkg, m[0], m[1], true)
        }
        Root.sh("cat $MOD/mux_removed.txt 2>/dev/null").out.lines().filter { it.contains("|") }.forEach {
            val parts = it.split("|", limit = 2)
            out += Removed(parts[0], parts[1], mountOf(parts[1]), false)
        }
        return out
    }

    fun restore(r: Removed, say: (String) -> Unit): Boolean {
        if (!r.direct) {
            Root.sh("rm -rf ${q(modPath(r.dir))}")
            Root.sh("grep -vF ${q("${r.pkg}|${r.dir}")} $MOD/mux_removed.txt > $MOD/.tmp; mv $MOD/.tmp $MOD/mux_removed.txt")
            say("✔ ${r.pkg} un-hidden (reboot to apply)")
            return true
        }
        val parent = File(r.dir).parent ?: return false
        if (!mountRw(r.mount, parent)) { say("✖ ${r.pkg}: cannot remount ${r.mount} read-write"); return false }
        val bk = "$BK/${r.pkg}"
        val x = Root.sh("tar -xf ${q("$bk/data.tar")} -C ${q(parent)}")
        if (!x.ok) { say("✖ ${r.pkg}: restore failed (${x.out})"); mountRo(r.mount); return false }
        Root.sh("chown -R 0:0 ${q(r.dir)}; find ${q(r.dir)} -type d -exec chmod 755 {} +; find ${q(r.dir)} -type f -exec chmod 644 {} +; restorecon -R ${q(r.dir)}")
        mountRo(r.mount)
        Root.sh("rm -rf ${q(bk)}")
        say("✔ ${r.pkg} restored to ${r.dir} (reboot)")
        return true
    }

    fun reboot() { Root.sh("reboot", 10) }
}

package com.mux.debloater

import java.util.concurrent.TimeUnit

data class Res(val code: Int, val out: String) { val ok: Boolean get() = code == 0 }

object Root {
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun sh(cmd: String, timeoutSec: Long = 120): Res = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val sb = StringBuilder()
        val t = Thread { p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) } }
        t.start()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly(); Res(-1, "timeout")
        } else {
            t.join(3000); Res(p.exitValue(), sb.toString().trim())
        }
    } catch (e: Exception) {
        Res(-2, e.message ?: "su failed")
    }

    fun available(): Boolean = sh("id -u", 30).let { it.ok && it.out.trim().endsWith("0") }
}

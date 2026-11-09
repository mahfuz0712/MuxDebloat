package com.mux.debloater

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainVM(app: Application) : AndroidViewModel(app) {
    var root by mutableStateOf<Boolean?>(null)
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var removed by mutableStateOf<List<Removed>>(emptyList())
    val selected = mutableStateListOf<String>()
    val selectedR = mutableStateListOf<String>()
    var systemless by mutableStateOf(false)
    var busy by mutableStateOf(false)
    val logs = mutableStateListOf<String>()

    init { refresh() }

    fun say(s: String) { viewModelScope.launch(Dispatchers.Main) { logs.add(s) } }

    fun toggle(p: String) { if (p in selected) selected.remove(p) else selected.add(p) }
    fun toggleR(p: String) { if (p in selectedR) selectedR.remove(p) else selectedR.add(p) }
    fun selectPreset() { apps.filter { isBloatPreset(it.pkg) }.forEach { if (it.pkg !in selected) selected.add(it.pkg) } }

    fun refresh() {
        if (busy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val ok = Root.available()
            withContext(Dispatchers.Main) { root = ok }
            if (!ok) say("✖ Root (su) not granted. Grant superuser to MUX Debloat and press REFRESH.")
            val list = loadApps(ok)
            val rem = if (ok) Debloater.loadRemoved() else emptyList()
            withContext(Dispatchers.Main) {
                apps = list; removed = rem
                selected.removeAll { p -> list.none { it.pkg == p } }
                selectedR.removeAll { p -> rem.none { it.pkg == p } }
                busy = false
            }
            say("Loaded ${list.size} system apps, ${rem.size} removed.")
        }
    }

    private fun loadApps(rootOk: Boolean): List<AppItem> {
        val ctx = getApplication<Application>()
        val pm = ctx.packageManager
        val res = mutableListOf<AppItem>()
        for (ai in pm.getInstalledApplications(0)) {
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0) continue
            if (ai.packageName == ctx.packageName) continue
            val upd = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            var path: String? = ai.sourceDir
            if (path == null || path.startsWith("/data")) {
                path = if (upd && rootOk) Debloater.hiddenPath(ai.packageName) else null
            }
            val r = Debloater.resolve(path ?: continue) ?: continue
            val risk = isRisky(ai.packageName) || (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
            res += AppItem(ai.packageName, pm.getApplicationLabel(ai).toString(), r.first, r.second,
                Debloater.dirSize(r.first), risk, upd)
        }
        return res.sortedWith(compareBy({ it.risk }, { it.label.lowercase() }))
    }

    fun runDelete() {
        if (busy) return
        val todo = apps.filter { it.pkg in selected }
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            for (a in todo) {
                say("… ${a.pkg}")
                if (if (systemless) Debloater.deleteSystemless(a, ::say) else Debloater.deleteDirect(a, ::say)) ok++
            }
            say("Done: $ok / ${todo.size}. Reboot to finish.")
            withContext(Dispatchers.Main) { busy = false; selected.clear() }
            busy = false
            refresh()
        }
    }

    fun runRestore() {
        if (busy) return
        val todo = removed.filter { it.pkg in selectedR }
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            for (r in todo) { say("… ${r.pkg}"); if (Debloater.restore(r, ::say)) ok++ }
            say("Restored $ok / ${todo.size}. Reboot to finish.")
            withContext(Dispatchers.Main) { busy = false; selectedR.clear() }
            refresh()
        }
    }

    fun reboot() { viewModelScope.launch(Dispatchers.IO) { Debloater.reboot() } }
}

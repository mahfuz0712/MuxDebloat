package com.mux.debloater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen(vm) }
    }
}

@Composable
fun Screen(vm: MainVM) {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Header(vm)
        ModeRow(vm)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("APPS (${vm.apps.size})", Cyan, filled = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            HudButton("REMOVED (${vm.removed.size})", Amber, filled = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) AppList(vm, query) { query = it } else RemovedList(vm)
        }
        BottomBar(vm, tab) { confirm = true }
        LogPanel(vm)
    }
    if (confirm) ConfirmDialog(vm, tab) { confirm = false }
}

@Composable
fun Header(vm: MainVM) {
    Row(
        Modifier.fillMaxWidth().hudFrame().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("MUX // DEBLOAT", color = Cyan, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                when (vm.root) { true -> "ROOT: GRANTED"; false -> "ROOT: DENIED"; null -> "ROOT: CHECKING..." },
                color = when (vm.root) { true -> Cyan; false -> Red; null -> Amber },
                fontFamily = Mono, fontSize = 12.sp
            )
        }
        HudButton("REFRESH", Cyan, enabled = !vm.busy) { vm.refresh() }
    }
}

@Composable
fun ModeRow(vm: MainVM) {
    Column(Modifier.fillMaxWidth().hudFrame(Amber).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("DIRECT DELETE", Red, filled = !vm.systemless, modifier = Modifier.weight(1f)) { vm.systemless = false }
            HudButton("SYSTEMLESS", Amber, filled = vm.systemless, modifier = Modifier.weight(1f)) { vm.systemless = true }
        }
        Text(
            if (vm.systemless)
                "Magisk module hides the app at boot. Works on read-only/erofs system. Reversible. Storage not freed."
            else
                "Remounts the partition read-write and removes the APK for real (frees storage). Auto backup first. Fails if /system is read-only.",
            color = Dim, fontFamily = Mono, fontSize = 11.sp
        )
    }
}

@Composable
fun AppList(vm: MainVM, query: String, onQuery: (String) -> Unit) {
    val shown = vm.apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).hudFrame().padding(10.dp)) {
                if (query.isEmpty()) Text("search name / package", color = Dim, fontFamily = Mono, fontSize = 13.sp)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = Cyan, fontFamily = Mono, fontSize = 13.sp),
                    cursorBrush = SolidColor(Cyan), modifier = Modifier.fillMaxWidth()
                )
            }
            HudButton("PRESET", Amber) { vm.selectPreset() }
            HudButton("CLEAR", Dim) { vm.selected.clear() }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(shown, key = { it.pkg }) { a ->
                val sel = a.pkg in vm.selected
                Row(
                    Modifier.fillMaxWidth().hudFrame(if (a.risk) Red else Cyan)
                        .clickable { vm.toggle(a.pkg) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CheckBox(sel)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(a.label, color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(a.pkg, color = Dim, fontFamily = Mono, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Chip(a.part, Cyan); Chip(fmtSize(a.size), Amber)
                            if (a.updated) Chip("UPDATED", Dim)
                            if (a.risk) Chip("RISK", Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemovedList(vm: MainVM) {
    if (vm.removed.isEmpty()) {
        Text("Nothing removed yet.", color = Dim, fontFamily = Mono, fontSize = 13.sp)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(vm.removed, key = { it.pkg + it.direct }) { r ->
            Row(
                Modifier.fillMaxWidth().hudFrame(Amber).clickable { vm.toggleR(r.pkg) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CheckBox(r.pkg in vm.selectedR)
                Column(Modifier.weight(1f)) {
                    Text(r.pkg, color = Color.White, fontFamily = Mono, fontSize = 13.sp)
                    Text(r.dir, color = Dim, fontFamily = Mono, fontSize = 11.sp)
                }
                Chip(if (r.direct) "BACKUP" else "MODULE", if (r.direct) Cyan else Amber)
            }
        }
    }
}

@Composable
fun CheckBox(on: Boolean) {
    Box(
        Modifier.size(20.dp).border(1.5.dp, if (on) Amber else Cyan.copy(alpha = 0.6f))
            .background(if (on) Amber.copy(alpha = 0.25f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) { if (on) Text("X", color = Amber, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
fun BottomBar(vm: MainVM, tab: Int, onGo: () -> Unit) {
    val n = if (tab == 0) vm.selected.size else vm.selectedR.size
    val total = vm.apps.filter { it.pkg in vm.selected }.sumOf { it.size }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (tab == 0) "$n selected  ${fmtSize(total)}" else "$n selected",
            color = Cyan, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f)
        )
        HudButton("REBOOT", Dim) { vm.reboot() }
        HudButton(if (tab == 0) "DELETE" else "RESTORE", if (tab == 0) Red else Amber,
            enabled = n > 0 && !vm.busy && vm.root == true, filled = true) { onGo() }
    }
}

@Composable
fun LogPanel(vm: MainVM) {
    Column(Modifier.fillMaxWidth().hudFrame(Dim).padding(8.dp)) {
        val last = vm.logs.takeLast(5)
        if (last.isEmpty()) Text("log", color = Dim, fontFamily = Mono, fontSize = 11.sp)
        last.forEach { Text(it, color = Dim, fontFamily = Mono, fontSize = 11.sp, maxLines = 2) }
    }
}

@Composable
fun ConfirmDialog(vm: MainVM, tab: Int, close: () -> Unit) {
    val risky = if (tab == 0) vm.apps.filter { it.pkg in vm.selected && it.risk } else emptyList()
    Dialog(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().hudFrame(if (risky.isEmpty()) Cyan else Red).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (tab == 0) "CONFIRM DELETE" else "CONFIRM RESTORE", color = Cyan, fontFamily = Mono,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if (tab == 0) "${vm.selected.size} app(s), mode: " + (if (vm.systemless) "SYSTEMLESS (Magisk module)" else "DIRECT (real delete, backup first)")
                else "${vm.selectedR.size} app(s) will be put back.",
                color = Color.White, fontFamily = Mono, fontSize = 13.sp
            )
            if (risky.isNotEmpty()) Text(
                "WARNING: ${risky.size} RISKY system package(s) selected: " + risky.take(8).joinToString { it.pkg } +
                    ". Removing them can cause a bootloop. Keep a recovery/flash backup.",
                color = Red, fontFamily = Mono, fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("CANCEL", Dim, modifier = Modifier.weight(1f)) { close() }
                HudButton("CONFIRM", if (tab == 0) Red else Amber, filled = true, modifier = Modifier.weight(1f)) {
                    close(); if (tab == 0) vm.runDelete() else vm.runRestore()
                }
            }
        }
    }
}

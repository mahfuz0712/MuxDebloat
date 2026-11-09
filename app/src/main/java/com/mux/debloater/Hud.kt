package com.mux.debloater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cyan = Color(0xFF00E5FF)
val Amber = Color(0xFFFFB300)
val Bg = Color(0xFF040A10)
val PanelC = Color(0xFF09141E)
val Red = Color(0xFFFF3D57)
val Dim = Color(0xFF6B8794)
val Mono = FontFamily.Monospace

/** Dark panel with a thin outline and HUD corner brackets. */
fun Modifier.hudFrame(c: Color = Cyan): Modifier = this
    .background(PanelC.copy(alpha = 0.9f))
    .drawBehind {
        val len = 14.dp.toPx()
        val sw = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawRect(c.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
        drawLine(c, Offset(0f, 0f), Offset(len, 0f), sw)
        drawLine(c, Offset(0f, 0f), Offset(0f, len), sw)
        drawLine(c, Offset(w, 0f), Offset(w - len, 0f), sw)
        drawLine(c, Offset(w, 0f), Offset(w, len), sw)
        drawLine(c, Offset(0f, h), Offset(len, h), sw)
        drawLine(c, Offset(0f, h), Offset(0f, h - len), sw)
        drawLine(c, Offset(w, h), Offset(w - len, h), sw)
        drawLine(c, Offset(w, h), Offset(w, h - len), sw)
    }

@Composable
fun HudButton(
    text: String, color: Color = Cyan, enabled: Boolean = true, filled: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val c = if (enabled) color else Dim
    Box(
        modifier
            .border(1.dp, c)
            .background(if (filled) c.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = c, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontFamily = Mono, fontSize = 10.sp,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

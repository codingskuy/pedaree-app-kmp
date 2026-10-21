package com.pedaree.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pedaree.shared.domain.StatusStok

private val Skema = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondary = Color(0xFF8D6E63),
    background = Color(0xFFFAFAF5),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun PedareeTema(isi: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Skema) {
        Surface(color = MaterialTheme.colorScheme.background, content = isi)
    }
}

/** Warna status stok (hijau aman, kuning menipis, merah habis). */
fun warnaStatus(status: StatusStok): Color = when (status) {
    StatusStok.AMAN -> Color(0xFF2E7D32)
    StatusStok.MENIPIS -> Color(0xFFF9A825)
    StatusStok.HABIS -> Color(0xFFC62828)
}

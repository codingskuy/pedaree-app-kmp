package com.pedaree.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pedaree.shared.api.HasilHttp
import kotlinx.coroutines.launch

/**
 * Layar masuk. TIDAK ada layar daftar: akun dan rumah tangga dibuat operator/admin (keputusan v1.0.0).
 * Sesi: akses 15 menit + refresh 7 hari, dikelola otomatis oleh klien.
 */
@Composable
fun LayarLogin(deps: Deps) {
    var id by remember { mutableStateOf("") }
    var sandi by remember { mutableStateOf("") }
    var galat by remember { mutableStateOf<String?>(null) }
    var proses by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("Pedaree", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("login_judul"))
        OutlinedTextField(id, { id = it }, label = { Text("ID pengguna") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("login_id"))
        OutlinedTextField(
            sandi, { sandi = it }, label = { Text("Kata sandi") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("login_sandi"),
        )
        galat?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("login_galat")) }
        Button(
            onClick = {
                scope.launch {
                    proses = true
                    galat = when (val h = deps.masuk(id.trim(), sandi)) {
                        is HasilHttp.Sukses -> null
                        is HasilHttp.Ditolak -> if (h.status == 401) "ID pengguna atau kata sandi salah" else h.galat.pesan
                        is HasilHttp.Sementara -> "Tidak dapat terhubung. Periksa koneksi lalu coba lagi."
                        HasilHttp.SesiHabis -> "Sesi tidak valid"
                    }
                    proses = false
                }
            },
            enabled = id.isNotBlank() && sandi.isNotEmpty() && !proses,
            modifier = Modifier.fillMaxWidth().testTag("login_masuk"),
        ) { Text(if (proses) "Masuk..." else "Masuk") }
        Text("Akun dibuat oleh admin. Hubungi admin bila belum punya akun.", style = MaterialTheme.typography.bodySmall)
    }
}

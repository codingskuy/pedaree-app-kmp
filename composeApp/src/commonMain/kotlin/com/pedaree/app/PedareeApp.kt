package com.pedaree.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.pedaree.shared.nav.Rute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Akar aplikasi. Navigation3: back stack adalah daftar [Rute] yang diamati Compose;
 * [NavDisplay] merender entri teratas dan memanggil [onBack] untuk gestur kembali (ADR-0003).
 */
@Composable
fun PedareeApp(deps: Deps) {
    val tumpukan = remember { mutableStateListOf<Rute>(Rute.Dapur) }
    val buka: (Rute) -> Unit = { tujuan -> if (tumpukan.last() != tujuan) tumpukan.add(tujuan) }
    val kembali: () -> Unit = { if (tumpukan.size > 1) tumpukan.removeAt(tumpukan.lastIndex) }
    val masuk by (deps.sesi?.masuk ?: remember { MutableStateFlow(true) }).collectAsState()
    PedareeTema {
        if (!masuk) {
            LayarLogin(deps)
            return@PedareeTema
        }
        // Sinkron antrean luring: saat aplikasi aktif lalu berkala (ASUMSI: 60 dtk; latar belakang/WorkManager belum ada).
        LaunchedEffect(Unit) {
            while (true) {
                val sebelum = deps.status.value.menunggu
                deps.kuras()
                if (deps.status.value.menunggu < sebelum) deps.dataBerubah()
                deps.evaluasiNotifikasi() // hanya mengirim di jendela 06:30-20:00 WIB
                delay(60_000)
            }
        }
        NavDisplay(
            backStack = tumpukan,
            onBack = kembali,
            entryProvider = { rute ->
                when (rute) {
                    is Rute.Dapur -> NavEntry(rute) { LayarDapur(deps, bukaItem = { buka(Rute.DetailItem(it)) }) }
                    is Rute.DetailItem -> NavEntry(rute) {
                        LayarDetailItem(
                            deps, rute.itemId,
                            aksi = AksiDetail(
                                masuk = { buka(Rute.CatatMasuk(rute.itemId)) },
                                pakai = { buka(Rute.CatatPakai(rute.itemId)) },
                                opname = { buka(Rute.Opname(rute.itemId)) },
                                pindah = { buka(Rute.Pindah(rute.itemId, it)) },
                                buang = { buka(Rute.Buang(rute.itemId, it)) },
                            ),
                            kembali = kembali,
                        )
                    }
                    is Rute.CatatMasuk -> NavEntry(rute) { LayarCatatMasuk(deps, rute.itemId, kembali) }
                    is Rute.CatatPakai -> NavEntry(rute) { LayarCatatPakai(deps, rute.itemId, kembali) }
                    is Rute.Opname -> NavEntry(rute) { LayarOpname(deps, rute.itemId, kembali) }
                    is Rute.Pindah -> NavEntry(rute) { LayarPindah(deps, rute.itemId, rute.batchId, kembali) }
                    is Rute.Buang -> NavEntry(rute) { LayarBuang(deps, rute.itemId, rute.batchId, kembali) }
                }
            },
        )
    }
}

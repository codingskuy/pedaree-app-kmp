package com.pedaree.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.nav.Rute

/**
 * Akar aplikasi. Navigation3: back stack adalah daftar [Rute] yang diamati Compose;
 * [NavDisplay] merender entri teratas dan memanggil [onBack] untuk gestur kembali.
 */
@Composable
fun PedareeApp(sumber: SumberDapur) {
    val tumpukan = remember { mutableStateListOf<Rute>(Rute.Dapur) }
    PedareeTema {
        NavDisplay(
            backStack = tumpukan,
            onBack = { if (tumpukan.size > 1) tumpukan.removeAt(tumpukan.lastIndex) },
            entryProvider = { rute ->
                when (rute) {
                    is Rute.Dapur -> NavEntry(rute) {
                        LayarDapur(
                            sumber = sumber,
                            bukaItem = { id ->
                                val tujuan = Rute.DetailItem(id)
                                if (tumpukan.last() != tujuan) tumpukan.add(tujuan)
                            },
                        )
                    }
                    is Rute.DetailItem -> NavEntry(rute) {
                        LayarDetailItem(
                            sumber = sumber,
                            itemId = rute.itemId,
                            kembali = { if (tumpukan.size > 1) tumpukan.removeAt(tumpukan.lastIndex) },
                        )
                    }
                }
            },
        )
    }
}

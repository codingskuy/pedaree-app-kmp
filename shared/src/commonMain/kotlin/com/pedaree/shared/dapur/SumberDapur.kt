package com.pedaree.shared.dapur

import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.PantryItemRingkas

/** Sumber data layar Dapur dan Detail. Implementasi nyata (cache lokal + API) hadir di PDR-KMP-005. */
interface SumberDapur {
    suspend fun daftarItem(): List<PantryItemRingkas>
    suspend fun detailItem(id: String): PantryItemDetail?
}

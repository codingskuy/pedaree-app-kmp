package com.pedaree.shared.data

import com.pedaree.shared.api.HasilHttp
import com.pedaree.shared.api.KlienApi
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.db.PedareeDb
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.PantryItemRingkas
import com.pedaree.shared.kontrak.JsonPedaree
import kotlinx.serialization.Serializable

@Serializable
private data class Halaman(val data: List<PantryItemRingkas> = emptyList(), val nextCursor: String? = null)

/** Gagal baca dari server DAN tidak ada cache lokal. */
class DataTidakTersedia(pesan: String) : Exception(pesan)

/**
 * Sumber data nyata layar Dapur/Detail: coba server dulu (lalu perbarui cache SQLDelight); bila luring atau
 * server gagal, jatuh ke cache. Pembacaan tidak pernah memblokir pada antrean tulis.
 */
class RepositoriDapur(
    db: PedareeDb,
    private val api: KlienApi,
    private val sekarang: () -> Long,
) : SumberDapur {
    private val cache = db.cacheQueries

    /** `true` bila data terakhir yang ditampilkan berasal dari cache (luring). */
    var dariCache: Boolean = false
        private set

    override suspend fun daftarItem(): List<PantryItemRingkas> {
        val segar = ambilSemuaHalaman()
        if (segar != null) {
            cache.transaction {
                cache.hapusRingkas()
                segar.forEachIndexed { i, item ->
                    cache.simpanRingkas(item.id, i.toLong(), JsonPedaree.encodeToString(PantryItemRingkas.serializer(), item))
                }
            }
            dariCache = false
            return segar
        }
        val tersimpan = cache.semuaRingkas().executeAsList()
        if (tersimpan.isEmpty()) throw DataTidakTersedia("Tidak ada koneksi dan belum ada data tersimpan")
        dariCache = true
        return tersimpan.map { JsonPedaree.decodeFromString(PantryItemRingkas.serializer(), it) }
    }

    override suspend fun detailItem(id: String): PantryItemDetail? {
        when (val h = api.ambil("/pantry/items/$id")) {
            is HasilHttp.Sukses -> {
                val item = JsonPedaree.decodeFromString(PantryItemDetail.serializer(), h.badan)
                cache.simpanDetail(id, h.badan, sekarang())
                dariCache = false
                return item
            }
            is HasilHttp.Ditolak -> if (h.status == 404) return null
            else -> Unit
        }
        val json = cache.detail(id).executeAsOneOrNull() ?: throw DataTidakTersedia("Tidak ada koneksi dan item belum tersimpan")
        dariCache = true
        return JsonPedaree.decodeFromString(PantryItemDetail.serializer(), json)
    }

    /** Ikuti `nextCursor` sampai habis; `null` bila ada halaman yang gagal (jangan menimpa cache dengan data separuh). */
    private suspend fun ambilSemuaHalaman(): List<PantryItemRingkas>? {
        val hasil = mutableListOf<PantryItemRingkas>()
        var kursor: String? = null
        do {
            val kueri = buildMap { put("limit", "100"); kursor?.let { put("cursor", it) } }
            val h = api.ambil("/pantry/items", kueri) as? HasilHttp.Sukses ?: return null
            val halaman = runCatching { JsonPedaree.decodeFromString(Halaman.serializer(), h.badan) }.getOrNull() ?: return null
            hasil += halaman.data
            kursor = halaman.nextCursor
        } while (kursor != null)
        return hasil
    }
}

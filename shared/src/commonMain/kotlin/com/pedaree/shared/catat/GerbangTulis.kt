package com.pedaree.shared.catat

import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.kontrak.PerintahTulis

/**
 * Pintu keluar semua alur catat stok. Implementasi nyata (antrean SQLDelight + klien Ktor + `X-Request-Id`)
 * ada di PDR-KMP-005; alur catat hanya mengenal antarmuka ini sehingga berjalan sama daring maupun luring.
 */
interface GerbangTulis {
    suspend fun kirim(perintah: PerintahTulis): HasilTulis
}

sealed interface HasilTulis {
    /** Server menjawab sukses; [badan] adalah JSON respons asli (didekode pemanggil bila perlu). */
    data class Terkirim(val badan: String) : HasilTulis

    /** Tersimpan di antrean luring; akan dikirim ulang idempoten saat daring. */
    data object Diantrekan : HasilTulis

    /** Ditolak server dengan galat baku (mis. `STOK_HABIS`, `KONFLIK_OPNAME`). Tidak diantrekan ulang. */
    data class Ditolak(val galat: GalatApi) : HasilTulis
}

/** Pencatat dalam memori untuk pengembangan/uji: menyimpan perintah dan membalas [balasan]. */
class GerbangTulisMemori(private val balasan: (PerintahTulis) -> HasilTulis = { HasilTulis.Diantrekan }) : GerbangTulis {
    private val _terkirim = mutableListOf<PerintahTulis>()
    val terkirim: List<PerintahTulis> get() = _terkirim.toList()

    override suspend fun kirim(perintah: PerintahTulis): HasilTulis {
        _terkirim.add(perintah)
        return balasan(perintah)
    }
}

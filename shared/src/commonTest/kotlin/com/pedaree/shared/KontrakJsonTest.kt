package com.pedaree.shared

import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.domain.Kategori
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Contoh diambil dari openapi.yaml pedaree-backend-service#9 dan `inventori/10-model-stok-dapur.md`. */
class KontrakJsonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun stokBatchDariContohKontrak() {
        val b = json.decodeFromString<StokBatch>(
            """{"id":"btc_01J9Z8QK2M4C","pantryItemId":"itm_01J9Z8QK2M4A","jumlahAwal":1000,"jumlahAktif":650,
            "tanggalMasuk":"2026-09-28","tanggalKedaluwarsa":"2026-10-19","lokasi":"BASKOM_BAWANG",
            "sumber":"MASUK_BELANJA","status":"AKTIF","dibuatPada":"2026-09-28T09:15:00Z"}""",
        )
        assertEquals(650.0, b.jumlahAktif)
        assertEquals(LokasiSimpan.BASKOM_BAWANG, b.lokasi)
        assertEquals(StatusBatch.AKTIF, b.status)
    }

    @Test fun tanggalKedaluwarsaNullDiterima() {
        val b = json.decodeFromString<StokBatch>(
            """{"id":"btc_1","pantryItemId":"itm_1","jumlahAwal":500,"jumlahAktif":500,"tanggalMasuk":"2026-08-01",
            "tanggalKedaluwarsa":null,"lokasi":"SUHU_RUANG","sumber":"MASUK_BELANJA","status":"AKTIF",
            "dibuatPada":"2026-08-01T03:00:00Z"}""",
        )
        assertNull(b.tanggalKedaluwarsa)
    }

    @Test fun satuanDanAlasanBuangPakaiKodeKabel() {
        assertEquals(Satuan.BONGGOL, json.decodeFromString<Satuan>("\"bonggol\""))
        assertEquals("\"berlendir/berbau\"", json.encodeToString(AlasanBuang.serializer(), AlasanBuang.BERLENDIR_BERBAU))
        assertEquals("\"lewat tanggal\"", json.encodeToString(AlasanBuang.serializer(), AlasanBuang.LEWAT_TANGGAL))
        assertEquals(13, Satuan.entries.size)
        assertEquals(8, Kategori.entries.size)
    }

    @Test fun galatBaku() {
        val g = json.decodeFromString<GalatApi>("""{"kode":"SATUAN_TIDAK_BERLAKU","pesan":"satuan tidak berlaku untuk komoditas ini","field":"satuan"}""")
        assertEquals("SATUAN_TIDAK_BERLAKU", g.kode)
        assertEquals("satuan", g.field)
    }
}

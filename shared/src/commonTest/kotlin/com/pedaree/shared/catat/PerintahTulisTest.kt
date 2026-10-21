package com.pedaree.shared.catat

import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.SumberBatch
import com.pedaree.shared.kontrak.BatchBuat
import com.pedaree.shared.kontrak.BuangRequest
import com.pedaree.shared.kontrak.JsonPedaree
import com.pedaree.shared.kontrak.KeranjangBaris
import com.pedaree.shared.kontrak.KeranjangTitipo
import com.pedaree.shared.kontrak.OpnameRequest
import com.pedaree.shared.kontrak.PemakaianBaris
import com.pedaree.shared.kontrak.PemakaianHasil
import com.pedaree.shared.kontrak.PemakaianRequest
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.kontrak.PindahRequest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Jalur dan badan JSON harus identik dengan contoh di openapi.yaml pedaree-backend-service#9. */
class PerintahTulisTest {
    /** Normalisasi angka: `1000` dan `1000.0` setara pada JSON (nilai, bukan teks). */
    private fun norm(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { norm(it.value) })
        is JsonArray -> JsonArray(e.map { norm(it) })
        is JsonPrimitive -> if (!e.isString && e.doubleOrNull != null) JsonPrimitive(e.double) else e
    }

    private fun json(s: String): JsonObject = norm(Json.parseToJsonElement(s)) as JsonObject

    @Test fun catatMasukSamaDenganContohKontrak() {
        val p = PerintahTulis.MasukBatch(
            BatchBuat(
                "itm_01J9Z8QK2M4A", 1000.0, Satuan.GRAM, LocalDate(2026, 9, 28), LocalDate(2026, 10, 19),
                LokasiSimpan.BASKOM_BAWANG, SumberBatch.MASUK_BELANJA,
            ),
        )
        assertEquals("/pantry/batches", p.jalur)
        assertEquals(
            json("""{"pantryItemId":"itm_01J9Z8QK2M4A","jumlah":1000,"satuan":"gram","tanggalMasuk":"2026-09-28","tanggalKedaluwarsa":"2026-10-19","lokasi":"BASKOM_BAWANG","sumber":"MASUK_BELANJA"}"""),
            json(p.badanJson()),
        )
    }

    @Test fun kedaluwarsaNullDihilangkanDariBadan() {
        val p = PerintahTulis.MasukBatch(
            BatchBuat("itm_1", 500.0, Satuan.GRAM, HARI_INI, null, LokasiSimpan.SUHU_RUANG, SumberBatch.MASUK_BELANJA),
        )
        assertFalse("tanggalKedaluwarsa" in json(p.badanJson()))
    }

    @Test fun catatPakaiSamaDenganContohKontrak() {
        val p = PerintahTulis.PakaiStok(PemakaianRequest(listOf(PemakaianBaris("itm_01J9Z8QK2M4A", 200.0, Satuan.GRAM))))
        assertEquals("/pantry/consume", p.jalur)
        assertEquals(
            json("""{"pemakaian":[{"pantryItemId":"itm_01J9Z8QK2M4A","jumlah":200,"satuan":"gram"}]}"""),
            json(p.badanJson()),
        )
    }

    @Test fun sumberResepIdDikirimBilaAda() {
        val p = PerintahTulis.PakaiStok(PemakaianRequest(listOf(PemakaianBaris("itm_1", 1.0, Satuan.PCS)), "pwn_r1234"))
        assertEquals(JsonPrimitive("pwn_r1234"), json(p.badanJson())["sumberResepId"])
    }

    @Test fun opnameSamaDenganContohKontrak() {
        val p = PerintahTulis.OpnameStok(OpnameRequest("itm_01J9Z8QK2M4A", 650.0, Satuan.GRAM, "susut bayam"))
        assertEquals("/pantry/opname", p.jalur)
        assertEquals(
            json("""{"pantryItemId":"itm_01J9Z8QK2M4A","stokFisik":650,"satuan":"gram","alasan":"susut bayam"}"""),
            json(p.badanJson()),
        )
    }

    @Test fun pindahDanBuangSamaDenganContohKontrak() {
        val pindah = PerintahTulis.PindahBatch("btc_01J9Z8QK2M4C", PindahRequest(LokasiSimpan.KULKAS_BAWAH))
        assertEquals("/pantry/batches/btc_01J9Z8QK2M4C/pindah", pindah.jalur)
        assertEquals(json("""{"lokasiTujuan":"KULKAS_BAWAH"}"""), json(pindah.badanJson()))

        val buang = PerintahTulis.BuangBatch("btc_01J9Z8QK2M4C", BuangRequest(150.0, Satuan.GRAM, AlasanBuang.LEWAT_TANGGAL))
        assertEquals("/pantry/batches/btc_01J9Z8QK2M4C/buang", buang.jalur)
        assertEquals(json("""{"jumlah":150,"satuan":"gram","alasan":"lewat tanggal"}"""), json(buang.badanJson()))
    }

    @Test fun titipoSamaDenganContohKontrak() {
        val p = PerintahTulis.KeTitipo(KeranjangTitipo(listOf(KeranjangBaris("itm_01J9Z8QK2M4A", 500.0, Satuan.GRAM))))
        assertEquals("/demand/titipo", p.jalur)
        assertEquals(
            json("""{"items":[{"pantryItemId":"itm_01J9Z8QK2M4A","jumlah":500,"satuan":"gram"}]}"""),
            json(p.badanJson()),
        )
    }

    @Test fun responsPakaiDenganKurangDidekode() {
        val h = JsonPedaree.decodeFromString(
            PemakaianHasil.serializer(),
            """{"terpotong":[{"batchId":"btc_1","jumlah":100}],"kurang":[{"pantryItemId":"itm_1","kurangJumlah":200,"satuanSimpan":"gram"}]}""",
        )
        assertEquals(200.0, h.kurang.single().kurangJumlah)
        assertEquals("btc_1", h.terpotong.single().batchId)
    }
}

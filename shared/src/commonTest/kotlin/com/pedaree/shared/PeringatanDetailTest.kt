package com.pedaree.shared

import com.pedaree.shared.catat.HARI_INI
import com.pedaree.shared.catat.batchUji
import com.pedaree.shared.catat.itemUji
import com.pedaree.shared.dapur.DetailItemState
import com.pedaree.shared.dapur.peringatan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.notif.AksiSop
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeringatanDetailTest {
    private fun state(vararg batch: com.pedaree.shared.domain.StokBatch) =
        DetailItemState(memuat = false, item = itemUji(nama = "Bayam", simpan = Satuan.GRAM, batches = batch.toList()))

    private fun hari(n: Int) = LocalDate(2026, 10, 21 + n)

    @Test fun amanTidakMemunculkanBanner() {
        assertTrue(state(batchUji("a", 200.0, hari(8))).peringatan(HARI_INI).isEmpty())
        assertTrue(state(batchUji("a", 200.0, null)).peringatan(HARI_INI).isEmpty())
    }

    @Test fun statusDanAksiSesuaiSopPerBatchTerparahDulu() {
        val s = state(
            batchUji("waspada", 100.0, hari(5)),
            batchUji("segera", 100.0, hari(2)),
            batchUji("hariini", 100.0, hari(0)),
            batchUji("lewat", 100.0, hari(-1), status = StatusBatch.KEDALUWARSA),
        )
        val p = s.peringatan(HARI_INI)
        assertEquals(listOf("lewat", "hariini", "segera", "waspada"), p.map { it.batch.id })
        assertEquals(setOf(AksiSop.BUANG_DAN_CATAT), p[0].aksi)
        assertEquals(setOf(AksiSop.CATAT_PAKAI, AksiSop.BUANG), p[1].aksi)
        assertEquals(setOf(AksiSop.LIHAT_RESEP, AksiSop.CATAT_PAKAI), p[2].aksi)
        assertEquals(setOf(AksiSop.LIHAT_RESEP), p[3].aksi)
        assertEquals(StatusKedaluwarsa.KEDALUWARSA, p[0].status)
    }

    @Test fun pesanMemakaiTemplateBaku() {
        val p = state(batchUji("segera", 650.0, hari(2))).peringatan(HARI_INI).single()
        assertEquals("[Bayam] sisa 650 g — tinggal 2 hari. Lihat resep Pawonee atau catat pakai.", p.pesan)
    }

    @Test fun batchKosongDanDibuangDiabaikan() {
        val s = state(
            batchUji("kosong", 0.0, hari(0), status = StatusBatch.HABIS),
            batchUji("dibuang", 50.0, hari(0), status = StatusBatch.DIBUANG),
        )
        assertTrue(s.peringatan(HARI_INI).isEmpty())
    }

    @Test fun statusDariTanggalBukanFieldStatusBatch() {
        // Field status masih AKTIF tetapi tanggal sudah lewat: tetap KEDALUWARSA (cache basi setelah ganti hari).
        val p = state(batchUji("basi", 100.0, hari(-3))).peringatan(HARI_INI).single()
        assertEquals(StatusKedaluwarsa.KEDALUWARSA, p.status)
    }
}

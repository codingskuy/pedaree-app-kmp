package com.pedaree.shared

import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.domain.StatusStok
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.domain.SumberBatch
import com.pedaree.shared.stok.HitungStok
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HitungStokTest {
    private val hariIni = LocalDate(2026, 10, 21)

    private fun batch(id: String, aktif: Double, exp: LocalDate?, status: StatusBatch = StatusBatch.AKTIF) = StokBatch(
        id = id, pantryItemId = "itm_1", jumlahAwal = 1000.0, jumlahAktif = aktif,
        tanggalMasuk = LocalDate(2026, 10, 1), tanggalKedaluwarsa = exp, lokasi = LokasiSimpan.KULKAS_BAWAH,
        sumber = SumberBatch.MASUK_BELANJA, status = status, dibuatPada = Instant.parse("2026-10-01T00:00:00Z"),
    )

    @Test fun stokTampilHanyaBatchYangDapatDipakai() {
        val batches = listOf(
            batch("a", 300.0, LocalDate(2026, 10, 25)),
            batch("b", 200.0, hariIni), // kedaluwarsa hari ini masih boleh
            batch("c", 500.0, LocalDate(2026, 10, 20)), // lewat tanggal
            batch("d", 100.0, null),
            batch("e", 0.0, LocalDate(2026, 11, 1), StatusBatch.HABIS),
            batch("f", 50.0, LocalDate(2026, 11, 1), StatusBatch.DIBUANG),
        )
        assertEquals(600.0, HitungStok.stokTampil(batches, hariIni))
    }

    @Test fun statusStokBatasMinimum() {
        assertEquals(StatusStok.HABIS, HitungStok.statusStok(0.0, 250.0))
        assertEquals(StatusStok.MENIPIS, HitungStok.statusStok(250.0, 250.0))
        assertEquals(StatusStok.MENIPIS, HitungStok.statusStok(0.1, 250.0))
        assertEquals(StatusStok.AMAN, HitungStok.statusStok(250.1, 250.0))
    }

    @Test fun ambangSopKedaluwarsa() {
        fun s(hari: Int) = HitungStok.statusKedaluwarsa(LocalDate(2026, 10, 21 + hari).let { it }, hariIni)
        assertEquals(StatusKedaluwarsa.AMAN, s(8))
        assertEquals(StatusKedaluwarsa.WASPADA, s(7))
        assertEquals(StatusKedaluwarsa.WASPADA, s(4))
        assertEquals(StatusKedaluwarsa.SEGERA, s(3))
        assertEquals(StatusKedaluwarsa.SEGERA, s(1))
        assertEquals(StatusKedaluwarsa.HARI_INI, s(0))
        assertEquals(StatusKedaluwarsa.KEDALUWARSA, s(-1))
        assertNull(HitungStok.statusKedaluwarsa(null, hariIni))
    }
}

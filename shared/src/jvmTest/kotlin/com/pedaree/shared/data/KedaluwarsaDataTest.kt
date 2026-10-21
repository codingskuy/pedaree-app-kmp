package com.pedaree.shared.data

import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.kontrak.BuangRequest
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.kontrak.PindahRequest
import com.pedaree.shared.notif.Notifikasi
import com.pedaree.shared.notif.PengirimNotifikasi
import com.pedaree.shared.notif.RiwayatNotif
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun expiringJson(vararg baris: String) = """{"data":[${baris.joinToString(",")}]}"""

private fun baris(item: String, batch: String, tanggal: String, nama: String = "Bayam", sisa: Int = 200) =
    """{"batchId":"$batch","pantryItemId":"$item","nama":"$nama","sisa":$sisa,"satuanTampil":"gram","tanggalKedaluwarsa":"$tanggal","status":"SEGERA"}"""

class RepositoriKedaluwarsaTest {
    private val db = dbBaru()

    @Test fun mengambilDaftarDanMenyimpanCache() = runTest {
        val server = ServerTiruan { json(expiringJson(baris("i1", "b1", "2026-10-23"))) }
        val repo = RepositoriKedaluwarsa(db, server.klien())
        val h = repo.muat()
        assertTrue(h.segar)
        assertEquals("b1", h.data.single().batchId)
        assertEquals("/v1/pantry/expiring?dalamHari=7", server.permintaan.single().jalur)
        assertTrue(server.permintaan.single().header.containsKey("X-Request-Id"))
    }

    @Test fun luringMembacaCacheDanTanpaCacheKosongBukanGalat() = runTest {
        var daring = true
        val server = ServerTiruan { if (daring) json(expiringJson(baris("i1", "b1", "2026-10-23"))) else putusJaringan() }
        val kosong = RepositoriKedaluwarsa(dbBaru(), ServerTiruan { putusJaringan() }.klien()).muat()
        assertFalse(kosong.adaData)
        assertTrue(kosong.data.isEmpty())

        val repo = RepositoriKedaluwarsa(db, server.klien())
        repo.muat()
        daring = false
        val luring = repo.muat()
        assertFalse(luring.segar)
        assertTrue(luring.adaData)
        assertEquals("b1", luring.data.single().batchId)
    }
}

class LogBuangTest {
    private val db = dbBaru()
    private val log = LogBuang(db)

    private fun buang(jumlah: Double, satuan: Satuan = Satuan.GRAM) =
        PerintahTulis.BuangBatch("btc_1", BuangRequest(jumlah, satuan, AlasanBuang.LEWAT_TANGGAL))

    @Test fun rekapBulanBerjalanPerSatuan() {
        log.catat("r1", buang(150.0), LocalDate(2026, 10, 3))
        log.catat("r2", buang(200.0), LocalDate(2026, 10, 31))
        log.catat("r3", buang(2.0, Satuan.IKAT), LocalDate(2026, 10, 15))
        log.catat("r4", buang(999.0), LocalDate(2026, 9, 30)) // bulan lalu
        log.catat("r5", buang(999.0), LocalDate(2026, 11, 1)) // bulan depan
        val r = log.bulanIni(LocalDate(2026, 10, 21))
        assertEquals(3, r.kali)
        assertEquals(350.0, r.totalPerSatuan[Satuan.GRAM])
        assertEquals(2.0, r.totalPerSatuan[Satuan.IKAT])
    }

    @Test fun hanyaPerintahBuangDicatatDanIdSamaTidakGanda() {
        log.catat("r1", PerintahTulis.PindahBatch("btc_1", PindahRequest(LokasiSimpan.FREEZER)), LocalDate(2026, 10, 3))
        log.catat("r2", buang(10.0), LocalDate(2026, 10, 3))
        log.catat("r2", buang(10.0), LocalDate(2026, 10, 3)) // kirim ulang tidak menggandakan
        assertEquals(1, log.bulanIni(LocalDate(2026, 10, 21)).kali)
    }

    @Test fun batalkanMenghapusCatatanYangDitolakServer() {
        log.catat("r1", buang(10.0), LocalDate(2026, 10, 3))
        log.batalkan("r1")
        assertEquals(0, log.bulanIni(LocalDate(2026, 10, 21)).kali)
    }

    @Test fun desemberMelewatiPergantianTahun() {
        log.catat("r1", buang(10.0), LocalDate(2026, 12, 31))
        assertEquals(1, log.bulanIni(LocalDate(2026, 12, 1)).kali)
    }
}

class PemicuNotifikasiTest {
    private val db = dbBaru()
    private val terkirim = mutableListOf<Notifikasi>()
    private val pengirim = object : PengirimNotifikasi {
        override fun tampilkan(notifikasi: Notifikasi) { terkirim += notifikasi }
    }
    private var sekarang = Instant.parse("2026-10-21T10:00:00+07:00")
    private val riwayat = PenyimpanRiwayatNotif(db)

    private fun pemicu(server: ServerTiruan) =
        PemicuNotifikasi(RepositoriKedaluwarsa(db, server.klien()), riwayat, pengirim) { sekarang }

    @Test fun diDalamJendelaMengirimLaluTidakMengulangDiHariYangSama() = runTest {
        val server = ServerTiruan { json(expiringJson(baris("i1", "b1", "2026-10-23"))) }
        val p = pemicu(server)
        assertEquals(1, p.evaluasi().size)
        assertEquals(StatusKedaluwarsa.SEGERA, terkirim.single().status)
        assertEquals(0, p.evaluasi().size) // evaluasi kedua: sudah dikirim
        assertEquals(1, terkirim.size)
        assertEquals(RiwayatNotif(StatusKedaluwarsa.SEGERA, LocalDate(2026, 10, 21)), riwayat.baca()["i1"])
    }

    @Test fun diLuarJendelaTidakMengirimDanTidakMengubahRiwayatLaluTerkirimSetelah0630() = runTest {
        val server = ServerTiruan { json(expiringJson(baris("i1", "b1", "2026-10-23"))) }
        val p = pemicu(server)
        sekarang = Instant.parse("2026-10-21T05:00:00+07:00")
        assertTrue(p.evaluasi().isEmpty())
        assertTrue(riwayat.baca().isEmpty())
        assertTrue(server.permintaan.isEmpty()) // bahkan tidak memanggil server di luar jendela

        sekarang = Instant.parse("2026-10-21T06:30:00+07:00")
        assertEquals(1, p.evaluasi().size) // pesan tertahan terkirim pada evaluasi pertama dalam jendela
    }

    @Test fun luringTanpaDataTidakMengirimApaPun() = runTest {
        val p = pemicu(ServerTiruan { putusJaringan() })
        assertTrue(p.evaluasi().isEmpty())
    }

    @Test fun penyimpanRiwayatBolehMenghapusDanMemperbarui() {
        riwayat.simpan(mapOf("a" to RiwayatNotif(StatusKedaluwarsa.WASPADA, LocalDate(2026, 10, 20)), "b" to RiwayatNotif(StatusKedaluwarsa.SEGERA, LocalDate(2026, 10, 20))))
        riwayat.simpan(mapOf("a" to RiwayatNotif(StatusKedaluwarsa.SEGERA, LocalDate(2026, 10, 21))))
        assertEquals(setOf("a"), riwayat.baca().keys)
        assertEquals(StatusKedaluwarsa.SEGERA, riwayat.baca()["a"]?.status)
    }
}

class KartuDariGerbangTest {
    /** Buang yang diterima (diantrekan) tercatat untuk kartu; buang yang ditolak server tidak. */
    @Test fun buangDiterimaMasukKartuYangDitolakTidak() = runTest {
        val jam = Jam(Instant.parse("2026-10-21T10:00:00+07:00").toEpochMilliseconds())
        val db = dbBaru()
        val antrean = AntreanTulis(db, jam.baca)
        val log = LogBuang(db)
        val server = ServerTiruan { r ->
            if (r.jalur.endsWith("btc_ditolak/buang")) json("""{"kode":"JUMLAH_MELEBIHI_SISA","pesan":"melebihi sisa"}""", HttpStatusCode.UnprocessableEntity)
            else putusJaringan()
        }
        val pengirim = PengirimAntrean(antrean, server.klien(), jam.baca, setelahDitolak = { log.batalkan(it.requestId) })
        val gerbang = GerbangTulisOffline(
            antrean, pengirim,
            diterima = { e, p -> log.catat(e.requestId, p, LocalDate(2026, 10, 21)) },
        )
        gerbang.kirim(PerintahTulis.BuangBatch("btc_ok", BuangRequest(100.0, Satuan.GRAM, AlasanBuang.BUSUK))) // luring: diantrekan
        gerbang.kirim(PerintahTulis.BuangBatch("btc_ditolak", BuangRequest(999.0, Satuan.GRAM, AlasanBuang.BUSUK)))
        val r = log.bulanIni(LocalDate(2026, 10, 21))
        // Entri pertama tertahan (jaringan), entri kedua di belakangnya tidak dikirim, jadi keduanya "diantrekan".
        assertEquals(2, r.kali)
        assertEquals(1099.0, r.totalPerSatuan[Satuan.GRAM])

        // Setelah daring dan entri kedua ditolak saat sinkron, catatannya dibatalkan.
        val serverDaring = ServerTiruan { r2 ->
            if (r2.jalur.endsWith("btc_ditolak/buang")) json("""{"kode":"JUMLAH_MELEBIHI_SISA","pesan":"x"}""", HttpStatusCode.UnprocessableEntity)
            else json("{}")
        }
        jam.maju(60_001)
        PengirimAntrean(antrean, serverDaring.klien(), jam.baca, setelahDitolak = { log.batalkan(it.requestId) }).kuras()
        val akhir = log.bulanIni(LocalDate(2026, 10, 21))
        assertEquals(1, akhir.kali)
        assertEquals(100.0, akhir.totalPerSatuan[Satuan.GRAM])
    }
}

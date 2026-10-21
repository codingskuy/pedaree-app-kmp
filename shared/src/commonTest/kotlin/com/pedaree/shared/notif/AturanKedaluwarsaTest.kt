package com.pedaree.shared.notif

import com.pedaree.shared.domain.BatchKedaluwarsa
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusKedaluwarsa
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val HARI = LocalDate(2026, 10, 21)

private fun bk(
    item: String,
    batch: String,
    hari: Int,
    nama: String = "Bayam",
    sisa: Double = 200.0,
    satuan: Satuan = Satuan.GRAM,
) = BatchKedaluwarsa(
    batchId = batch, pantryItemId = item, nama = nama, sisa = sisa, satuanTampil = satuan,
    tanggalKedaluwarsa = LocalDate(2026, 10, 21 + hari), status = StatusKedaluwarsa.AMAN, // status server sengaja diabaikan
)

class PesanDanAksiTest {
    private val tgl = LocalDate(2026, 10, 19)

    @Test fun templatePesanBakuPerStatus() {
        assertEquals("[Bayam] sisa 200 g — kedaluwarsa 19 Oktober 2026. Cocok dimasak minggu ini.", PesanKedaluwarsa.isi(StatusKedaluwarsa.WASPADA, "Bayam", "200 g", tgl, 5))
        assertEquals("[Bayam] sisa 200 g — tinggal 2 hari. Lihat resep Pawonee atau catat pakai.", PesanKedaluwarsa.isi(StatusKedaluwarsa.SEGERA, "Bayam", "200 g", tgl, 2))
        assertEquals("[Bayam] sisa 200 g — pakai hari ini atau buang dan catat.", PesanKedaluwarsa.isi(StatusKedaluwarsa.HARI_INI, "Bayam", "200 g", tgl, 0))
        assertEquals("[Bayam] 200 g kedaluwarsa 19 Oktober 2026. Buang dan catat agar metrik waste akurat.", PesanKedaluwarsa.isi(StatusKedaluwarsa.KEDALUWARSA, "Bayam", "200 g", tgl, -2))
        assertNull(PesanKedaluwarsa.isi(StatusKedaluwarsa.AMAN, "Bayam", "200 g", tgl, 10))
    }

    @Test fun aksiWajibSesuaiTabelSop() {
        assertEquals(emptySet(), aksiWajib(StatusKedaluwarsa.AMAN))
        assertEquals(setOf(AksiSop.LIHAT_RESEP), aksiWajib(StatusKedaluwarsa.WASPADA))
        assertEquals(setOf(AksiSop.LIHAT_RESEP, AksiSop.CATAT_PAKAI), aksiWajib(StatusKedaluwarsa.SEGERA))
        assertEquals(setOf(AksiSop.CATAT_PAKAI, AksiSop.BUANG), aksiWajib(StatusKedaluwarsa.HARI_INI))
        assertEquals(setOf(AksiSop.BUANG_DAN_CATAT), aksiWajib(StatusKedaluwarsa.KEDALUWARSA))
    }

    @Test fun formatTanggalIndonesia() {
        assertEquals("1 Januari 2027", formatTanggalId(LocalDate(2027, 1, 1)))
        assertEquals("31 Desember 2026", formatTanggalId(LocalDate(2026, 12, 31)))
    }
}

class JendelaPushTest {
    private fun wib(jam: String) = Instant.parse("2026-10-21T$jam:00+07:00")

    @Test fun jendelaDari0630SampaiSebelum2000Wib() {
        assertFalse(JendelaPush.bolehKirim(wib("06:29")))
        assertTrue(JendelaPush.bolehKirim(wib("06:30")))
        assertTrue(JendelaPush.bolehKirim(wib("12:00")))
        assertTrue(JendelaPush.bolehKirim(wib("19:59")))
        assertFalse(JendelaPush.bolehKirim(wib("20:00")))
        assertFalse(JendelaPush.bolehKirim(wib("23:59")))
        assertFalse(JendelaPush.bolehKirim(wib("00:00")))
    }

    @Test fun diLuarJendelaAntreSampai0630() {
        assertEquals(wib("06:30"), JendelaPush.kirimPada(wib("05:00")))
        assertEquals(Instant.parse("2026-10-22T06:30:00+07:00"), JendelaPush.kirimPada(wib("21:00")))
        assertEquals(Instant.parse("2026-10-22T06:30:00+07:00"), JendelaPush.kirimPada(wib("20:00")))
        assertEquals(wib("10:15"), JendelaPush.kirimPada(wib("10:15")))
    }

    @Test fun tanggalWibMengikutiUtcPlusTujuh() {
        // 23:30 UTC tanggal 20 = 06:30 WIB tanggal 21
        assertEquals(LocalDate(2026, 10, 21), JendelaPush.tanggalWib(Instant.parse("2026-10-20T23:30:00Z")))
        assertEquals(LocalDate(2026, 10, 20), JendelaPush.tanggalWib(Instant.parse("2026-10-20T16:59:00Z")))
    }
}

class PemilihNotifikasiTest {
    @Test fun amanTidakMemicuPush() {
        val k = PemilihNotifikasi.pilih(listOf(bk("i1", "b1", 8)), emptyMap(), HARI)
        assertTrue(k.kirim.isEmpty())
        assertTrue(k.riwayatBaru.isEmpty())
    }

    @Test fun statusDihitungUlangBukanDariFieldServer() {
        // Field status server sengaja AMAN; tanggalnya H+2 -> SEGERA.
        val k = PemilihNotifikasi.pilih(listOf(bk("i1", "b1", 2)), emptyMap(), HARI)
        assertEquals(StatusKedaluwarsa.SEGERA, k.kirim.single().status)
        assertEquals("[Bayam] sisa 200 g — tinggal 2 hari. Lihat resep Pawonee atau catat pakai.", k.kirim.single().isi)
    }

    @Test fun hanyaKenaikanTingkatMemicuPush() {
        val kemarin = LocalDate(2026, 10, 20)
        // Kemarin sudah WASPADA; hari ini masih WASPADA (H+5): tidak ada push.
        var k = PemilihNotifikasi.pilih(listOf(bk("i1", "b1", 5)), mapOf("i1" to RiwayatNotif(StatusKedaluwarsa.WASPADA, kemarin)), HARI)
        assertTrue(k.kirim.isEmpty())
        // Hari ini naik ke SEGERA (H+3): push.
        k = PemilihNotifikasi.pilih(listOf(bk("i1", "b1", 3)), mapOf("i1" to RiwayatNotif(StatusKedaluwarsa.WASPADA, kemarin)), HARI)
        assertEquals(1, k.kirim.size)
        assertEquals(RiwayatNotif(StatusKedaluwarsa.SEGERA, HARI), k.riwayatBaru["i1"])
    }

    @Test fun maksimalSatuPushPerItemPerHariKenaikanDitundaKeHariBerikut() {
        val sudahHariIni = mapOf("i1" to RiwayatNotif(StatusKedaluwarsa.WASPADA, HARI))
        val k = PemilihNotifikasi.pilih(listOf(bk("i1", "b1", 0)), sudahHariIni, HARI) // naik ke HARI_INI di hari yang sama
        assertTrue(k.kirim.isEmpty())
        assertEquals(StatusKedaluwarsa.WASPADA, k.riwayatBaru["i1"]?.status) // tidak berubah -> besok tetap naik
        val besok = PemilihNotifikasi.pilih(listOf(bk("i1", "b1", 1)), k.riwayatBaru, LocalDate(2026, 10, 22)) // tanggal batch 22 Okt = hari ini
        assertEquals(StatusKedaluwarsa.HARI_INI, besok.kirim.single().status)
    }

    @Test fun batchTerparahMewakiliItem() {
        val k = PemilihNotifikasi.pilih(
            listOf(bk("i1", "lama", -3), bk("i1", "baru", 6), bk("i1", "tengah", 2)), emptyMap(), HARI,
        )
        val n = k.kirim.single()
        assertEquals(StatusKedaluwarsa.KEDALUWARSA, n.status)
        assertEquals("lama", n.batchId)
    }

    @Test fun turunTingkatMenurunkanRiwayatAgarKenaikanBerikutnyaMemicuLagi() {
        val riwayat = mapOf("i1" to RiwayatNotif(StatusKedaluwarsa.HARI_INI, LocalDate(2026, 10, 20)))
        // Batch terparah sudah dipakai; sisa batch baru WASPADA: riwayat turun.
        val turun = PemilihNotifikasi.pilih(listOf(bk("i1", "b2", 6)), riwayat, HARI)
        assertTrue(turun.kirim.isEmpty())
        assertEquals(StatusKedaluwarsa.WASPADA, turun.riwayatBaru["i1"]?.status)
        // Beberapa hari kemudian naik lagi ke SEGERA: push.
        val naik = PemilihNotifikasi.pilih(listOf(bk("i1", "b2", 3)), turun.riwayatBaru, HARI)
        assertEquals(StatusKedaluwarsa.SEGERA, naik.kirim.single().status)
    }

    @Test fun itemYangHilangDariDaftarDilupakan() {
        val riwayat = mapOf("hilang" to RiwayatNotif(StatusKedaluwarsa.SEGERA, HARI))
        assertTrue(PemilihNotifikasi.pilih(emptyList(), riwayat, HARI).riwayatBaru.isEmpty())
    }

    @Test fun beberapaItemUrutDariTerparah() {
        val k = PemilihNotifikasi.pilih(listOf(bk("a", "a1", 6), bk("b", "b1", 0), bk("c", "c1", -1)), emptyMap(), HARI)
        assertEquals(listOf("c", "b", "a"), k.kirim.map { it.itemId })
    }
}

class KartuRingkasTest {
    @Test fun hitungKartuDariTanggalBukanFieldServer() {
        val data = listOf(
            bk("a", "a1", 0), bk("b", "b1", 0), // HARI_INI x2
            bk("c", "c1", 1), bk("d", "d1", 3), bk("e", "e1", 2), // SEGERA x3
            bk("f", "f1", 5), // WASPADA: tidak dihitung di kartu
            bk("g", "g1", -2), // KEDALUWARSA: hanya badge
        )
        val kartu = hitungKartu(data, HARI, RingkasanBuang(2, mapOf(Satuan.GRAM to 350.0)))
        assertEquals(2, kartu.habisHariIni)
        assertEquals(3, kartu.segeraTigaHari)
        assertEquals(1, kartu.sudahKedaluwarsa)
        assertEquals(6, kartu.perluTindakan)
        assertEquals(2, kartu.buangBulanIni.kali)
    }

    @Test fun kartuKosong() {
        assertEquals(0, hitungKartu(emptyList(), HARI, RingkasanBuang()).perluTindakan)
    }

    @Test fun teksRingkasanBuang() {
        assertEquals("Belum ada", RingkasanBuang().teks())
        assertEquals("3 kali: 1.250 g, 2 ikat", RingkasanBuang(3, linkedMapOf(Satuan.GRAM to 1250.0, Satuan.IKAT to 2.0)).teks())
    }
}

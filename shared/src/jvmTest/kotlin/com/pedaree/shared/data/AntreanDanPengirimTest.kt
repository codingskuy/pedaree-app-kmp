package com.pedaree.shared.data

import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.SumberBatch
import com.pedaree.shared.kontrak.BatchBuat
import com.pedaree.shared.kontrak.BuangRequest
import com.pedaree.shared.kontrak.PindahRequest
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.domain.AlasanBuang
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun masuk(item: String = "itm_1", jumlah: Double = 100.0) = PerintahTulis.MasukBatch(
    BatchBuat(item, jumlah, Satuan.GRAM, LocalDate(2026, 10, 21), LocalDate(2026, 10, 28), LokasiSimpan.KULKAS_BAWAH, SumberBatch.MASUK_BELANJA),
)

class AntreanTulisTest {
    private val jam = Jam()
    private val db = dbBaru()
    private val antrean = AntreanTulis(db, jam.baca)

    @Test fun fifoDanRequestIdUnik() {
        val a = antrean.tambah(masuk("a"))
        val b = antrean.tambah(masuk("b"))
        assertNotEquals(a.requestId, b.requestId)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(a.requestId))
        assertEquals(a.seq, antrean.kepala()?.seq)
        antrean.hapus(a.seq)
        assertEquals(b.seq, antrean.kepala()?.seq)
        assertEquals(1, antrean.jumlahMenunggu())
    }

    @Test fun bertahanDiLuarInstansiSelamaDbSama() {
        val a = antrean.tambah(masuk("a"))
        val instansiBaru = AntreanTulis(db, jam.baca) // meniru aplikasi dibuka ulang: DB sama
        val kepala = instansiBaru.kepala()
        assertEquals(a.requestId, kepala?.requestId)
        assertEquals("/pantry/batches", kepala?.jalur)
        assertEquals(a.badan, kepala?.badan)
    }

    @Test fun jadwalCobaUlangSatuLimaLimaBelasLimaBelas() {
        assertEquals(60_000L, Penundaan.jeda(0))
        assertEquals(300_000L, Penundaan.jeda(1))
        assertEquals(900_000L, Penundaan.jeda(2))
        assertEquals(900_000L, Penundaan.jeda(3))
        assertEquals(900_000L, Penundaan.jeda(40))
    }

    @Test fun gagalSementaraMenjadwalkanUlangTanpaMengubahRequestId() {
        val a = antrean.tambah(masuk())
        antrean.gagalSementara(antrean.kepala()!!)
        var k = antrean.kepala()!!
        assertEquals(1, k.percobaan)
        assertEquals(jam.sekarang + 60_000L, k.cobaLagiPada)
        assertEquals(a.requestId, k.requestId)
        antrean.gagalSementara(k)
        k = antrean.kepala()!!
        assertEquals(2, k.percobaan)
        assertEquals(jam.sekarang + 300_000L, k.cobaLagiPada)
        assertEquals(a.requestId, k.requestId)
    }

    @Test fun ditolakKeluarDariAntreanKirimTapiDisimpanUntukPengguna() {
        val a = antrean.tambah(masuk("a"))
        val b = antrean.tambah(masuk("b"))
        antrean.tandaiDitolak(a.seq, GalatApi("STOK_HABIS", "stok habis"))
        assertEquals(b.seq, antrean.kepala()?.seq)
        assertEquals(1, antrean.jumlahMenunggu())
        assertEquals(1, antrean.jumlahDitolak())
        assertEquals("STOK_HABIS", antrean.ditolak().single().ditolak?.kode)
    }
}

class PengirimAntreanTest {
    private val jam = Jam()
    private val db = dbBaru()
    private var urut = 0
    private val antrean = AntreanTulis(db, jam.baca, buatId = { "req-${++urut}" })

    private fun pengirim(server: ServerTiruan, sesi: com.pedaree.shared.api.SesiAuth = sesiMasuk()) =
        PengirimAntrean(antrean, server.klien(sesi = sesi), jam.baca)

    @Test fun setiapPostMembawaHeaderWajibDanRequestIdDariAntrean() = runTest {
        val server = ServerTiruan { json("""{"id":"btc_1"}""", HttpStatusCode.Created) }
        antrean.tambah(masuk())
        val hasil = pengirim(server).kuras()
        assertEquals(1, hasil.terkirim)
        val r = server.permintaan.single()
        assertEquals("POST", r.metode)
        assertEquals("/v1/pantry/batches", r.jalur)
        assertEquals("req-1", r.header["X-Request-Id"])
        assertEquals("hh_1", r.header["X-Household-Id"])
        assertEquals("kmp-mobile/1.0.0", r.header["X-Client"])
        assertTrue(r.header["Authorization"]!!.startsWith("Bearer "))
        assertTrue(r.header.getValue("Content-Type").startsWith("application/json"))
        assertEquals(0, antrean.jumlahMenunggu())
    }

    /** Server tiruan idempoten: menyimpan hasil per X-Request-Id, mutasi dihitung sekali. */
    @Test fun kirimUlangDenganIdSamaTidakMenggandakanMutasi() = runTest {
        val tersimpan = mutableMapOf<String, String>()
        var mutasi = 0
        var responsHilang = true
        val server = ServerTiruan { r ->
            val id = r.header.getValue("X-Request-Id")
            val badan = tersimpan.getOrPut(id) { mutasi++; """{"id":"btc_$mutasi"}""" }
            if (responsHilang) { responsHilang = false; putusJaringan() } // diproses server, jawaban tak sampai
            json(badan, HttpStatusCode.Created)
        }
        antrean.tambah(masuk())
        val p = pengirim(server)
        assertTrue(p.kuras().tertahan) // percobaan 1: jaringan putus setelah server memproses
        assertEquals(1, antrean.jumlahMenunggu())

        jam.maju(60_001)
        assertEquals(1, p.kuras().terkirim) // percobaan 2: ID sama -> hasil asli, bukan mutasi baru
        assertEquals(1, mutasi)
        assertEquals(listOf("req-1", "req-1"), server.permintaan.map { it.header["X-Request-Id"] })
        assertEquals(0, antrean.jumlahMenunggu())
    }

    @Test fun gagalSementaraMenahanSeluruhAntreanSampaiJatuhTempo() = runTest {
        var hidup = false
        val server = ServerTiruan { if (hidup) json("{}", HttpStatusCode.Created) else json("{}", HttpStatusCode.ServiceUnavailable) }
        antrean.tambah(masuk("a"))
        antrean.tambah(masuk("b"))
        val p = pengirim(server)
        assertTrue(p.kuras().tertahan)
        assertEquals(1, server.permintaan.size) // entri kedua TIDAK dikirim mendahului kepala (urutan terjaga)

        assertTrue(p.kuras().tertahan) // belum jatuh tempo: tidak ada permintaan baru
        assertEquals(1, server.permintaan.size)

        hidup = true
        jam.maju(60_001)
        val h = p.kuras()
        assertEquals(2, h.terkirim)
        assertEquals(listOf("req-1", "req-1", "req-2"), server.permintaan.map { it.header["X-Request-Id"] })
    }

    @Test fun jaringanKe429DanTimeoutDiperlakukanSementara() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.RequestTimeout, HttpStatusCode.InternalServerError)) {
            val a = AntreanTulis(dbBaru(), jam.baca)
            a.tambah(masuk())
            val p = PengirimAntrean(a, ServerTiruan { json("{}", status) }.klien(), jam.baca)
            assertTrue(p.kuras().tertahan, "status $status")
            assertEquals(1, a.jumlahMenunggu())
        }
    }

    @Test fun ditolakServerDitandaiLaluLanjutKeBerikutnya() = runTest {
        val server = ServerTiruan { r ->
            if (r.header["X-Request-Id"] == "req-1") json("""{"kode":"STOK_HABIS","pesan":"stok habis","field":"jumlah"}""", HttpStatusCode.UnprocessableEntity)
            else json("{}", HttpStatusCode.OK)
        }
        antrean.tambah(masuk("a"))
        antrean.tambah(masuk("b"))
        val h = pengirim(server).kuras()
        assertEquals(1, h.terkirim)
        assertEquals(1, h.ditolak)
        assertEquals("STOK_HABIS", antrean.ditolak().single().ditolak?.kode)
        assertEquals(0, antrean.jumlahMenunggu())
        assertEquals(2, server.permintaan.size) // yang ditolak tidak dikirim ulang
    }

    @Test fun konflikIdempotensiDitolakBukanDiulang() = runTest {
        val server = ServerTiruan { json("""{"kode":"IDEMPOTENSI_BENTROK","pesan":"id dipakai operasi lain"}""", HttpStatusCode.Conflict) }
        antrean.tambah(masuk())
        pengirim(server).kuras()
        assertEquals(1, antrean.jumlahDitolak())
        assertEquals(1, server.permintaan.size)
    }

    @Test fun tokenKedaluwarsaDisegarkanSekaliLaluKirimUlangDenganIdSama() = runTest {
        val akses2 = jwtUji("hh_1") + "x"
        val server = ServerTiruan { r ->
            when {
                r.jalur.endsWith("/auth/refresh") -> json("""{"tokenAkses":"$akses2","tokenRefresh":"refresh_2","kedaluwarsaDetik":900,"audiens":"pedaree"}""")
                r.header["Authorization"] == "Bearer $akses2" -> json("{}", HttpStatusCode.Created)
                else -> json("""{"kode":"TIDAK_SAH","pesan":"Token tidak sah"}""", HttpStatusCode.Unauthorized)
            }
        }
        val sesi = sesiMasuk()
        antrean.tambah(masuk())
        val h = pengirim(server, sesi).kuras()
        assertEquals(1, h.terkirim)
        assertEquals(
            listOf("/v1/pantry/batches", "/v1/auth/refresh", "/v1/pantry/batches"),
            server.permintaan.map { it.jalur },
        )
        assertEquals("""{"tokenRefresh":"refresh_1"}""", server.permintaan[1].badan)
        assertNull(server.permintaan[1].header["Authorization"]) // refresh tidak memakai token akses
        assertEquals("req-1", server.permintaan[2].header["X-Request-Id"])
        assertEquals("refresh_2", sesi.tokenRefresh) // refresh dirotasi (sekali pakai)
    }

    @Test fun refreshKedaluwarsaMenghentikanAntreanTanpaMembuangEntri() = runTest {
        val server = ServerTiruan { r ->
            if (r.jalur.endsWith("/auth/refresh")) json("""{"kode":"REFRESH_KEDALUWARSA","pesan":"login ulang"}""", HttpStatusCode.Unauthorized)
            else json("""{"kode":"TIDAK_SAH","pesan":"x"}""", HttpStatusCode.Unauthorized)
        }
        val sesi = sesiMasuk()
        antrean.tambah(masuk())
        val h = pengirim(server, sesi).kuras()
        assertTrue(h.perluLogin)
        assertFalse(sesi.masuk.value)
        assertEquals(1, antrean.jumlahMenunggu()) // perubahan stok pengguna tidak hilang
    }

    @Test fun kurasSampaiMengembalikanHasilEntriYangDituju() = runTest {
        val server = ServerTiruan { json("""{"ok":true}""", HttpStatusCode.Created) }
        antrean.tambah(masuk("a"))
        val b = antrean.tambah(masuk("b"))
        val h = pengirim(server).kurasSampai(b.seq)
        assertEquals(PengirimAntrean.HasilEntri.Terkirim("""{"ok":true}"""), h)
        assertEquals(2, server.permintaan.size)
    }

    @Test fun pindahDanBuangDikirimKeJalurBatch() = runTest {
        val server = ServerTiruan { json("{}") }
        antrean.tambah(PerintahTulis.PindahBatch("btc_9", PindahRequest(LokasiSimpan.FREEZER)))
        antrean.tambah(PerintahTulis.BuangBatch("btc_9", BuangRequest(10.0, Satuan.GRAM, AlasanBuang.BUSUK)))
        pengirim(server).kuras()
        assertEquals(listOf("/v1/pantry/batches/btc_9/pindah", "/v1/pantry/batches/btc_9/buang"), server.permintaan.map { it.jalur })
    }
}

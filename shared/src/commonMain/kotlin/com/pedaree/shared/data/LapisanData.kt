package com.pedaree.shared.data

import app.cash.sqldelight.db.SqlDriver
import com.pedaree.shared.api.HasilHttp
import com.pedaree.shared.api.KlienApi
import com.pedaree.shared.api.SesiAuth
import com.pedaree.shared.catat.GerbangTulis
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.db.PedareeDb
import com.pedaree.shared.notif.KartuRingkas
import com.pedaree.shared.notif.PengirimNotifikasi
import com.pedaree.shared.notif.PengirimNotifikasiKosong
import com.pedaree.shared.notif.JendelaPush
import com.pedaree.shared.notif.hitungKartu
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant

/** Jumlah entri antrean tulis untuk banner sinkron. */
data class StatusAntrean(val menunggu: Long = 0, val ditolak: Long = 0)

/** URL basis produksi (openapi `servers`). Server lokal (`cargo run`) memakai `http://10.0.2.2:8080` tanpa `/v1`. */
const val URL_API_PRODUKSI = "https://api.pedaree.chefgenie.id/v1"

/**
 * Perakitan lapisan data offline-first: SQLDelight (cache + antrean) + klien REST + pengirim antrean.
 * UI hanya menyentuh [sumber] (baca, cache-first), [gerbang] (tulis, antre-dulu), [masuk], dan [kuras].
 */
class LapisanData(
    driver: SqlDriver,
    basisUrl: String,
    val sesi: SesiAuth,
    http: HttpClient = buatHttpClient(),
    notifikasi: PengirimNotifikasi = PengirimNotifikasiKosong,
    private val sekarang: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val db = PedareeDb(driver)
    private val api = KlienApi(http, basisUrl, sesi)
    val antrean = AntreanTulis(db, sekarang)
    private val logBuang = LogBuang(db)
    private val pengirim = PengirimAntrean(antrean, api, sekarang, setelahDitolak = { logBuang.batalkan(it.requestId) })
    private val kedaluwarsa = RepositoriKedaluwarsa(db, api)
    private val pemicu = PemicuNotifikasi(
        kedaluwarsa, PenyimpanRiwayatNotif(db), notifikasi, sekarang = { Instant.fromEpochMilliseconds(sekarang()) },
    )
    private val repositori = RepositoriDapur(db, api, sekarang)

    private val _status = MutableStateFlow(StatusAntrean())
    val status: StateFlow<StatusAntrean> = _status.asStateFlow()

    val sumber: SumberDapur get() = repositori

    val gerbang: GerbangTulis = GerbangTulisOffline(
        antrean, pengirim, setelahTulis = ::muatUlangStatus,
        diterima = { entri, perintah ->
            logBuang.catat(entri.requestId, perintah, kotaLokal())
            Unit
        },
    )

    /** Login (tanpa pendaftaran publik: akun dibuat operator). */
    suspend fun masuk(idPengguna: String, kataSandi: String): HasilHttp = api.login(idPengguna, kataSandi)

    /** Kirim antrean yang jatuh tempo; dipanggil saat aplikasi aktif dan berkala. */
    suspend fun kuras(): HasilKuras {
        val h = pengirim.kuras()
        muatUlangStatus()
        return h
    }

    private fun kotaLokal(): LocalDate = JendelaPush.tanggalWib(Instant.fromEpochMilliseconds(sekarang()))

    /** Tiga kartu ringkas Dapur: habis hari ini, segera 3 hari (dari server/cache), buang bulan ini (log lokal). */
    suspend fun kartu(hariIni: LocalDate): KartuRingkas =
        hitungKartu(kedaluwarsa.muat().data, hariIni, logBuang.bulanIni(hariIni))

    /** Evaluasi push kedaluwarsa (hanya di dalam jendela 06:30-20:00 WIB). */
    suspend fun evaluasiNotifikasi() { pemicu.evaluasi() }

    fun muatUlangStatus() {
        _status.value = StatusAntrean(antrean.jumlahMenunggu(), antrean.jumlahDitolak())
    }

    init { muatUlangStatus() }
}

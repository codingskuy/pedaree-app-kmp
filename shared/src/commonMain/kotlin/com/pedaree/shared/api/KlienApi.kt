package com.pedaree.shared.api

import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.kontrak.JsonPedaree
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Hasil satu permintaan HTTP, sudah dikelompokkan menurut cara klien harus bereaksi. */
sealed interface HasilHttp {
    /** 2xx. */
    data class Sukses(val status: Int, val badan: String) : HasilHttp

    /** 4xx domain/validasi/konflik (bukan 401/408/429): kirim ulang tidak akan membantu. */
    data class Ditolak(val status: Int, val galat: GalatApi) : HasilHttp

    /** Jaringan putus, timeout, 408, 429, atau 5xx: aman dikirim ulang dengan `X-Request-Id` yang sama. */
    data class Sementara(val status: Int?, val pesan: String) : HasilHttp

    /** Refresh gagal atau kedaluwarsa: pengguna harus login ulang. */
    data object SesiHabis : HasilHttp
}

@Serializable
private data class LoginBadan(val idPengguna: String, val kataSandi: String, val idPerangkat: String? = null)

@Serializable
private data class RefreshBadan(val tokenRefresh: String)

const val VERSI_KLIEN = "kmp-mobile/1.0.0"

/**
 * Klien REST Pedaree (diturunkan dari openapi backend PR #9). Setiap permintaan membawa `X-Request-Id`
 * (UUID), `X-Household-Id` (klaim `hh` JWT), `X-Client`, dan `Authorization: Bearer`. Pada 401 klien menukar
 * refresh token SEKALI lalu mengulang permintaan dengan `X-Request-Id` yang sama.
 *
 * [basisUrl] contoh `https://api.pedaree.chefgenie.id/v1` (produksi) atau `http://10.0.2.2:8080` (lokal; server
 * lokal di openapi tidak memakai awalan `/v1`).
 */
@OptIn(ExperimentalUuidApi::class)
class KlienApi(
    private val http: HttpClient,
    private val basisUrl: String,
    private val sesi: SesiAuth,
    private val buatId: () -> String = { Uuid.random().toString() },
) {
    private val kunciRefresh = Mutex()

    suspend fun login(idPengguna: String, kataSandi: String): HasilHttp {
        val badan = JsonPedaree.encodeToString(LoginBadan.serializer(), LoginBadan(idPengguna, kataSandi))
        val hasil = jalankan(HttpMethod.Post, "/auth/login", badan, buatId(), butuhAuth = false)
        if (hasil is HasilHttp.Sukses) {
            sesi.simpan(JsonPedaree.decodeFromString(TokenPasangan.serializer(), hasil.badan))
        }
        return hasil
    }

    /** POST dengan `X-Request-Id` yang disediakan pemanggil (dari antrean) agar idempoten. */
    suspend fun kirim(jalur: String, badanJson: String, requestId: String): HasilHttp =
        jalankan(HttpMethod.Post, jalur, badanJson, requestId)

    /** GET; setiap pemanggilan memakai `X-Request-Id` baru. */
    suspend fun ambil(jalur: String, kueri: Map<String, String> = emptyMap()): HasilHttp {
        val q = kueri.entries.joinToString("&") { "${it.key}=${it.value}" }
        return jalankan(HttpMethod.Get, if (q.isEmpty()) jalur else "$jalur?$q", null, buatId())
    }

    private suspend fun jalankan(
        metode: HttpMethod, jalur: String, badan: String?, requestId: String, butuhAuth: Boolean = true, sudahRefresh: Boolean = false,
    ): HasilHttp {
        val tokenDipakai = sesi.tokenAkses
        val hasil = try {
            val r = http.request {
                method = metode
                url(basisUrl.trimEnd('/') + jalur)
                header("X-Request-Id", requestId)
                header("X-Client", VERSI_KLIEN)
                sesi.householdId?.let { header("X-Household-Id", it) }
                if (butuhAuth && tokenDipakai != null) header("Authorization", "Bearer $tokenDipakai")
                if (badan != null) {
                    contentType(ContentType.Application.Json)
                    setBody(badan)
                }
            }
            klasifikasi(r.status.value, r.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            HasilHttp.Sementara(null, e.message ?: "jaringan tidak tersedia")
        }
        if (butuhAuth && !sudahRefresh && hasil is HasilHttp.Ditolak && hasil.status == 401) {
            return when (segarkan(tokenDipakai)) {
                true -> jalankan(metode, jalur, badan, requestId, butuhAuth, sudahRefresh = true)
                false -> HasilHttp.SesiHabis
                null -> HasilHttp.Sementara(null, "tidak dapat menyegarkan sesi (jaringan)")
            }
        }
        if (hasil is HasilHttp.Ditolak && hasil.status == 401 && butuhAuth) return HasilHttp.SesiHabis
        return hasil
    }

    /** `true` berhasil, `false` sesi berakhir (login ulang), `null` gagal sementara (jaringan). */
    private suspend fun segarkan(tokenGagal: String?): Boolean? = kunciRefresh.withLock {
        // Bila permintaan lain sudah menyegarkan sementara kita menunggu kunci, pakai token baru itu.
        if (sesi.tokenAkses != null && sesi.tokenAkses != tokenGagal) return@withLock true
        val refresh = sesi.tokenRefresh ?: return@withLock false
        val badan = JsonPedaree.encodeToString(RefreshBadan.serializer(), RefreshBadan(refresh))
        when (val h = jalankan(HttpMethod.Post, "/auth/refresh", badan, buatId(), butuhAuth = false)) {
            is HasilHttp.Sukses -> {
                sesi.simpan(JsonPedaree.decodeFromString(TokenPasangan.serializer(), h.badan))
                true
            }
            is HasilHttp.Ditolak -> { sesi.keluar(); false } // REFRESH_KEDALUWARSA / DIPAKAI_ULANG / TIDAK_SAH
            else -> null
        }
    }

    private fun klasifikasi(status: Int, teks: String): HasilHttp = when {
        status in 200..299 -> HasilHttp.Sukses(status, teks)
        status == 408 || status == 429 || status >= 500 -> HasilHttp.Sementara(status, "server menjawab $status")
        else -> HasilHttp.Ditolak(
            status,
            runCatching { JsonPedaree.decodeFromString(GalatApi.serializer(), teks) }
                .getOrElse { GalatApi("HTTP_$status", "permintaan ditolak ($status)") },
        )
    }
}

package com.pedaree.shared.data

import com.pedaree.shared.api.KlienApi
import com.pedaree.shared.api.PenyimpanTokenMemori
import com.pedaree.shared.api.SesiAuth
import com.pedaree.shared.api.TokenPasangan
import com.pedaree.shared.db.PedareeDb
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun dbBaru(): PedareeDb = PedareeDb(driverPedaree())

@OptIn(ExperimentalEncodingApi::class)
fun jwtUji(hh: String = "hh_1"): String {
    val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    return listOf("""{"alg":"EdDSA"}""", """{"hh":"$hh","aud":"pedaree"}""", "tanda").mapIndexed { i, s ->
        if (i == 2) s else b64.encode(s.encodeToByteArray())
    }.joinToString(".")
}

fun sesiMasuk(hh: String = "hh_1", akses: String = jwtUji(hh), refresh: String = "refresh_1"): SesiAuth =
    SesiAuth(PenyimpanTokenMemori()).also { it.simpan(TokenPasangan(akses, refresh)) }

/** Waktu palsu yang dapat dimajukan. */
class Jam(var sekarang: Long = 1_000_000L) {
    fun maju(ms: Long) { sekarang += ms }
    val baca: () -> Long = { sekarang }
}

/** Permintaan yang dilihat server tiruan. */
class Tercatat(val metode: String, val jalur: String, val header: Map<String, String>, val badan: String?)

class ServerTiruan(private val tangani: MockRequestHandleScope.(Tercatat) -> HttpResponseData) {
    val permintaan = mutableListOf<Tercatat>()

    val engine = MockEngine { r ->
        val t = r.catat()
        permintaan += t
        tangani(t)
    }

    fun klien(basis: String = "https://api.test/v1", sesi: SesiAuth = sesiMasuk(), idBaru: () -> String = { "gen-${permintaan.size}" }) =
        KlienApi(HttpClient(engine), basis, sesi, idBaru)

    private fun HttpRequestData.catat(): Tercatat = Tercatat(
        metode = method.value,
        jalur = url.encodedPath + (url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""),
        header = (
            listOf("X-Request-Id", "X-Household-Id", "X-Client", "Authorization")
                .mapNotNull { h -> headers[h]?.let { h to it } } +
                listOfNotNull(body.contentType?.toString()?.let { HttpHeaders.ContentType to it })
            ).toMap(),
        badan = (body as? TextContent)?.text,
    )
}

fun MockRequestHandleScope.json(isi: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(isi, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun putusJaringan(): Nothing = throw IOException("tidak ada koneksi")

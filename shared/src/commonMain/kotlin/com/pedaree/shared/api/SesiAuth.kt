package com.pedaree.shared.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Pasangan token dari `/auth/login` dan `/auth/refresh` (openapi `TokenPasangan`). */
@Serializable
data class TokenPasangan(
    val tokenAkses: String,
    val tokenRefresh: String,
    val kedaluwarsaDetik: Int = 900,
    val audiens: String = "pedaree",
)

/**
 * Penyimpanan token. Implementasi aman per platform (Android Keystore / iOS Keychain) BELUM dibuat;
 * [PenyimpanTokenMemori] hanya menyimpan di memori proses (token hilang saat aplikasi dimatikan).
 */
interface PenyimpanToken {
    fun baca(): TokenPasangan?
    fun simpan(token: TokenPasangan)
    fun hapus()
}

class PenyimpanTokenMemori : PenyimpanToken {
    private var isi: TokenPasangan? = null
    override fun baca() = isi
    override fun simpan(token: TokenPasangan) { isi = token }
    override fun hapus() { isi = null }
}

/**
 * Keadaan sesi: akses 15 menit + refresh 7 hari (sekali pakai, dirotasi server). Rumah tangga diambil dari
 * klaim `hh` pada JWT akses sehingga `X-Household-Id` selalu cocok dengan token (server menolak bila beda).
 */
class SesiAuth(private val penyimpan: PenyimpanToken) {
    private val _masuk = MutableStateFlow(penyimpan.baca() != null)
    val masuk: StateFlow<Boolean> = _masuk.asStateFlow()

    val tokenAkses: String? get() = penyimpan.baca()?.tokenAkses
    val tokenRefresh: String? get() = penyimpan.baca()?.tokenRefresh
    val householdId: String? get() = tokenAkses?.let(::klaimHouseholdDariJwt)

    fun simpan(token: TokenPasangan) {
        penyimpan.simpan(token)
        _masuk.value = true
    }

    fun keluar() {
        penyimpan.hapus()
        _masuk.value = false
    }
}

/** Baca klaim `hh` dari payload JWT (tanpa memverifikasi tanda tangan: itu tugas server). */
@OptIn(ExperimentalEncodingApi::class)
fun klaimHouseholdDariJwt(jwt: String): String? = runCatching {
    val payload = jwt.split('.').getOrNull(1) ?: return null
    val json = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
    Json.parseToJsonElement(json).jsonObject["hh"]?.jsonPrimitive?.content
}.getOrNull()

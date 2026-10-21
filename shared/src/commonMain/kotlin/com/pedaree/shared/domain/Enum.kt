package com.pedaree.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 13 satuan baku (sumber sah `inventori/20-satuan-hortikultura.md`). Kode = nilai di kabel API. */
@Serializable
enum class Satuan(val kode: String, val simbol: String, val jenis: JenisSatuan) {
    @SerialName("gram") GRAM("gram", "g", JenisSatuan.BERAT),
    @SerialName("kg") KG("kg", "kg", JenisSatuan.BERAT),
    @SerialName("ml") ML("ml", "ml", JenisSatuan.VOLUME),
    @SerialName("liter") LITER("liter", "L", JenisSatuan.VOLUME),
    @SerialName("sdm") SDM("sdm", "sdm", JenisSatuan.VOLUME),
    @SerialName("sdt") SDT("sdt", "sdt", JenisSatuan.VOLUME),
    @SerialName("pcs") PCS("pcs", "pcs", JenisSatuan.HITUNG),
    @SerialName("siung") SIUNG("siung", "siung", JenisSatuan.HITUNG),
    @SerialName("buah") BUAH("buah", "buah", JenisSatuan.HITUNG),
    @SerialName("ikat") IKAT("ikat", "ikat", JenisSatuan.HITUNG),
    @SerialName("batang") BATANG("batang", "btg", JenisSatuan.HITUNG),
    @SerialName("bonggol") BONGGOL("bonggol", "bonggol", JenisSatuan.HITUNG),
    @SerialName("lembar") LEMBAR("lembar", "lbr", JenisSatuan.HITUNG),
    ;

    companion object {
        fun dariKode(kode: String): Satuan? = entries.firstOrNull { it.kode == kode }
    }
}

enum class JenisSatuan { BERAT, VOLUME, HITUNG }

@Serializable
enum class Kategori(val nama: String) {
    SAYUR("Sayuran"), BUMBU("Bumbu dapur"), BUAH("Buahan"), PROTEIN("Protein"),
    KERING("Bahan kering"), CAIR("Cairan dapur"), BEKU("Bahan beku"), SISA("Sisa masakan"),
}

@Serializable
enum class LokasiSimpan(val nama: String) {
    KULKAS_BAWAH("Rak bawah kulkas"), KULKAS_ATAS("Chiller / rak atas"), FREEZER("Freezer"),
    SUHU_RUANG("Rak dapur suhu ruang"), BASKOM_BAWANG("Keranjang bawang"),
}

@Serializable
enum class SumberBatch { MASUK_BELANJA, MASUK_PANEN, MASUK_TITIPO, MASUK_KOREKSI }

@Serializable
enum class StatusBatch { AKTIF, HABIS, KEDALUWARSA, DIBUANG }

@Serializable
enum class StatusStok { AMAN, MENIPIS, HABIS }

/** Ambang SOP (`notifikasi/10-sop-kedaluwarsa.md` bab 2). */
@Serializable
enum class StatusKedaluwarsa(val label: String) {
    AMAN("Aman"), WASPADA("Waspada"), SEGERA("Segera"), HARI_INI("Pakai hari ini"), KEDALUWARSA("Kedaluwarsa"),
}

@Serializable
enum class JenisMutasi { PAKAI, MASUK, KOREKSI, PINDAH, BUANG }

/** Alasan buang baku; nilai kabel memuat spasi/garis miring sehingga dipetakan lewat @SerialName. */
@Serializable
enum class AlasanBuang(val teks: String) {
    @SerialName("busuk") BUSUK("busuk"),
    @SerialName("berlendir/berbau") BERLENDIR_BERBAU("berlendir/berbau"),
    @SerialName("lewat tanggal") LEWAT_TANGGAL("lewat tanggal"),
    @SerialName("tercemar") TERCEMAR("tercemar"),
}

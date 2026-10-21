package com.pedaree.shared.kontrak

import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.SumberBatch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON kabel Pedaree: abaikan kunci tak dikenal, null tidak ditulis (kunci opsional dihilangkan). */
val JsonPedaree: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

// Badan permintaan diturunkan dari docs/api/openapi.yaml di pedaree-backend-service#9.

@Serializable
data class BatchBuat(
    val pantryItemId: String,
    val jumlah: Double,
    val satuan: Satuan,
    val tanggalMasuk: LocalDate,
    val tanggalKedaluwarsa: LocalDate? = null,
    val lokasi: LokasiSimpan,
    val sumber: SumberBatch,
)

@Serializable
data class PemakaianBaris(val pantryItemId: String, val jumlah: Double, val satuan: Satuan)

@Serializable
data class PemakaianRequest(val pemakaian: List<PemakaianBaris>, val sumberResepId: String? = null)

@Serializable
data class PemakaianKurang(val pantryItemId: String, val kurangJumlah: Double, val satuanSimpan: Satuan)

@Serializable
data class PemakaianTerpotong(val batchId: String, val jumlah: Double)

@Serializable
data class PemakaianHasil(val terpotong: List<PemakaianTerpotong> = emptyList(), val kurang: List<PemakaianKurang> = emptyList())

@Serializable
data class OpnameRequest(
    val pantryItemId: String,
    val stokFisik: Double,
    val satuan: Satuan,
    val alasan: String,
    val stokCatatTerakhir: Double? = null,
)

@Serializable
data class OpnameHasil(val mutasi: List<com.pedaree.shared.domain.MutasiStok> = emptyList())

@Serializable
data class PindahRequest(val lokasiTujuan: LokasiSimpan)

@Serializable
data class BuangRequest(val jumlah: Double, val satuan: Satuan, val alasan: AlasanBuang)

@Serializable
data class KeranjangBaris(val pantryItemId: String, val jumlah: Double, val satuan: Satuan)

@Serializable
data class KeranjangTitipo(val items: List<KeranjangBaris>)

/**
 * Satu operasi tulis ke backend. Setiap perintah tahu jalur POST-nya dan badan JSON-nya, sehingga dapat
 * disimpan di antrean luring (PDR-KMP-005) dan dikirim ulang persis sama dengan `X-Request-Id` yang sama.
 */
sealed interface PerintahTulis {
    /** Jalur relatif terhadap basis API (`/v1`), selalu POST. */
    val jalur: String
    val jenis: JenisPerintah
    fun badanJson(): String

    data class MasukBatch(val badan: BatchBuat) : PerintahTulis {
        override val jalur = "/pantry/batches"
        override val jenis = JenisPerintah.MASUK
        override fun badanJson() = JsonPedaree.encodeToString(BatchBuat.serializer(), badan)
    }

    data class PakaiStok(val badan: PemakaianRequest) : PerintahTulis {
        override val jalur = "/pantry/consume"
        override val jenis = JenisPerintah.PAKAI
        override fun badanJson() = JsonPedaree.encodeToString(PemakaianRequest.serializer(), badan)
    }

    data class OpnameStok(val badan: OpnameRequest) : PerintahTulis {
        override val jalur = "/pantry/opname"
        override val jenis = JenisPerintah.OPNAME
        override fun badanJson() = JsonPedaree.encodeToString(OpnameRequest.serializer(), badan)
    }

    data class PindahBatch(val batchId: String, val badan: PindahRequest) : PerintahTulis {
        override val jalur = "/pantry/batches/$batchId/pindah"
        override val jenis = JenisPerintah.PINDAH
        override fun badanJson() = JsonPedaree.encodeToString(PindahRequest.serializer(), badan)
    }

    data class BuangBatch(val batchId: String, val badan: BuangRequest) : PerintahTulis {
        override val jalur = "/pantry/batches/$batchId/buang"
        override val jenis = JenisPerintah.BUANG
        override fun badanJson() = JsonPedaree.encodeToString(BuangRequest.serializer(), badan)
    }

    /** Aksi pengguna `Tambah ke TitipO`; anjuran, bukan pesanan otomatis. */
    data class KeTitipo(val badan: KeranjangTitipo) : PerintahTulis {
        override val jalur = "/demand/titipo"
        override val jenis = JenisPerintah.KE_TITIPO
        override fun badanJson() = JsonPedaree.encodeToString(KeranjangTitipo.serializer(), badan)
    }
}

enum class JenisPerintah { MASUK, PAKAI, OPNAME, PINDAH, BUANG, KE_TITIPO }

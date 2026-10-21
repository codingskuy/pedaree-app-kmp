package com.pedaree.shared.nav

/** Rute aplikasi (Navigation3 memakai daftar rute sebagai back stack). */
sealed interface Rute {
    /** Tab Dapur: daftar item stok. */
    data object Dapur : Rute

    /** Detail satu item + batch FEFO. */
    data class DetailItem(val itemId: String) : Rute
}

/**
 * Back stack murni (tanpa Compose) agar logika navigasi teruji di JVM.
 * Rute dasar tidak pernah terbuang: [kembali] mengembalikan false bila sudah di dasar.
 */
class TumpukanRute(dasar: Rute = Rute.Dapur) {
    private val isi = mutableListOf(dasar)

    val daftar: List<Rute> get() = isi.toList()
    val atas: Rute get() = isi.last()

    fun buka(rute: Rute) {
        // Hindari menumpuk rute identik berturut-turut (ketuk ganda).
        if (isi.last() != rute) isi.add(rute)
    }

    fun kembali(): Boolean {
        if (isi.size <= 1) return false
        isi.removeAt(isi.lastIndex)
        return true
    }
}

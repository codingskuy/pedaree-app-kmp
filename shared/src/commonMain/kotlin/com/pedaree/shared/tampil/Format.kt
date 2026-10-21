package com.pedaree.shared.tampil

import com.pedaree.shared.domain.JenisSatuan
import com.pedaree.shared.domain.Satuan
import kotlin.math.floor

/**
 * Format tampil jumlah (`inventori/20-satuan-hortikultura.md` bab 3): berat/volume 1 desimal bila < 1000,
 * tanpa desimal bila >= 1000 dengan pemisah ribuan titik (`1.500 g`); satuan hitung selalu bilangan bulat.
 * Ini hanya PEMFORMATAN; tidak ada konversi satuan di sini (konversi milik modul Pawonee).
 */
fun formatJumlah(jumlah: Double, satuan: Satuan): String =
    "${formatAngka(jumlah, satuan.jenis)} ${satuan.simbol}"

fun formatAngka(jumlah: Double, jenis: JenisSatuan): String = when (jenis) {
    JenisSatuan.HITUNG -> ribuan(bulatSetengahKeAtas(jumlah, 0).toLong())
    else -> if (bulatSetengahKeAtas(jumlah, 1) >= 1000.0) ribuan(bulatSetengahKeAtas(jumlah, 0).toLong()) else satuDesimal(jumlah)
}

private fun bulatSetengahKeAtas(nilai: Double, desimal: Int): Double {
    var pengali = 1.0
    repeat(desimal) { pengali *= 10.0 }
    return floor(nilai * pengali + 0.5) / pengali
}

private fun satuDesimal(nilai: Double): String {
    val dibulatkan = bulatSetengahKeAtas(nilai, 1)
    val bulat = floor(dibulatkan).toLong()
    val pecahan = ((dibulatkan - bulat) * 10.0 + 0.5).toInt()
    return if (pecahan == 0) "$bulat" else "$bulat,$pecahan"
}

private fun ribuan(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

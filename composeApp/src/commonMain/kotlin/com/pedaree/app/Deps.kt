package com.pedaree.app

import com.pedaree.shared.catat.GerbangTulis
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.satuan.ModulPantryResep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/**
 * Ketergantungan aplikasi yang disuntikkan dari entri platform (Android/iOS).
 * [versiData] naik setiap kali ada tulis sukses/diantrekan; layar daftar dan detail memuat ulang.
 */
class Deps(
    val sumber: SumberDapur,
    val modul: ModulPantryResep,
    val gerbang: GerbangTulis,
    val hariIni: () -> LocalDate,
) {
    val versiData = MutableStateFlow(0)
    fun dataBerubah() { versiData.value = versiData.value + 1 }
}

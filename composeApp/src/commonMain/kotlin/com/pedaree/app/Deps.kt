package com.pedaree.app

import com.pedaree.shared.api.HasilHttp
import com.pedaree.shared.api.SesiAuth
import com.pedaree.shared.catat.GerbangTulis
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.data.LapisanData
import com.pedaree.shared.data.StatusAntrean
import com.pedaree.shared.satuan.ModulPantryResep
import com.pedaree.shared.satuan.ModulPantryResepTertunda
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Ketergantungan aplikasi yang disuntikkan dari entri platform (Android/iOS).
 * [versiData] naik setiap kali ada tulis sukses/diantrekan; layar daftar dan detail memuat ulang.
 * [sesi] `null` = mode pengembangan tanpa login (data contoh).
 */
class Deps(
    val sumber: SumberDapur,
    val modul: ModulPantryResep,
    val gerbang: GerbangTulis,
    val hariIni: () -> LocalDate,
    val sesi: SesiAuth? = null,
    val masuk: suspend (String, String) -> HasilHttp = { _, _ -> HasilHttp.SesiHabis },
    val kuras: suspend () -> Unit = {},
    val status: StateFlow<StatusAntrean> = MutableStateFlow(StatusAntrean()),
) {
    val versiData = MutableStateFlow(0)
    fun dataBerubah() { versiData.value = versiData.value + 1 }
}

private fun hariIniPerangkat(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * Ketergantungan produksi: cache SQLDelight + antrean luring + klien REST ([data]).
 * Modul Pawonee masih [ModulPantryResepTertunda] (ADR-0004).
 */
fun depsNyata(data: LapisanData): Deps = Deps(
    sumber = data.sumber,
    modul = ModulPantryResepTertunda,
    gerbang = data.gerbang,
    hariIni = ::hariIniPerangkat,
    sesi = data.sesi,
    masuk = data::masuk,
    kuras = { data.kuras() },
    status = data.status,
)

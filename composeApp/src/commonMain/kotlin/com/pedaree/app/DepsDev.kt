package com.pedaree.app

import com.pedaree.shared.catat.GerbangTulisMemori
import com.pedaree.shared.data.hariIniPerangkat
import com.pedaree.shared.dapur.ContohSumberDapur
import com.pedaree.shared.satuan.ModulPantryResepTertunda

/**
 * Ketergantungan sementara sampai PDR-KMP-005 (cache SQLDelight + antrean + klien API):
 * data contoh, modul Pawonee tertunda (ADR-0004), dan gerbang tulis yang hanya mengantrekan di memori.
 * Tanggal "hari ini" memakai zona waktu perangkat (ASUMSI: WIB bagi pengguna Indonesia).
 */
fun depsPengembangan(): Deps = Deps(
    sumber = ContohSumberDapur(),
    modul = ModulPantryResepTertunda,
    gerbang = GerbangTulisMemori(),
    hariIni = ::hariIniPerangkat,
)

package com.pedaree.shared.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Tanggal "hari ini" menurut zona waktu perangkat (ASUMSI: WIB bagi pengguna Indonesia).
 * Sengaja tanpa `todayIn(Clock)`: tanda tangannya berbeda antara kotlinx-datetime 0.6.2 (Android)
 * dan 0.7.1 (iOS, transitif) sehingga kompilasi pecah di salah satu target.
 */
fun hariIniPerangkat(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

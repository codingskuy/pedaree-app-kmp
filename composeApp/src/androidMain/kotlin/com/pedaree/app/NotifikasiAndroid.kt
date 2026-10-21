package com.pedaree.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.pedaree.shared.notif.Notifikasi
import com.pedaree.shared.notif.PengirimNotifikasi

/**
 * Notifikasi lokal Android untuk kedaluwarsa. BELUM diuji di perangkat. Push FCM dari server belum ada:
 * kontrak payload dan pendaftaran token FCM tidak ada di openapi backend; notifikasi ini dihitung di perangkat
 * dari data kedaluwarsa saat aplikasi berjalan (lihat ADR-0006).
 */
class NotifikasiAndroid(private val context: Context) : PengirimNotifikasi {
    private val manajer = context.getSystemService(NotificationManager::class.java)

    init {
        manajer.createNotificationChannel(
            NotificationChannel(SALURAN, "Kedaluwarsa bahan", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Pengingat bahan yang mendekati atau melewati tanggal kedaluwarsa" },
        )
    }

    override fun tampilkan(notifikasi: Notifikasi) {
        val n = Notification.Builder(context, SALURAN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notifikasi.judul)
            .setContentText(notifikasi.isi)
            .setStyle(Notification.BigTextStyle().bigText(notifikasi.isi))
            .setAutoCancel(true)
            .build()
        // Satu notifikasi per item (id tetap) agar pembaruan tingkat menggantikan yang lama.
        manajer.notify(notifikasi.itemId.hashCode(), n)
    }

    private companion object { const val SALURAN = "kedaluwarsa" }
}

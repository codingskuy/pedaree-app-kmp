package com.pedaree.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.pedaree.shared.api.PenyimpanTokenMemori
import com.pedaree.shared.api.SesiAuth
import com.pedaree.shared.data.LapisanData
import com.pedaree.shared.data.URL_API_PRODUKSI
import com.pedaree.shared.data.driverPedaree

class MainActivity : ComponentActivity() {
    private val data: LapisanData by lazy {
        // URL API dapat ditimpa lewat <meta-data android:name="pedaree.api.url"> (mis. http://10.0.2.2:8080 untuk server lokal).
        val meta = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA).metaData
        val url = meta?.getString("pedaree.api.url") ?: URL_API_PRODUKSI
        // ASUMSI: token hanya di memori (PenyimpanTokenMemori); Android Keystore belum dibuat (lihat ADR-0005).
        LapisanData(driverPedaree(applicationContext), url, SesiAuth(PenyimpanTokenMemori()), notifikasi = NotifikasiAndroid(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+: izin notifikasi runtime (tanpa izin, notifikasi kedaluwarsa tidak tampil; aplikasi tetap jalan).
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // testTag terekspos sebagai resource-id agar terbaca selector Maestro (`id:`).
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                PedareeApp(remember { depsNyata(data) })
            }
        }
    }
}

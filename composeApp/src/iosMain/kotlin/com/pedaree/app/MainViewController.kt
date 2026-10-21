package com.pedaree.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pedaree.shared.api.PenyimpanTokenMemori
import com.pedaree.shared.api.SesiAuth
import com.pedaree.shared.data.LapisanData
import com.pedaree.shared.data.URL_API_PRODUKSI
import com.pedaree.shared.data.driverPedaree
import platform.UIKit.UIViewController

/** Entri iOS. ASUMSI: token hanya di memori (Keychain belum dibuat, ADR-0005). */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    val data = LapisanData(driverPedaree(), URL_API_PRODUKSI, SesiAuth(PenyimpanTokenMemori()))
    return ComposeUIViewController { PedareeApp(remember { depsNyata(data) }) }
}

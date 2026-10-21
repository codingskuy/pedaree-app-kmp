package com.pedaree.app

import androidx.compose.ui.window.ComposeUIViewController
import com.pedaree.shared.dapur.ContohSumberDapur

@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController { PedareeApp(sumber = ContohSumberDapur()) }

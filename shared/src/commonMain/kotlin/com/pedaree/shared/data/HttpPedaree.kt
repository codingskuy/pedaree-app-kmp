package com.pedaree.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/** Klien HTTP dengan batas waktu agar permintaan luring gagal cepat (mesin: OkHttp di Android, Darwin di iOS). */
fun buatHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
    }
}

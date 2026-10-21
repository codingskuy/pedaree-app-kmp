package com.pedaree.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.pedaree.shared.db.PedareeDb

/** Driver SQLite iOS (berkas `pedaree.db` di sandbox aplikasi). */
fun driverPedaree(): SqlDriver = NativeSqliteDriver(PedareeDb.Schema, "pedaree.db")

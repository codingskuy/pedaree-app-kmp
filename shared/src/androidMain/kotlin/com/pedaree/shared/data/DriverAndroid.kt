package com.pedaree.shared.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.pedaree.shared.db.PedareeDb

/** Driver SQLite Android (berkas `pedaree.db` di penyimpanan privat aplikasi). */
fun driverPedaree(context: Context): SqlDriver = AndroidSqliteDriver(PedareeDb.Schema, context, "pedaree.db")

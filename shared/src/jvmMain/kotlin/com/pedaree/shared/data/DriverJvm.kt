package com.pedaree.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pedaree.shared.db.PedareeDb

/** Hanya inang uji (ADR-0002): SQLite dalam memori dengan skema terpasang. */
fun driverPedaree(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { PedareeDb.Schema.create(it) }

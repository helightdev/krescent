package dev.helight.krescent.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

internal suspend fun <T> jdbcSuspendTransaction(database: Database, statement: suspend Transaction.() -> T): T =
    coroutineScope {
        async(Dispatchers.IO) {
            transaction(database) {
                runBlocking {
                    statement.invoke(this@transaction)
                }
            }
        }.await()
    }
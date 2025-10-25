package dev.helight.krescent.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

suspend fun <T> jdbcSuspendTransaction(database: Database, statement: suspend JdbcTransaction.() -> T): T =
    coroutineScope {
        async(Dispatchers.IO) {
            transaction(database) {
                runBlocking {
                    statement.invoke(this@transaction)
                }
            }
        }.await()
    }
package dev.helight.krescent.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

internal suspend fun <T> jdbcSuspendTransaction(
    database: Database,
    reuseTransaction: Boolean = false,
    statement: suspend Transaction.() -> T,
): T =
    coroutineScope {
        if (reuseTransaction) TransactionManager.currentOrNull()
            ?.takeIf { it.db == database }
            ?.let { return@coroutineScope statement.invoke(it) }

        async(Dispatchers.IO) {
            transaction(database) {
                runBlocking {
                    statement.invoke(this@transaction)
                }
            }
        }.await()
    }
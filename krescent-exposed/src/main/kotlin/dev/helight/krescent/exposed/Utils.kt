package dev.helight.krescent.exposed

import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

internal suspend fun <T> jdbcSuspendTransaction(
    database: Database,
    reuseTransaction: Boolean = false,
    statement: suspend Transaction.() -> T,
): T =
    coroutineScope {
        if (reuseTransaction) TransactionManager.currentOrNull()
            ?.takeIf { it.db == database }
            ?.let { return@coroutineScope statement.invoke(it) }

        return@coroutineScope suspendTransaction(db = database) {
            statement.invoke(this@suspendTransaction)
        }
    }
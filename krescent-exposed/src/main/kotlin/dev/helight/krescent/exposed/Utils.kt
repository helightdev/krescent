package dev.helight.krescent.exposed

import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

internal suspend fun <T> jdbcSuspendTransaction(
    database: Database,
    reuseTransaction: Boolean = false,
    statement: suspend Transaction.() -> T,
): T =
    coroutineScope {
        if (reuseTransaction) TransactionManager.currentOrNull()
            ?.takeIf { it.db == database }
            ?.let { return@coroutineScope statement.invoke(it) }

        return@coroutineScope newSuspendedTransaction(db = database) {
            statement.invoke(this@newSuspendedTransaction)
        }
    }
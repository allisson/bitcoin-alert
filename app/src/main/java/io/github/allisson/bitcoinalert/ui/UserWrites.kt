package io.github.allisson.bitcoinalert.ui

import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs a database or DataStore write started by a user action. A failure (e.g. a full disk) is
 * logged instead of crashing the app; the screens read the stored state, so they stay accurate.
 */
fun CoroutineScope.launchUserWrite(block: suspend () -> Unit): Job = launch {
    try {
        block()
    } catch (e: SQLiteException) {
        Log.w("UserWrite", "Database write failed", e)
    } catch (e: IOException) {
        Log.w("UserWrite", "Settings write failed", e)
    }
}

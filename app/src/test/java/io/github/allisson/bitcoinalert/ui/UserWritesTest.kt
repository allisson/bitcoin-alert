package io.github.allisson.bitcoinalert.ui

import android.database.sqlite.SQLiteFullException
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserWritesTest {
    @Test
    fun `a full disk or a failed settings write is logged, not thrown`() = runTest {
        val jobs = listOf(
            launchUserWrite { throw SQLiteFullException("disk full") },
            launchUserWrite { throw IOException("disk full") },
        )
        runCurrent()
        jobs.forEach { assertFalse(it.isCancelled) }
    }
}

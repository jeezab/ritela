package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.ritela.data.sync.SyncScheduler
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkerTest {
    @Test fun uniqueWorkWaitsForNetworkAndAnOldProfileWorkerDoesNotOpenItsDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        val manager = WorkManager.getInstance(context)
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        val profile = UUID.randomUUID().toString()
        val scheduler = SyncScheduler(context, profile)
        scheduler.ensure(true)
        scheduler.ensure(true)
        val now = manager.getWorkInfosForUniqueWork("sync-now-$profile").get(5, TimeUnit.SECONDS)
        assertEquals(1, now.size)
        val request = now.single()
        assertEquals(WorkInfo.State.ENQUEUED, request.state)
        assertEquals(NetworkType.CONNECTED, request.constraints.requiredNetworkType)
        assertFalse(context.getDatabasePath("ritela-$profile.db").exists())
        driver.setAllConstraintsMet(request.id)
        assertEquals(
            WorkInfo.State.SUCCEEDED,
            manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)!!.state
        )
        scheduler.continueNow().result.get(5, TimeUnit.SECONDS)
        val continuation = manager.getWorkInfosForUniqueWork("sync-now-$profile")
            .get(5, TimeUnit.SECONDS).single { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(NetworkType.CONNECTED, continuation.constraints.requiredNetworkType)
        driver.setAllConstraintsMet(continuation.id)
        assertEquals(
            WorkInfo.State.SUCCEEDED,
            manager.getWorkInfoById(continuation.id).get(5, TimeUnit.SECONDS)!!.state
        )
        val periodic = manager.getWorkInfosForUniqueWork("sync-periodic-$profile")
            .get(5, TimeUnit.SECONDS).single()
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        assertNotNull(periodic.periodicityInfo)
        scheduler.cancel()
        assertEquals(
            WorkInfo.State.CANCELLED,
            manager.getWorkInfoById(periodic.id).get(5, TimeUnit.SECONDS)!!.state
        )
        assertFalse(context.getDatabasePath("ritela-$profile.db").exists())
    }
}

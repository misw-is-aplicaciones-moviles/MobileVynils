package co.edu.uniandes.vinilos.testing

import androidx.test.espresso.IdlingResource
import co.edu.uniandes.vinilos.testing.idling.AsyncWorkIdlingResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class AsyncWorkIdlingResourceTest {
    @Test
    fun queued_tasks_remain_busy_until_the_last_task_finishes() {
        val resource = AsyncWorkIdlingResource("test")
        val queue = mutableListOf<Runnable>()
        val executor = resource.executor(Executor { queue += it })
        var transitions = 0
        resource.registerIdleTransitionCallback(IdlingResource.ResourceCallback { transitions++ })

        executor.execute {}
        executor.execute {}
        assertFalse(resource.isIdleNow())
        queue[0].run()
        assertFalse(resource.isIdleNow())
        queue[1].run()
        assertTrue(resource.isIdleNow())
        assertEquals(1, transitions)
    }

    @Test
    fun rejected_tasks_release_the_counter_and_propagate_the_failure() {
        val resource = AsyncWorkIdlingResource("test")
        val executor = resource.executor(Executor { throw RejectedExecutionException("stopped") })

        assertThrows(RejectedExecutionException::class.java) { executor.execute {} }

        assertTrue(resource.isIdleNow())
    }

    @Test
    fun inline_failure_releases_the_counter_only_once() {
        val resource = AsyncWorkIdlingResource("test")
        val executor = resource.executor(Executor { it.run() })
        var transitions = 0
        resource.registerIdleTransitionCallback(IdlingResource.ResourceCallback { transitions++ })

        assertThrows(IllegalStateException::class.java) { executor.execute { error("failed") } }

        assertTrue(resource.isIdleNow())
        assertEquals(1, transitions)
    }
}

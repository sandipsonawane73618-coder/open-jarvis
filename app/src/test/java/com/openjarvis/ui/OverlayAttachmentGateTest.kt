package com.openjarvis.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OverlayAttachmentGateTest {
    @Test
    fun rejectsAlreadyAttachedAndConcurrentReentry() {
        val gate = OverlayAttachmentGate()

        assertFalse(gate.begin(isAlreadyAttached = true))
        assertTrue(gate.begin(isAlreadyAttached = false))
        assertFalse(gate.begin(isAlreadyAttached = false))

        gate.finish()

        assertTrue(gate.begin(isAlreadyAttached = false))
        gate.finish()
    }

    @Test
    fun concurrentAttachRequestsAllowOnlyOneOwner() {
        val gate = OverlayAttachmentGate()
        val workers = 12
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val attempted = CountDownLatch(workers)
        val releaseWinner = CountDownLatch(1)
        val acquired = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) {
            executor.submit {
                ready.countDown()
                start.await()
                val ownsAttach = gate.begin(isAlreadyAttached = false)
                attempted.countDown()
                if (ownsAttach) {
                    acquired.incrementAndGet()
                    releaseWinner.await()
                    gate.finish()
                }
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        releaseWinner.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, acquired.get())
    }
}

package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialThinkingSessionStateTest {

    private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
        crossinline block: suspend () -> Unit
    ) {
        try {
            block()
            throw AssertionError("Expected exception ${T::class.java.name}")
        } catch (e: Throwable) {
            if (e !is T) {
                throw AssertionError(
                    "Expected exception ${T::class.java.name}, got ${e::class.java.name}: ${e.message}",
                    e
                )
            }
        }
    }

    @Test
    fun `states are isolated`() = runBlocking {
        val s1 = SequentialThinkingSessionState()
        val s2 = SequentialThinkingSessionState()

        val r1 = s1.process(
            buildJsonObject {
                put("thought", "t1")
                put("nextThoughtNeeded", true)
                put("thoughtNumber", 1)
                put("totalThoughts", 3)
            }
        )
        val r2 = s2.process(
            buildJsonObject {
                put("thought", "u1")
                put("nextThoughtNeeded", true)
                put("thoughtNumber", 1)
                put("totalThoughts", 3)
            }
        )

        assertEquals(1, r1.payload["thoughtHistoryLength"]!!.jsonPrimitive.int)
        assertEquals(1, r2.payload["thoughtHistoryLength"]!!.jsonPrimitive.int)
    }

    @Test
    fun `branchFromThought requires branchId`() = runBlocking {
        val s = SequentialThinkingSessionState()

        // First thought to establish maxThoughtNumberSeen
        s.process(
            buildJsonObject {
                put("thought", "t1")
                put("nextThoughtNeeded", true)
                put("thoughtNumber", 1)
                put("totalThoughts", 3)
            }
        )

        assertThrowsSuspend<IllegalStateException> {
            s.process(
                buildJsonObject {
                    put("thought", "t2")
                    put("nextThoughtNeeded", true)
                    put("thoughtNumber", 2)
                    put("totalThoughts", 3)
                    put("branchFromThought", 1)
                }
            )
        }
    }

    @Test
    fun `isRevision requires revisesThought`() = runBlocking {
        val s = SequentialThinkingSessionState()
        s.process(
            buildJsonObject {
                put("thought", "t1")
                put("nextThoughtNeeded", true)
                put("thoughtNumber", 1)
                put("totalThoughts", 3)
            }
        )

        assertThrowsSuspend<IllegalStateException> {
            s.process(
                buildJsonObject {
                    put("thought", "t2")
                    put("nextThoughtNeeded", true)
                    put("thoughtNumber", 2)
                    put("totalThoughts", 3)
                    put("isRevision", true)
                }
            )
        }
    }

    @Test
    fun `sequence end resets state`() = runBlocking {
        val s = SequentialThinkingSessionState()

        val end = s.process(
            buildJsonObject {
                put("thought", "final")
                put("nextThoughtNeeded", false)
                put("thoughtNumber", 1)
                put("totalThoughts", 1)
            }
        )

        assertTrue(end.sequenceEnded)
        assertEquals(1, end.payload["thoughtHistoryLength"]!!.jsonPrimitive.int)

        // Next run should start from a clean state
        val next = s.process(
            buildJsonObject {
                put("thought", "t1")
                put("nextThoughtNeeded", true)
                put("thoughtNumber", 1)
                put("totalThoughts", 2)
            }
        )

        assertFalse(next.sequenceEnded)
        assertEquals(1, next.payload["thoughtHistoryLength"]!!.jsonPrimitive.int)
        assertEquals(0, next.payload["branches"]!!.jsonArray.size)
    }

    @Test
    fun `branch ids are capped`() = runBlocking {
        val s = SequentialThinkingSessionState(maxBranchIds = 2)

        // Seed
        s.process(
            buildJsonObject {
                put("thought", "t1")
                put("nextThoughtNeeded", true)
                put("thoughtNumber", 1)
                put("totalThoughts", 4)
            }
        )

        suspend fun callWithBranch(thoughtNumber: Int, branchId: String) =
            s.process(
                buildJsonObject {
                    put("thought", "t$thoughtNumber")
                    put("nextThoughtNeeded", true)
                    put("thoughtNumber", thoughtNumber)
                    put("totalThoughts", 4)
                    put("branchFromThought", thoughtNumber - 1)
                    put("branchId", branchId)
                }
            )

        callWithBranch(2, "b1")
        val r3 = callWithBranch(3, "b2")
        val r4 = callWithBranch(4, "b3")

        assertEquals(2, r3.payload["branches"]!!.jsonArray.size)
        assertEquals(2, r4.payload["branches"]!!.jsonArray.size)
    }
}

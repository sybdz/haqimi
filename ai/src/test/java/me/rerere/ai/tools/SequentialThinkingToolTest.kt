package me.rerere.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialThinkingToolTest {
    private val json = Json

    @Test
    fun `tool name matches MCP`() {
        val tool = SequentialThinkingTool.create()
        assertEquals("sequentialthinking", tool.name)
    }

    @Test
    fun `stores thoughts and returns expected output`() = runBlocking {
        val tool = SequentialThinkingTool.create()

        val out1 = execute(tool, buildJsonObject {
            put("thought", "t1")
            put("nextThoughtNeeded", true)
            put("thoughtNumber", 1)
            put("totalThoughts", 3)
        })

        assertEquals(1, out1["thoughtNumber"]!!.jsonPrimitive.int)
        assertEquals(3, out1["totalThoughts"]!!.jsonPrimitive.int)
        assertEquals(true, out1["nextThoughtNeeded"]!!.jsonPrimitive.booleanOrNull)
        assertEquals(0, out1["branches"]!!.jsonArray.size)
        assertEquals(1, out1["thoughtHistoryLength"]!!.jsonPrimitive.int)

        // totalThoughts should be bumped if thoughtNumber exceeds it
        val out2 = execute(tool, buildJsonObject {
            put("thought", "t4")
            put("nextThoughtNeeded", false)
            put("thoughtNumber", 4)
            put("totalThoughts", 3)
        })
        assertEquals(4, out2["totalThoughts"]!!.jsonPrimitive.int)
        assertEquals(2, out2["thoughtHistoryLength"]!!.jsonPrimitive.int)

        // branch should be tracked when branchFromThought + branchId are provided
        val out3 = execute(tool, buildJsonObject {
            put("thought", "tb")
            put("nextThoughtNeeded", false)
            put("thoughtNumber", 5)
            put("totalThoughts", 5)
            put("branchFromThought", 2)
            put("branchId", "b1")
        })
        assertTrue(out3["branches"]!!.jsonArray.any { it.jsonPrimitive.content == "b1" })
        assertEquals(3, out3["thoughtHistoryLength"]!!.jsonPrimitive.int)
    }

    private suspend fun execute(tool: me.rerere.ai.core.Tool, args: JsonObject): JsonObject {
        val parts = tool.execute(args)
        val text = parts.filterIsInstance<UIMessagePart.Text>().single().text
        return json.parseToJsonElement(text).jsonObject
    }
}

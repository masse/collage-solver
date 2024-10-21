package se.kodverket.collage

import java.awt.Color
import kotlin.test.Test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class UtilTest {
    @Test
    fun testValidColorString() {
        "000000".toColor() shouldBe Color(0, 0, 0)
        "FF0000".toColor() shouldBe Color(255, 0, 0)
        "ffff00".toColor() shouldBe Color(255, 255, 0)
        "ffffff".toColor() shouldBe Color(255, 255, 255)
    }

    @Test
    fun testInvalidColorString() {
        shouldThrow<IllegalArgumentException> {
            "".toColor()
        }
        shouldThrow<IllegalArgumentException> {
            "12345".toColor()
        }
    }

    @Test
    fun testConcurrently() {
        fun testOperation(input: Int): String = "Result: $input"

        val inputList = listOf(1, 2, 3)

        val results =
            runBlocking {
                inputList.concurrently { testOperation(it) }
            }
        results shouldContainExactlyInAnyOrder listOf("Result: 1", "Result: 2", "Result: 3")
    }
}

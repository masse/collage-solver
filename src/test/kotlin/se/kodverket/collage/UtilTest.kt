package se.kodverket.collage

import java.awt.Color
import java.io.File
import kotlin.test.Test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import se.kodverket.collage.layoutsolver.Rotation

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

    @Test
    fun testGetImageMetadata() {
        // Map each file index to its expected rotation enum
        // The file name (rotated-i.jpg) corresponds to the EXIF orientation value i 1..8
        val expectedRotations =
            mapOf(
                1 to Rotation.ROT_0,
                2 to Rotation.MIRROR_HORIZONTAL,
                3 to Rotation.ROT_180,
                4 to Rotation.MIRROR_VERTICAL,
                5 to Rotation.MIRROR_HORIZONTAL_ROT_270_CW,
                6 to Rotation.ROT_CW_90,
                7 to Rotation.MIRROR_HORIZONTAL_ROT_90_CW,
                8 to Rotation.ROT_CW_270
            )

        for (i in 1..8) {
            val fileWithExifOrientation = File("src/test/resources/images/rotated-$i.jpg")
            val (dimension, rotation) = getImageMetadata(fileWithExifOrientation)

            // Verify that the function returns valid dimensions
            // The actual dimensions depend on the images
            // For this test, we're just verifying that the dimensions are positive
            (dimension.widthAsInt > 0) shouldBe true
            (dimension.heightAsInt > 0) shouldBe true

            // Assert that the rotation returned by getImageMetadata matches the expected rotation
            // for the corresponding EXIF orientation value
            rotation shouldBe expectedRotations[i]
        }
    }
}

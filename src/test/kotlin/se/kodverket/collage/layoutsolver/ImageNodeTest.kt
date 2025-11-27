package se.kodverket.collage.layoutsolver

import kotlin.test.Test
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

class ImageNodeTest {
    @Test
    fun `it should scale down to fit when placed in smaller parent`() {
        runComputeDimensionTest(
            parentDimension = Dimension(200.0, 200.0),
            sourceImage = SourceImage("sourceImage.png", Dimension(300.0, 200.0), 1),
            expectedWidth = 200.0,
            expectedHeight = 133.333,
            expectedOffCenterDistance = 0.0
        )
    }

    @Test
    fun `it should not scale up over maxScaleFactor when placed in larger parent`() {
        runComputeDimensionTest(
            parentDimension = Dimension(200.0, 200.0),
            sourceImage = SourceImage("sourceImage.png", Dimension(100.0, 130.0), 1),
            expectedWidth = 100.0,
            expectedHeight = 130.0,
            expectedOffCenterDistance = 0.0
        )
    }

    @Test
    fun `it calculates correct off center distance value for feature images for image NOT centered perfectly`() {
        runComputeDimensionTest(
            parentDimension = Dimension(200.0, 200.0),
            sourceImage = SourceImage("sourceImage.png", Dimension(100.0, 100.0), 2),
            expectedWidth = 100.0,
            expectedHeight = 100.0,
            expectedOffCenterDistance = 0.20763,
            currentXOffset = 120.0,
            currentYOffset = 980.0
        )
    }

    @Test
    fun `it calculates correct off center distance value for feature images for image centered perfectly`() {
        runComputeDimensionTest(
            parentDimension = Dimension(200.0, 200.0),
            sourceImage = SourceImage("sourceImage.png", Dimension(200.0, 200.0), 2),
            expectedWidth = 200.0,
            expectedHeight = 200.0,
            expectedOffCenterDistance = 0.0,
            currentXOffset = 900.0,
            currentYOffset = 900.0
        )
    }

    @Test
    fun `it can be copied`() {
        val sourceImage = SourceImage("sourceImage.png", Dimension(200.0, 200.0), 2)
        val (original, _) = ImageNode(sourceImage).computeAspectRatio()
        val copy = original.copy()

        copy shouldBeEqual original
        copy shouldNotBeSameInstanceAs original
    }

    private fun runComputeDimensionTest(
        parentDimension: Dimension,
        sourceImage: SourceImage,
        expectedWidth: Double,
        expectedHeight: Double,
        expectedOffCenterDistance: Double,
        currentXOffset: Double = 0.0,
        currentYOffset: Double = 0.0,
    ) {
        val config = CollageConfig(maxScaleFactor = 1.0, targetWidth = 2000, targetHeight = 2000)
        val (nodeWithAR, _) = ImageNode(sourceImage).computeAspectRatio()

        val (resultNode, imageCount) = nodeWithAR.computeDimensions(parentDimension, config, currentXOffset, currentYOffset)

        imageCount shouldBe 1
        resultNode.dimension.width shouldBe expectedWidth.plusOrMinus(0.001)
        resultNode.dimension.height shouldBe expectedHeight.plusOrMinus(0.001)
        resultNode.offCenterDistance shouldBe expectedOffCenterDistance.plusOrMinus(0.001)
    }
}

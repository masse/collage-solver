package se.kodverket.collage.layoutsolver

import kotlin.test.Test
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import se.kodverket.collage.layoutsolver.SlicingDirection.H
import se.kodverket.collage.layoutsolver.SlicingDirection.V

class LayoutNodeTest {
    @Test
    fun `computeAspectRatio should calculate the aspect ratio of a node with horizontal slicing direction`() {
        val (imageNode1, imageNode2) = exampleImageNodes()

        val layoutNode = LayoutNode(slicingDirection = H, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }

        layoutNode.aspectRatio shouldBe 0.6.plusOrMinus(0.001)
    }

    @Test
    fun `computeAspectRatio should calculate the aspect ratio of a node with vertical slicing direction`() {
        val (imageNode1, imageNode2) = exampleImageNodes()

        val layoutNode = LayoutNode(slicingDirection = V, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }

        layoutNode.aspectRatio shouldBe 2.5.plusOrMinus(0.001)
    }

    @Test
    fun `computeDimensions should scale down to fit when placed in smaller parent with horizontal slicing direction`() {
        val parentDimension = Dimension(200.0, 200.0)
        val (imageNode1, imageNode2) = exampleImageNodes()
        val layoutNode = LayoutNode(slicingDirection = H, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }
        val config = CollageConfig(maxScaleFactor = 1.0, targetWidth = 300, targetHeight = 300)

        val nodeCount = layoutNode.computeDimensions(parentDimension, config, 0.0, 0.0)

        nodeCount shouldBe 2

        layoutNode.aspectRatio shouldBe 0.6.plusOrMinus(0.001)
        layoutNode.dimension.width shouldBe 120.0.plusOrMinus(0.001)
        layoutNode.dimension.height shouldBe 200.0.plusOrMinus(0.001)

        layoutNode.left.aspectRatio shouldBe 1.5.plusOrMinus(0.001)
        layoutNode.left.dimension.width shouldBe 120.0.plusOrMinus(0.001)
        layoutNode.left.dimension.height shouldBe 80.0.plusOrMinus(0.001)

        layoutNode.right.aspectRatio shouldBe 1.0.plusOrMinus(0.001)
        layoutNode.right.dimension.width shouldBe 120.0.plusOrMinus(0.001)
        layoutNode.right.dimension.height shouldBe 120.0.plusOrMinus(0.001)
    }

    @Test
    fun `computeDimensions should scale down to fit when placed in smaller parent with vertical slicing direction`() {
        val parentDimension = Dimension(200.0, 200.0)
        val (imageNode1, imageNode2) = exampleImageNodes()
        val layoutNode = LayoutNode(slicingDirection = V, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }
        val config = CollageConfig(maxScaleFactor = 1.0, targetWidth = 300, targetHeight = 300)

        val nodeCount = layoutNode.computeDimensions(parentDimension, config, 0.0, 0.0)

        nodeCount shouldBe 2

        layoutNode.aspectRatio shouldBe 2.5.plusOrMinus(0.001)
        layoutNode.dimension.width shouldBe 200.0.plusOrMinus(0.001)
        layoutNode.dimension.height shouldBe 80.0.plusOrMinus(0.001)

        layoutNode.left.aspectRatio shouldBe 1.5.plusOrMinus(0.001)
        layoutNode.left.dimension.width shouldBe 120.0.plusOrMinus(0.001)
        layoutNode.left.dimension.height shouldBe 80.0.plusOrMinus(0.001)

        layoutNode.right.aspectRatio shouldBe 1.0.plusOrMinus(0.001)
        layoutNode.right.dimension.width shouldBe 80.0.plusOrMinus(0.001)
        layoutNode.right.dimension.height shouldBe 80.0.plusOrMinus(0.001)
    }

    @Test
    fun `computeDimensions should not scale image nodes beyond original when placed in larger parent with horizontal slicing direction`() {
        val parentDimension = Dimension(800.0, 800.0)
        val (imageNode1, imageNode2) = exampleImageNodes()
        val layoutNode = LayoutNode(slicingDirection = H, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }
        val config = CollageConfig(maxScaleFactor = 1.0, targetWidth = 800, targetHeight = 800)

        val nodeCount = layoutNode.computeDimensions(parentDimension, config, 0.0, 0.0)

        nodeCount shouldBe 2

        layoutNode.aspectRatio shouldBe 0.6.plusOrMinus(0.001)
        layoutNode.dimension.width shouldBe 480.0.plusOrMinus(0.001)
        layoutNode.dimension.height shouldBe 800.0.plusOrMinus(0.001)

        imageNode1.sourceImage.aspectRatio shouldBe layoutNode.left.aspectRatio.plusOrMinus(0.001)
        imageNode1.sourceImage.dimension.width shouldBe layoutNode.left.dimension.width.plusOrMinus(0.001)
        imageNode1.sourceImage.dimension.height shouldBe layoutNode.left.dimension.height.plusOrMinus(0.001)

        imageNode2.sourceImage.aspectRatio shouldBe layoutNode.right.aspectRatio.plusOrMinus(0.001)
        imageNode2.sourceImage.dimension.width shouldBe layoutNode.right.dimension.width.plusOrMinus(0.001)
        imageNode2.sourceImage.dimension.height shouldBe layoutNode.right.dimension.height.plusOrMinus(0.001)
    }

    @Test
    fun `computeDimensions should not scale image nodes beyond original when placed in larger parent with vertical slicing direction`() {
        val parentDimension = Dimension(1000.0, 1000.0)
        val (imageNode1, imageNode2) = exampleImageNodes()
        val layoutNode = LayoutNode(slicingDirection = V, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }
        val config = CollageConfig(maxScaleFactor = 1.0, targetWidth = 1000, targetHeight = 1000)

        val nodeCount = layoutNode.computeDimensions(parentDimension, config, 0.0, 0.0)

        nodeCount shouldBe 2
        layoutNode.aspectRatio shouldBe 2.5.plusOrMinus(0.001)
        layoutNode.dimension.width shouldBe 1000.0.plusOrMinus(0.001)
        layoutNode.dimension.height shouldBe 400.0.plusOrMinus(0.001)

        imageNode1.sourceImage.aspectRatio shouldBe layoutNode.left.aspectRatio
        imageNode1.sourceImage.dimension.width shouldBe layoutNode.left.dimension.width.plusOrMinus(0.001)
        imageNode1.sourceImage.dimension.height shouldBe layoutNode.left.dimension.height.plusOrMinus(0.001)

        imageNode2.sourceImage.aspectRatio shouldBe layoutNode.right.aspectRatio.plusOrMinus(0.001)
        imageNode2.sourceImage.dimension.width shouldBe layoutNode.right.dimension.width.plusOrMinus(0.001)
        imageNode2.sourceImage.dimension.height shouldBe layoutNode.right.dimension.height.plusOrMinus(0.001)
    }

    @Test
    fun `it can be cloned`() {
        val parentDimension = Dimension(200.0, 200.0)
        val (imageNode1, imageNode2) = exampleImageNodes()
        val layoutNode = LayoutNode(slicingDirection = H, left = imageNode1, right = imageNode2).also { it.computeAspectRatio() }
        val config = CollageConfig(maxScaleFactor = 1.0, targetWidth = 300, targetHeight = 300)
        layoutNode.computeDimensions(parentDimension, config, 0.0, 0.0)

        val clone = layoutNode.clone()

        clone shouldBeEqual layoutNode
        clone shouldNotBeSameInstanceAs layoutNode
    }

    private fun exampleImageNodes(): Pair<ImageNode, ImageNode> {
        val imageNode1 = ImageNode(SourceImage("sourceImage1.png", Dimension(300.0, 200.0), 1)).also { it.computeAspectRatio() }
        val imageNode2 = ImageNode(SourceImage("sourceImage2.png", Dimension(400.0, 400.0), 1)).also { it.computeAspectRatio() }
        return Pair(imageNode1, imageNode2)
    }
}

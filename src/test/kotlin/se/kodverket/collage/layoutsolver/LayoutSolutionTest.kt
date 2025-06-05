package se.kodverket.collage.layoutsolver

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import se.kodverket.collage.generic.ScoredIndividual
import se.kodverket.collage.layoutsolver.render.BinaryTreeDebugRenderer

class LayoutSolutionTest {
    @Test
    fun `it should create a layout solution`() {
        val sourceImages =
            listOf(
                SourceImage("image1.png", Dimension(100.0, 100.0), 1),
                SourceImage("image2.png", Dimension(200.0, 300.0), 1),
                SourceImage("image3.png", Dimension(300.0, 100.0), 1)
            )
        val config = CollageConfig(400, 400)

        val layoutSolution = generateLayoutSolution(sourceImages, config)

        layoutSolution.score shouldBe 0.0
        layoutSolution.layoutNodes().size shouldBe sourceImages.size.minus(1)

        layoutSolution.imageNodes().map { it.sourceImage } shouldContainExactlyInAnyOrder sourceImages
    }

    @Test
    fun `it should compute score correctly`() {
        val config =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                featureImages = listOf(FeatureImage("image-1.png", 2)),
                scoringFactors = ScoringFactors(1.0, 1.0, 1.0)
            )

        var scoredIndividual = createScoredIndividual(config)
        scoredIndividual.score shouldBe 20.5415.plusOrMinus(0.001)
        ImageIO.write(
            BinaryTreeDebugRenderer(scoredIndividual.individual).render(),
            "png",
            File("build/score-feature-image.png")
        )

        scoredIndividual = createScoredIndividual(config.copy(targetWidth = 800, targetHeight = 400))
        scoredIndividual.score shouldBe 22.7173.plusOrMinus(0.001)
        ImageIO.write(
            BinaryTreeDebugRenderer(scoredIndividual.individual).render(),
            "png",
            File("build/score-feature-image-smaller.png")
        )

        scoredIndividual =
            createScoredIndividual(config.copy(targetWidth = 800, targetHeight = 400, scoringFactors = ScoringFactors(2.0, 2.0, 2.0)))
        scoredIndividual.score shouldBe (2.0 * 22.7173).plusOrMinus(0.001)

        ImageIO.write(
            BinaryTreeDebugRenderer(scoredIndividual.individual).render(),
            "png",
            File("build/feature-image-doubled-scoring-factors.png")
        )
    }

    private fun createScoredIndividual(config: CollageConfig): ScoredIndividual<LayoutSolution> {
        val image1 = SourceImage("image1.png", Dimension(500.0, 600.0), 1)
        val image2 = SourceImage("image2.png", Dimension(200.0, 300.0), 1)
        val image3 = SourceImage("image3.png", Dimension(100.0, 400.0), 1)
        val sourceImages = listOf(image1, image2)
        val imageNode1 = ImageNode(image1)
        val imageNode2 = ImageNode(image2)
        val imageNode3 = ImageNode(image3)

        val conf = config.copy(desiredRelativeWeightSum = sourceImages.sumOf { it.desiredRelativeWeight })
        val rightLayoutNode = LayoutNode(slicingDirection = SlicingDirection.H, left = imageNode1, right = imageNode2)
        val rootNode = LayoutNode(slicingDirection = SlicingDirection.V, left = rightLayoutNode, right = imageNode3)
        val layoutSolution = LayoutSolution(rootNode, conf)

        return layoutSolution.score()
    }
}

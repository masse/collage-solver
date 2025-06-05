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

    @Test
    fun `it should penalize undersized feature images more than oversized ones`() {
        // Create a base configuration
        val baseConfig =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                scoringFactors = ScoringFactors(0.0, 1.0, 0.0) // Only consider relative image size
            )

        // Create images with different sizes
        val smallImage = SourceImage("small.png", Dimension(100.0, 100.0), 2) // Feature image (weight > 1)
        val largeImage = SourceImage("large.png", Dimension(400.0, 400.0), 2) // Feature image (weight > 1)

        // Create layouts with undersized and oversized feature images
        val undersizedLayout =
            createCustomLayout(
                smallImage,
                SourceImage("filler.png", Dimension(300.0, 300.0), 1),
                baseConfig
            )

        val oversizedLayout =
            createCustomLayout(
                largeImage,
                SourceImage("filler.png", Dimension(100.0, 100.0), 1),
                baseConfig
            )

        // Score both layouts
        val undersizedScore = undersizedLayout.score().score
        val oversizedScore = oversizedLayout.score().score

        // Undersized feature images should be penalized more (higher score is worse)
        (undersizedScore > oversizedScore) shouldBe true
    }

    @Test
    fun `it should consider off-center distance for feature images`() {
        // Create configurations with different scoring factors
        val withCenteringConfig =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                scoringFactors = ScoringFactors(0.0, 0.0, 1.0) // Only consider centering
            )

        val withoutCenteringConfig =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                scoringFactors = ScoringFactors(0.0, 0.0, 0.0) // Ignore centering
            )

        // Create a feature image
        val featureImage = SourceImage("feature.png", Dimension(200.0, 200.0), 2)
        val fillerImage = SourceImage("filler.png", Dimension(200.0, 200.0), 1)

        // Create layouts with the same images but different configs
        val layoutWithCentering = createCustomLayout(featureImage, fillerImage, withCenteringConfig)
        val layoutWithoutCentering = createCustomLayout(featureImage, fillerImage, withoutCenteringConfig)

        // Score both layouts
        val scoreWithCentering = layoutWithCentering.score().score
        val scoreWithoutCentering = layoutWithoutCentering.score().score

        // Score with centering should be higher (worse) than without
        (scoreWithCentering > scoreWithoutCentering) shouldBe true
    }

    @Test
    fun `it should consider canvas coverage`() {
        // Create configurations with different scoring factors
        val withCoverageConfig =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                scoringFactors = ScoringFactors(1.0, 0.0, 0.0) // Only consider coverage
            )

        val withoutCoverageConfig =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                scoringFactors = ScoringFactors(0.0, 0.0, 0.0) // Ignore coverage
            )

        // Create small images that won't cover the canvas well
        val image1 = SourceImage("small1.png", Dimension(100.0, 100.0), 1)
        val image2 = SourceImage("small2.png", Dimension(100.0, 100.0), 1)

        // Create layouts with the same images but different configs
        val layoutWithCoverage = createCustomLayout(image1, image2, withCoverageConfig)
        val layoutWithoutCoverage = createCustomLayout(image1, image2, withoutCoverageConfig)

        // Score both layouts
        val scoreWithCoverage = layoutWithCoverage.score().score
        val scoreWithoutCoverage = layoutWithoutCoverage.score().score

        // Score with coverage should be higher (worse) than without
        (scoreWithCoverage > scoreWithoutCoverage) shouldBe true
    }

    @Test
    fun `it should handle extreme image size differences`() {
        // Create a base configuration
        val config =
            CollageConfig(
                targetWidth = 800,
                targetHeight = 600,
                scoringFactors = ScoringFactors(0.0, 1.0, 0.0) // Only consider relative image size
            )

        // Create images with extreme size differences
        val tinyImage = SourceImage("tiny.png", Dimension(10.0, 10.0), 1)
        val hugeImage = SourceImage("huge.png", Dimension(1000.0, 1000.0), 1)

        // Create a layout with these extreme images
        val layout = createCustomLayout(tinyImage, hugeImage, config)

        // Score the layout
        val score = layout.score().score

        // Score should be positive (there should be some penalty)
        (score > 0.0) shouldBe true
    }

    private fun createCustomLayout(
        image1: SourceImage,
        image2: SourceImage,
        config: CollageConfig,
    ): LayoutSolution {
        val sourceImages = listOf(image1, image2)
        val imageNode1 = ImageNode(image1)
        val imageNode2 = ImageNode(image2)

        val conf = config.copy(desiredRelativeWeightSum = sourceImages.sumOf { it.desiredRelativeWeight })
        val rootNode = LayoutNode(slicingDirection = SlicingDirection.H, left = imageNode1, right = imageNode2)
        return LayoutSolution(rootNode, conf)
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

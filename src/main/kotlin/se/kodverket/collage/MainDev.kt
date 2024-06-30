package se.kodverket.collage

import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import se.kodverket.collage.generic.GeneticAlgorithm
import se.kodverket.collage.generic.fromFittestPartSelection
import se.kodverket.collage.layoutsolver.CollageConfig
import se.kodverket.collage.layoutsolver.DEFAULT_IMAGE_RELATIVE_WEIGHT
import se.kodverket.collage.layoutsolver.Dimension
import se.kodverket.collage.layoutsolver.FeatureImage
import se.kodverket.collage.layoutsolver.LayoutSolution
import se.kodverket.collage.layoutsolver.SourceImage
import se.kodverket.collage.layoutsolver.crossBreedIndividuals
import se.kodverket.collage.layoutsolver.generateLayoutSolution
import se.kodverket.collage.layoutsolver.render.BinaryTreeDebugRenderer
import se.kodverket.collage.layoutsolver.render.BinaryTreePrinter

fun main() {
    val featureImages = listOf(FeatureImage("Image-1", 3), FeatureImage("Image-2", 3), FeatureImage("Image-3", 10))
    val sourceImages = getRandomImages(100, featureImages)
    val config =
        CollageConfig(
            featureImages = featureImages,
            borderWidth = 1,
            populationSize = 1000,
            numGenerations = 1000,
            desiredRelativeWeightSum = sourceImages.sumOf { it.desiredRelativeWeight }
        )
    val population = (1..config.populationSize).map { generateLayoutSolution(sourceImages, config) }

    val algorithm =
        GeneticAlgorithm(
            population,
            select = ::fromFittestPartSelection,
            cross = ::crossBreedIndividuals,
            mutate = LayoutSolution::mutate,
            score = LayoutSolution::score,
            clone = LayoutSolution::clone
        )
    val (result, timeTaken) =
        measureTimedValue {
            algorithm.run(numGenerations = config.numGenerations, mutationProbability = 0.1)
        }
    BinaryTreePrinter(result.individual.rootNode).print(System.out)
    println("Best individual after ${config.numGenerations} generations of population size ${config.populationSize}: $result")
    println("Execution time $timeTaken")
    println("WriteImage took ${measureTime { writeImage("main-dev-debug-rendering", result.individual) }} ms")
}

private fun getRandomImages(
    nrImages: Int,
    featureImages: List<FeatureImage>,
): List<SourceImage> = List(nrImages) { getRandomImage(it, featureImages) }

private fun getRandomImage(
    index: Int,
    featureImages: List<FeatureImage>,
): SourceImage {
    val dimensions =
        arrayOf(
            Pair(640, 480),
            Pair(800, 600),
            Pair(1024, 1024),
            Pair(1200, 1200),
            Pair(1712, 2288),
            Pair(1920, 1440),
            Pair(1600, 1200),
            Pair(2592, 1944),
            Pair(3072, 2304),
            Pair(4480, 6720),
            Pair(4288, 3216),
            Pair(2464, 1648)
        ).random()
    val landscape = Random.nextInt(100) < 80
    val width = if (landscape) dimensions.first else dimensions.second
    val height = if (landscape) dimensions.second else dimensions.first
    val featureFileName = "Image-$index"
    return SourceImage(
        "Image-$index",
        Dimension(width.toDouble(), height.toDouble()),
        desiredRelativeWeight =
            featureImages.find { it.name == featureFileName }?.relativeWeight
                ?: DEFAULT_IMAGE_RELATIVE_WEIGHT
    )
}

private fun writeImage(
    fileName: String,
    layout: LayoutSolution,
) {
    ImageIO.write(
        BinaryTreeDebugRenderer(layout).render(),
        "png",
        File("build/$fileName.png")
    )
}

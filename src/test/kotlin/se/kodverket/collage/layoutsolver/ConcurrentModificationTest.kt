package se.kodverket.collage.layoutsolver

import kotlin.test.Test
import se.kodverket.collage.generic.GeneticAlgorithm
import se.kodverket.collage.generic.fromFittestPartSelection

class ConcurrentModificationTest {
    @Test
    fun `crossBreedIndividuals should not throw ConcurrentModificationException when run concurrently`() {
        // Create test data
        val images = List(20) { index ->
            SourceImage(
                "image-$index.jpg",
                Dimension(800.0, 600.0),
                desiredRelativeWeight = 1
            )
        }
        val config = CollageConfig(
            targetWidth = 1920,
            targetHeight = 1080,
            populationSize = 100,
            numGenerations = 10,
            desiredRelativeWeightSum = images.sumOf { it.desiredRelativeWeight }
        )

        // Generate initial population
        val population = (1..config.populationSize).map {
            generateLayoutSolution(images, config)
        }

        // Create the genetic algorithm
        val algorithm = GeneticAlgorithm(
            population,
            select = { fromFittestPartSelection(it, 0.25) },
            cross = ::crossBreedIndividuals,
            mutate = LayoutSolution::mutate,
            score = LayoutSolution::score
        )

        // Run with coroutines - this should not throw ConcurrentModificationException
        val result = algorithm.run(
            numGenerations = 10,
            mutationProbability = 0.25,
            useCoroutines = true
        )

        // If we get here without exception, the test passes
        assert(result.score >= 0.0)
    }
}

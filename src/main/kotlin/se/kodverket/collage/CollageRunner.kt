package se.kodverket.collage

import kotlin.time.measureTimedValue
import se.kodverket.collage.generic.GeneticAlgorithm
import se.kodverket.collage.generic.ScoredIndividual
import se.kodverket.collage.generic.fromFittestPartSelection
import se.kodverket.collage.layoutsolver.CollageConfig
import se.kodverket.collage.layoutsolver.LayoutSolution
import se.kodverket.collage.layoutsolver.SourceImage
import se.kodverket.collage.layoutsolver.crossBreedIndividuals
import se.kodverket.collage.layoutsolver.generateLayoutSolution

class CollageRunner {
    fun run(
        config: CollageConfig,
        images: List<SourceImage>,
    ): ScoredIndividual<LayoutSolution> {
        require(images.size > 1) { "Must have at least 2 images to create a collage" }
        println("Start running using config $config")

        val population = (1..config.populationSize).map { generateLayoutSolution(images, config) }

        val algorithm =
            GeneticAlgorithm(
                population,
                select = { fromFittestPartSelection(it, 0.25) },
                cross = ::crossBreedIndividuals,
                mutate = LayoutSolution::mutate,
                score = LayoutSolution::score,
                clone = LayoutSolution::clone
            )
        val (result, duration) =
            measureTimedValue {
                algorithm.run(
                    numGenerations = config.numGenerations,
                    mutationProbability = config.mutationProbability,
                    costThreshold = 0.00000001
                )
            }
        println("\nBest image collage for ${images.size} images after ${config.numGenerations} generations and $duration was: $result")
        return result
    }
}

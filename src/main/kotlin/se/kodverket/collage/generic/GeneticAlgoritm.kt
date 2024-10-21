package se.kodverket.collage.generic

import java.lang.Math.random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import se.kodverket.collage.concurrently

/**
 * Implementation of a basic genetic algorithm.
 *
 * @param T the type of individual.
 * @property initialPopulation a collection of individuals to start optimization.
 * @property score a function which scores the fitness (cost, lower iw better) of an individual.
 * @property cross a function which implements the crossover of two individuals resulting in a new individual.
 * @property mutate a function which mutates a given individual, controlled by mutationProbability
 * @property select a function which implements a selection strategy of an individual from the population.
 * @property clone a function which should create a (deep) cloned copy of an individual.
 */
class GeneticAlgorithm<T>(
    private var initialPopulation: Collection<T>,
    val select: (scoredPopulation: Collection<ScoredIndividual<T>>) -> T,
    val cross: (parents: Pair<T, T>) -> T,
    val mutate: (individual: T) -> T,
    val score: (individual: T) -> ScoredIndividual<T>,
    val clone: (individual: T) -> T,
) {
    /**
     * Returns the best individual found after the given number of generations.
     *
     * @param numGenerations number of generations to evolve.
     * @property mutationProbability a value between 0 and 1, which defines the mutation probability of each child.
     */
    fun run(
        numGenerations: Int = 1000,
        mutationProbability: Double = 0.1,
        costThreshold: Double = 0.0,
        useCoroutines: Boolean = true,
    ): ScoredIndividual<T> {
        // Initialize the population and (initially) best individual
        var population = initialPopulation.map { score(it) }.sortedBy { it.score }
        var bestIndividual = population.first().clone()
        var winnerCount = 1

        for (generation in 1..numGenerations) {
            print("Evolving generation $generation...\r")
            if (useCoroutines) {
                runBlocking(Dispatchers.Default) {
                    population =
                        population
                            .concurrently {
                                val parents = Pair(select(population), select(population))
                                val child = cross(parents)
                                val mutatedChild = if (random() <= mutationProbability) mutate(child) else child
                                score(mutatedChild)
                            }.sortedBy { it.score }
                }
            } else {
                population =
                    population
                        .map {
                            val parents = Pair(select(population), select(population))
                            val child = cross(parents)
                            val mutatedChild = if (random() <= mutationProbability) mutate(child) else child
                            score(mutatedChild)
                        }.sortedBy { it.score }
            }

            if (bestIndividual.score > population.first().score) {
                val improvement = calculateImprovementPercentage(bestIndividual, population.first())
                println(
                    "Generation $generation produced $winnerCount-th winner - cost ${
                        "%.4f".format(
                            population.first().score
                        )
                    } = $improvement% improvement"
                )
                bestIndividual = population.first().clone()
                winnerCount++
            }

            if (bestIndividual.score <= costThreshold) {
                println("CostThreshold reached in generation $generation")
                return bestIndividual
            }
        }
        return bestIndividual
    }

    private fun calculateImprovementPercentage(
        oldScore: ScoredIndividual<T>,
        newScore: ScoredIndividual<T>,
    ): String = "%.2f".format(100 * (oldScore.score - newScore.score) / oldScore.score)

    private fun ScoredIndividual<T>.clone() = ScoredIndividual(score, clone(individual))
}

data class ScoredIndividual<T>(
    val score: Double,
    val individual: T,
)

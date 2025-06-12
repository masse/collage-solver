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
    private val initialPopulation: Collection<T>,
    val select: (scoredPopulation: Collection<ScoredIndividual<T>>) -> T,
    val cross: (parents: Pair<T, T>) -> T,
    val mutate: (individual: T) -> T,
    val score: (individual: T) -> ScoredIndividual<T>,
    val clone: (individual: T) -> T,
) {
    /**
     * Executes the genetic algorithm over a specified number of generations to optimize a population of individuals.
     *
     * @param numGenerations The maximum number of generations to evolve (default is 1000).
     * @param mutationProbability The probability of mutation for each child during the evolution process (default is 0.1).
     * @param costThreshold The threshold for the cost value; if the best individual's score is less than or equal to this, evolution stops (default is 0.0).
     * @param useCoroutines Determines whether to use coroutines for parallelism when evolving the population (default is true).
     * @return The best individual from the last generation or earlier if the cost threshold is reached, wrapped as a `ScoredIndividual`.
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
            print(
                "\rGeneration $generation, best score ${
                    "%.4f".format(
                        bestIndividual.score
                    )
                } (improved $winnerCount times)"
            )
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
                bestIndividual = population.first().clone()
                winnerCount++
            }

            if (bestIndividual.score <= costThreshold) {
                println("CostThreshold $costThreshold reached in generation $generation")
                return bestIndividual
            }
        }
        return bestIndividual
    }

    private fun ScoredIndividual<T>.clone() = ScoredIndividual(score, clone(individual))
}

data class ScoredIndividual<T>(
    val score: Double,
    val individual: T,
)

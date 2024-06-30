package se.kodverket.collage.generic

import java.lang.Math.random
import java.util.List.copyOf
import kotlin.math.abs
import kotlin.time.measureTimedValue

/**
 * A simple GeneticAlgorithm implementation to find a string that as closely as possible resembles
 * a target string. It uses a character pool containing lowercase and uppercase characters.
 *
 * The algorithm starts with a random population of char lists and iteratively improves
 * them by selecting the fittest individuals, performing crossover and mutation, and then selecting
 * the best individual from each new generation. The process continues for a specified number of
 * generations.
 */
fun main() {
    val genePool = ('a'..'z') + ('A'..'Z') + (' ')
    val target = "Charles Darwin had a brilliant idea".toCharArray().asList()

    val populationSize = 250
    val numGenerations = 50
    val mutationProbability = 0.5

    val population = (1..populationSize).map { (1..target.size).map { genePool.random() } }
    val startWinner = copyOf(population.first())

    val algorithm =
        GeneticAlgorithm(
            population,
            score = {
                ScoredIndividual(
                    it.zip(target) { actual: Char, wanted: Char -> abs(actual.compareTo(wanted)) }.sum().toDouble(),
                    it
                )
            },
            cross = {
                // Create a child that takes 50 % of genes from each parent
                it.first.mapIndexed { index, s -> if (random() < 0.5) s else it.second[index] }
            },
            mutate = { it.map { gene -> if (random() < 0.1) genePool.random() else gene } },
            select = ::fromFittestPartSelection,
            clone = { copyOf(it) }
        )

    val (result, duration) =
        measureTimedValue {
            algorithm.run(
                numGenerations = numGenerations,
                mutationProbability = mutationProbability,
                costThreshold = 0.0,
                useCoroutines = false
            )
        }
    println("Total execution time: $duration")
    println("First population best individual: ${startWinner.joinToString("")}")
    println("Final population best individual: ${result.individual.joinToString("")}")
}

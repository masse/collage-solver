package se.kodverket.collage.generic

import kotlin.random.Random

/**
 * Selects an individual randomly from the fittest percentage of a scored population.
 */
fun <T> fromFittestPartSelection(
    scoredPopulation: Collection<ScoredIndividual<T>>,
    percentage: Double = 0.5,
    random: Random = Random.Default,
): T = scoredPopulation.elementAt((random.nextDouble() * scoredPopulation.size.toDouble() * percentage).toInt()).individual

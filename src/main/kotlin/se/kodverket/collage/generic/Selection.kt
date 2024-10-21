package se.kodverket.collage.generic

import java.lang.Math.random

/**
 * Selects an individual randomly from the fittest percentage of a scored population.
 */
fun <T> fromFittestPartSelection(
    scoredPopulation: Collection<ScoredIndividual<T>>,
    percentage: Double = 0.5,
): T = scoredPopulation.elementAt((random() * scoredPopulation.size.toDouble() * percentage).toInt()).individual

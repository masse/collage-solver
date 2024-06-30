package se.kodverket.collage.generic

import kotlin.test.Test
import io.kotest.matchers.collections.shouldBeOneOf

class SelectionTest {
    private val firstQuartile = (1..25).map { ScoredIndividual(it.toDouble(), "Individual $it") }
    private val firstHalf = firstQuartile + (26..50).map { ScoredIndividual(it.toDouble(), "Individual $it") }
    private val bottomHalf = (51..100).map { ScoredIndividual(it.toDouble(), "Individual $it") }

    private val population = firstHalf + bottomHalf

    @Test
    fun fromFittestPartSelection() {
        fromFittestPartSelection(population, 0.25) shouldBeOneOf firstQuartile.map { it.individual }
        fromFittestPartSelection(population, 0.5) shouldBeOneOf firstHalf.map { it.individual }
        fromFittestPartSelection(population, 1.0) shouldBeOneOf population.map { it.individual }
    }
}

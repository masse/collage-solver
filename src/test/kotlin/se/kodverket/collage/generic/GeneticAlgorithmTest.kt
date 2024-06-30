package se.kodverket.collage.generic

import kotlin.math.min
import kotlin.test.Test
import io.kotest.matchers.shouldBe

class GeneticAlgorithmTest {
    private val geneticAlgorithm =
        GeneticAlgorithm(
            initialPopulation = listOf('Z', 'X', 'H', 'Y'),
            select = { p -> p.first().individual },
            cross = { p -> Char(min(p.first.code, p.second.code)) },
            mutate = { p -> p.dec() },
            score = { p -> ScoredIndividual(p.code.toDouble(), p) },
            clone = { p -> p }
        )

    @Test
    fun `should return best individual after given number of generations`() {
        geneticAlgorithm.run(numGenerations = 2, mutationProbability = 1.0) shouldBe ScoredIndividual(70.0, 'F')
        geneticAlgorithm.run(numGenerations = 5, mutationProbability = 1.0) shouldBe ScoredIndividual(67.0, 'C')
    }

    @Test
    fun `should not mutate if mutation probability is 0`() {
        geneticAlgorithm.run(numGenerations = 10, mutationProbability = 0.0) shouldBe ScoredIndividual(72.0, 'H')
    }

    @Test
    fun `should return best individual after first generation if cost threshold is met`() {
        geneticAlgorithm.run(numGenerations = 1000, mutationProbability = 1.0, costThreshold = 100.0) shouldBe ScoredIndividual(71.0, 'G')
    }
}

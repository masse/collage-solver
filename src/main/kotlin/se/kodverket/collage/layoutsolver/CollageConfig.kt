package se.kodverket.collage.layoutsolver

import java.awt.Color
import se.kodverket.collage.toColor

const val DEFAULT_IMAGE_RELATIVE_WEIGHT = 1

data class CollageConfig(
    val targetWidth: Int = 1920,
    val targetHeight: Int = 1080,
    val borderWidth: Int = 2,
    val borderColor: Color = "ffffff".toColor(),
    val featureImages: List<FeatureImage> = emptyList(),
    val maxScaleFactor: Double = 1.0,
    val desiredRelativeWeightSum: Int = 0,
    val mutationProbability: Double = 0.25,
    val numGenerations: Int = 500,
    val populationSize: Int = 1000,
    val scoringFactors: ScoringFactors = ScoringFactors(),
)

data class FeatureImage(
    val name: String,
    val relativeWeight: Int,
) {
    init {
        require(name.isNotEmpty()) { "Image name must not be empty" }
        require(relativeWeight > 0) { "Relative weight must be greater than 0" }
    }
}

object FeatureImageArgumentParser {
    val pattern = Regex("(.+):(\\d+)")

    fun parse(input: String): FeatureImage =
        pattern
            .matchEntire(input)
            ?.destructured
            ?.let { (name, weight) ->
                FeatureImage(name, weight.toInt())
            }
            ?: throw IllegalArgumentException("Bad feature image input '$input'")
}

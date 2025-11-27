package se.kodverket.collage.layoutsolver

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Represents an image node in a layout tree. The image nodes are always leaf nodes in the layout tree.
 *
 * An `ImageNode` contains information about an image, including its source image, off-center distance,
 * dimension, and aspect ratio. It also provides methods for computing the dimensions of the node and
 * for rendering the image on an image canvas.
 *
 * @property sourceImage The source image for the node.
 * @property offCenterDistance The off-center distance for the node.
 * @property dimension The dimension of the node.
 * @property aspectRatio The aspect ratio of the node.
 */
data class ImageNode(
    val sourceImage: SourceImage,
    val offCenterDistance: Double = 0.0,
    override val dimension: Dimension = Dimension(0.0, 0.0),
    override val aspectRatio: Double = 0.0,
) : Node {
    /**
     * Calculates the dimension of an image node in a layout tree.
     *
     * @param parentDimension The dimension of the parent node.
     * @param config The configuration for the collage.
     * @param currentXOffset The current X offset of the node.
     * @param currentYOffset The current Y offset of the node.
     * @return Pair of (new ImageNode with computed dimensions, count of 1)
     */
    override fun computeDimensions(
        parentDimension: Dimension,
        config: CollageConfig,
        currentXOffset: Double,
        currentYOffset: Double,
    ): Pair<ImageNode, Int> {
        // Calculate the width based on the aspect ratio and the height of the parent node
        val calculatedWidth = aspectRatio * parentDimension.height

        // If the calculated width exceeds the width of the parent node, cap it
        var width = min(calculatedWidth, parentDimension.width)
        var height = width / aspectRatio

        // Calculate the scale factor based on the width of the image and the width of the calculated width
        val scaleFactor = width / sourceImage.dimension.width

        // If the scale factor exceeds the maximum scale factor allowed, scale down the image
        if (scaleFactor > config.maxScaleFactor) {
            width = sourceImage.dimension.width * config.maxScaleFactor
            height = sourceImage.dimension.height * config.maxScaleFactor
        }

        val newDimension = Dimension(width, height)

        // Calculate the off-center distance weight of the image (only applies to feature images with a weight > 1)
        val newOffCenterDistance =
            if (sourceImage.desiredRelativeWeight > 1) {
                val a = (config.targetWidth.toDouble() / 2.0) - (currentXOffset + (newDimension.width / 2.0))
                val b = (config.targetHeight.toDouble() / 2.0) - (currentYOffset + (newDimension.height / 2.0))
                // Normalize by half of the longest canvas side so that the maximum possible distance is ~1.0
                val normalizationDenominator = 2 * max(config.targetWidth, config.targetHeight)
                hypot(a, b) / normalizationDenominator.toDouble()
            } else {
                0.0
            }

        // Return new node with updated values; image nodes always contains exactly 1 image node by definition.
        return Pair(copy(dimension = newDimension, offCenterDistance = newOffCenterDistance), 1)
    }

    override fun computeAspectRatio(): Pair<ImageNode, Double> {
        val ar = sourceImage.aspectRatio
        return Pair(copy(aspectRatio = ar), ar)
    }

    override fun toString(): String =
        "$dimension ≈ ${(100.0 * (dimension.width / sourceImage.dimension.width)).roundToInt()}% of " +
            "${sourceImage.dimension.widthAsInt}x${sourceImage.dimension.heightAsInt} " +
            "(weight: ${sourceImage.desiredRelativeWeight}) @${"%.${3}f".format(sourceImage.aspectRatio)}"
}

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
class ImageNode(
    var sourceImage: SourceImage,
    var offCenterDistance: Double = 0.0,
    override var dimension: Dimension = Dimension(0.0, 0.0),
    override var aspectRatio: Double = 0.0,
) : Node {
    /**
     * Calculates the dimension of an image node in a layout tree.
     *
     * @param parentDimension The dimension of the parent node.
     * @param config The configuration for the collage.
     * @param currentXOffset The current X offset of the node.
     * @param currentYOffset The current Y offset of the node.
     * @return The number of image nodes in the subtree rooted at this node.
     */
    override fun computeDimensions(
        parentDimension: Dimension,
        config: CollageConfig,
        currentXOffset: Double,
        currentYOffset: Double,
    ): Int {
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

        // Set the dimension of the node to the calculated width and height
        dimension = Dimension(width, height)

        // Calculate the off-center distance weight of the image (only applies to feature images with a weight > 1)
        offCenterDistance =
            if (sourceImage.desiredRelativeWeight > 1) {
                val a = (config.targetWidth.toDouble() / 2.0) - (currentXOffset + (dimension.width / 2.0))
                val b = (config.targetHeight.toDouble() / 2.0) - (currentYOffset + (dimension.height / 2.0))
                hypot(a, b) / (2 * max(config.targetWidth, config.targetHeight)).toDouble()
            } else {
                0.0
            }

        // image nodes always contains exactly 1 image nodes by definition.
        return 1
    }

    override fun computeAspectRatio(): Double {
        aspectRatio = sourceImage.aspectRatio
        return aspectRatio
    }

    override fun clone(): ImageNode =
        ImageNode(
            sourceImage = sourceImage.copy(),
            offCenterDistance = offCenterDistance,
            dimension = dimension.copy(),
            aspectRatio = aspectRatio
        )

    override fun toString(): String =
        "$dimension ≈ ${(100.0 * (dimension.width / sourceImage.dimension.width)).roundToInt()}% of " +
            "${sourceImage.dimension.widthAsInt}x${sourceImage.dimension.heightAsInt} " +
            "(weight: ${sourceImage.desiredRelativeWeight}) @${"%.${3}f".format(sourceImage.aspectRatio)}"

    override fun hashCode(): Int {
        var result = sourceImage.hashCode()
        result = 31 * result + offCenterDistance.hashCode()
        result = 31 * result + dimension.hashCode()
        result = 31 * result + aspectRatio.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageNode) return false
        return sourceImage == other.sourceImage &&
            offCenterDistance == other.offCenterDistance &&
            dimension == other.dimension &&
            aspectRatio == other.aspectRatio
    }
}

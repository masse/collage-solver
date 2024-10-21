package se.kodverket.collage.layoutsolver

import kotlin.math.min

/**
 * Represents a layout node in a layout tree.
 *
 * A `LayoutNode` is an internal (non-leaf)node in the tree that defines the layout properties of a section of the total canvas.
 * It contains information about the layout direction and size of the node and provides methods to
 * recursively compute the dimensions of the node itself, and it's descendant child nodes.
 *
 */
class LayoutNode(
    override var aspectRatio: Double = 0.0,
    override var dimension: Dimension = Dimension(0.0, 0.0),
    var slicingDirection: SlicingDirection,
    var imageNodeCount: Int = 0,
    var left: Node,
    var right: Node,
) : Node {
    override fun toString(): String = "LayoutNode($slicingDirection $dimension @${"%.${3}f".format(aspectRatio)})"

    /**
     * Calculates the dimension of a node in a layout tree.
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
        val width = min(calculatedWidth, parentDimension.width)
        val height = width / aspectRatio

        // Set the dimension of the node to the calculated width and height
        dimension = Dimension(width, height)

        // Initialize the count of image nodes in the subtree to 0
        imageNodeCount = 0

        // Calculate the dimension of the left and right child nodes based on the slicing direction
        if (slicingDirection == SlicingDirection.V) {
            // If the slicing direction is vertical, calculate the dimension of the left child node
            // and add it to the count of image nodes
            imageNodeCount = left.computeDimensions(dimension, config, currentXOffset, currentYOffset)

            // Calculate the dimension of the right child node and add it to the count of image nodes
            imageNodeCount +=
                right.computeDimensions(
                    dimension,
                    config,
                    currentXOffset + left.dimension.width,
                    currentYOffset
                )
        } else {
            // If the slicing direction is horizontal, calculate the dimension of the left child node
            // and add it to the count of image nodes
            imageNodeCount = left.computeDimensions(dimension, config, currentXOffset, currentYOffset)

            // Calculate the dimension of the right child node and add it to the count of image nodes
            imageNodeCount +=
                right.computeDimensions(
                    dimension,
                    config,
                    currentXOffset,
                    currentYOffset + left.dimension.height
                )
        }

        // Return the count of image nodes in the subtree rooted at this node
        return imageNodeCount
    }

    /**
     * Recursively computes the aspect ratio of the node which is based on the aspect ratios of the left and right child nodes.
     *
     * @return The computed aspect ratio of the node.
     */
    override fun computeAspectRatio(): Double {
        // Compute the aspect ratios of the left and right child nodes
        val leftAR = left.computeAspectRatio()
        val rightAR = right.computeAspectRatio()

        // If the slicing direction is vertical, calculate the aspect ratio as the sum of the left and right aspect ratios
        if (slicingDirection == SlicingDirection.V) {
            aspectRatio = leftAR + rightAR
            return aspectRatio
        } else {
            // If the slicing direction is horizontal, calculate the aspect ratio as the product of the left and right aspect ratios
            // divided by their sum
            aspectRatio = leftAR * rightAR / (leftAR + rightAR)
            return aspectRatio
        }
    }

    override fun clone(): LayoutNode =
        LayoutNode(
            slicingDirection = slicingDirection,
            aspectRatio = aspectRatio,
            dimension = dimension.copy(),
            imageNodeCount = imageNodeCount,
            left = left.clone(),
            right = right.clone()
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayoutNode) return false

        return aspectRatio == other.aspectRatio &&
            dimension == other.dimension &&
            slicingDirection == other.slicingDirection &&
            imageNodeCount == other.imageNodeCount &&
            left == other.left &&
            right == other.right
    }

    override fun hashCode(): Int {
        var result = aspectRatio.hashCode()
        result = 31 * result + dimension.hashCode()
        result = 31 * result + slicingDirection.hashCode()
        result = 31 * result + imageNodeCount
        result = 31 * result + left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }
}

/**
 * Converts a partial layout node to a full layout node.
 *
 * @param node The partial layout node to convert.
 * @return The converted layout node.
 * @throws IllegalArgumentException If the node is not a partial layout node.
 */
fun toLayoutNode(node: Any): LayoutNode =
    if (node is PartialLayoutNode) {
        LayoutNode(
            slicingDirection = node.slicingDirection,
            // Recursively convert the left and right children to a LayoutNode nodes unless it's an image node - then it can be added as is.
            left = if (node.left is ImageNode) node.left!! as ImageNode else toLayoutNode(node.left!!),
            right = if (node.right is ImageNode) node.right!! as ImageNode else toLayoutNode(node.right!!)
        )
    } else {
        // Throw an exception if the node is not a partial layout node
        throw IllegalArgumentException("Invalid node type $node")
    }

/**
 * Represents a partial layout node in a layout tree.
 *
 * While constructing the tree, the internal nodes will initially lack both left and right child nodes.
 * When the tree is fully populated we can safely convert each partial layout node to a full layout node that is guaranteed
 * to have both a left and right child, which enables stronger typing.
 */
class PartialLayoutNode(
    val slicingDirection: SlicingDirection,
    var left: Any? = null,
    var right: Any? = null,
)

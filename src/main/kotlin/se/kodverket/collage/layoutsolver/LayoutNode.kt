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
data class LayoutNode(
    override val aspectRatio: Double = 0.0,
    override val dimension: Dimension = Dimension(0.0, 0.0),
    val slicingDirection: SlicingDirection,
    val imageNodeCount: Int = 0,
    val left: Node,
    val right: Node,
) : Node {
    override fun toString(): String = "LayoutNode($slicingDirection $dimension @${"%.${3}f".format(aspectRatio)})"

    /**
     * Calculates the dimension of a node in a layout tree.
     *
     * @param parentDimension The dimension of the parent node.
     * @param config The configuration for the collage.
     * @param currentXOffset The current X offset of the node.
     * @param currentYOffset The current Y offset of the node.
     * @return Pair of (new LayoutNode with computed dimensions, count of image nodes in subtree)
     */
    override fun computeDimensions(
        parentDimension: Dimension,
        config: CollageConfig,
        currentXOffset: Double,
        currentYOffset: Double,
    ): Pair<LayoutNode, Int> {
        // Calculate the width based on the aspect ratio and the height of the parent node
        val calculatedWidth = aspectRatio * parentDimension.height

        // If the calculated width exceeds the width of the parent node, cap it
        val width = min(calculatedWidth, parentDimension.width)
        val height = width / aspectRatio
        val newDimension = Dimension(width, height)

        // Calculate the dimension of the left and right child nodes based on the slicing direction
        val (newLeft, leftCount) = if (slicingDirection == SlicingDirection.V) {
            // If the slicing direction is vertical, calculate the dimension of the left child node
            left.computeDimensions(newDimension, config, currentXOffset, currentYOffset)
        } else {
            // If the slicing direction is horizontal, calculate the dimension of the left child node
            left.computeDimensions(newDimension, config, currentXOffset, currentYOffset)
        }

        val (xOffset, yOffset) = if (slicingDirection == SlicingDirection.V) {
            Pair(currentXOffset + newLeft.dimension.width, currentYOffset)
        } else {
            Pair(currentXOffset, currentYOffset + newLeft.dimension.height)
        }

        // Calculate the dimension of the right child node
        val (newRight, rightCount) = right.computeDimensions(newDimension, config, xOffset, yOffset)

        val totalCount = leftCount + rightCount

        // Return new node with updated values
        return Pair(
            copy(
                dimension = newDimension,
                imageNodeCount = totalCount,
                left = newLeft,
                right = newRight
            ),
            totalCount
        )
    }

    /**
     * Recursively computes the aspect ratio of the node which is based on the aspect ratios of the left and right child nodes.
     *
     * @return Pair of (new LayoutNode with computed aspect ratio, the aspect ratio value)
     */
    override fun computeAspectRatio(): Pair<LayoutNode, Double> {
        // Compute the aspect ratios of the left and right child nodes
        val (newLeft, leftAR) = left.computeAspectRatio()
        val (newRight, rightAR) = right.computeAspectRatio()

        // If the slicing direction is vertical, calculate the aspect ratio as the sum of the left and right aspect ratios
        val newAspectRatio = if (slicingDirection == SlicingDirection.V) {
            leftAR + rightAR
        } else {
            // If the slicing direction is horizontal, calculate the aspect ratio as the product of the left and right aspect ratios
            // divided by their sum
            leftAR * rightAR / (leftAR + rightAR)
        }

        return Pair(copy(aspectRatio = newAspectRatio, left = newLeft, right = newRight), newAspectRatio)
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
            left = node.left.let { child ->
                when (child) {
                    is ImageNode -> child
                    is PartialLayoutNode -> toLayoutNode(child)
                    else -> throw IllegalArgumentException("Invalid left child type: $child")
                }
            },
            right = node.right.let { child ->
                when (child) {
                    is ImageNode -> child
                    is PartialLayoutNode -> toLayoutNode(child)
                    else -> throw IllegalArgumentException("Invalid right child type: $child")
                }
            }
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

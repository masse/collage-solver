package se.kodverket.collage.layoutsolver

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random.Default.nextBoolean
import se.kodverket.collage.generic.ScoredIndividual
import se.kodverket.collage.layoutsolver.SlicingDirection.H
import se.kodverket.collage.layoutsolver.SlicingDirection.V

/**
 * Represents the solution of a layout arrangement problem that organizes and scores a collage
 * layout based on specified configurations and parameters.
 *
 * @property rootNode The root node of the layout, representing the hierarchical structure of the layout.
 * @property config The configuration for the collage, including dimensions and scoring parameters.
 * @property score The current score of the layout solution, representing how optimized the solution is.
 */
class LayoutSolution(
    val rootNode: LayoutNode,
    val config: CollageConfig,
    var score: Double = 0.0,
) {
    private var nodes: Pair<MutableList<LayoutNode>, MutableList<ImageNode>> = Pair(mutableListOf(), mutableListOf())
    private val totalArea: Double = config.targetWidth.toDouble() * config.targetHeight.toDouble() // S

    fun clone(): LayoutSolution = LayoutSolution(rootNode.clone(), config, score)

    fun mutate(): LayoutSolution {
        // Randomly swap either the slicing direction of two Layout nodes or the source image of two random Image nodes.
        // (could be a no-op if same node is selected, I know)
        if (nextBoolean()) {
            val node1 = layoutNodes().random()
            val node2 = layoutNodes().random()
            node1.slicingDirection = node2.slicingDirection.also { node2.slicingDirection = node1.slicingDirection }
        } else {
            val node1 = imageNodes().random()
            val node2 = imageNodes().random()
            node1.sourceImage = node2.sourceImage.also { node2.sourceImage = node1.sourceImage }
        }

        return this
    }

    fun score(): ScoredIndividual<LayoutSolution> {
        // 1. Calculate AR recursively
        rootNode.computeAspectRatio()

        // 2. Calculate dimensions recursively
        rootNode.computeDimensions(
            Dimension(config.targetWidth.toDouble(), config.targetHeight.toDouble()),
            config,
            0.0,
            0.0
        )

        // 3. Now for the actual scoring, we only need to iterate the actual image nodes
        var areaCoveredByImageNodes = 0.0
        var relativeSizeMismatchCost = 0.0
        var offCenterMismatchCost = 0.0

        imageNodes().forEach { imageNode ->
            // Measure 1: How well is the wanted relative size realized in this layout solution?
            areaCoveredByImageNodes += imageNode.dimension.area

            // Calculate and accumulate relative image size mismatch cost
            relativeSizeMismatchCost += calculateRelativeImageSizeMismatchCost(imageNode)

            // Measure 3: How centered is the feature images (those with relativeWeight > 1)?
            offCenterMismatchCost += imageNode.offCenterDistance
        }

        // Measure 2: How much of target canvas area was not covered by images? (0..1.00, lower is better)
        val uncoveredCanvasAreaPercentage = 1.0 - areaCoveredByImageNodes / totalArea

        // return a (weighted) cost sum of all the measures
        this.score =
            config.scoringFactors.canvasCoverage * uncoveredCanvasAreaPercentage +
            config.scoringFactors.relativeImageSize * relativeSizeMismatchCost +
            config.scoringFactors.centeredFeature * offCenterMismatchCost
        return ScoredIndividual(this.score, this)
    }

    fun layoutNodes(): MutableList<LayoutNode> {
        if (nodes.first.isEmpty()) {
            nodes = collectNodes(rootNode, Pair(mutableListOf(), mutableListOf()))
        }
        return nodes.first
    }

    fun imageNodes(): MutableList<ImageNode> {
        if (nodes.second.isEmpty()) {
            nodes = collectNodes(rootNode, Pair(mutableListOf(), mutableListOf()))
        }
        return nodes.second
    }

    /**
     * Calculates the mismatch cost between the desired and actual (realized) relative image sizes in the layout.
     * The cost is determined based on the deviation of each image's actual calculated size from its desired size,
     * and the severity of the penalty depends on whether the image is a feature image or not.
     * Deviations from the desired weight are penalized more aggressively for featured images, since we specifically
     * expressed a desire about their relative size in the final image.
     *
     * @param imageNode The image node for which the mismatch cost is calculated. This includes information
     *        about the source image, desired weight, and the actual dimensions in the layout.
     * @return A double representing the calculated mismatch cost for the given image node. Higher values
     *         indicate a greater mismatch between the desired and actual image sizes.
     */
    private fun calculateRelativeImageSizeMismatchCost(imageNode: ImageNode): Double {
        val desiredRelativeWeight = imageNode.sourceImage.desiredRelativeWeight / config.desiredRelativeWeightSum.toDouble()
        val actualRelativeWeight = imageNode.dimension.area / totalArea
        val deviation = actualRelativeWeight / desiredRelativeWeight

        val isFeatureImage = imageNode.sourceImage.desiredRelativeWeight > 1

        return when {
            // Feature images: Highest penalties for deviations here as we actually requested this specifically for this image
            isFeatureImage -> {
                if (deviation < 1.0) {
                    // Undersized feature images - severe penalty, grows rapidly
                    2.5 * (1.0 / deviation).pow(2.2)
                } else {
                    // Oversized feature images - lower penalty than undersized
                    0.8 * deviation.pow(1.6)
                }
            }

            // Non-feature images too small: Medium penalty
            deviation < 1.0 -> {
                0.4 * (1.0 / deviation).pow(1.8)
            }

            // Non-feature images too large: Lowest penalty
            else -> {
                0.2 * deviation.pow(1.5)
            }
        }
    }

    override fun toString(): String = "$rootNode"
}

/**
 * Represents a node in a layout structure, used for computing geometry
 * and layouts within a hierarchical data structure.
 *
 * Its primary responsibilities include
 * managing dimensions, computing aspect ratios, and cloning itself. Nodes may serve roles as internal
 * or leaf nodes based on specific implementations.
 *
 * @property aspectRatio The aspect ratio of the node.
 * @property dimension The dimension (width and height) of the node.
 */
sealed interface Node {
    var aspectRatio: Double
    var dimension: Dimension

    fun computeDimensions(
        parentDimension: Dimension,
        config: CollageConfig,
        currentXOffset: Double,
        currentYOffset: Double,
    ): Int

    fun computeAspectRatio(): Double

    fun clone(): Node
}

/**
 * Generates a layout solution candidate. The resulting layout is in no way optimal and only abides to the
 * basic constraints of a valid collage layout solution:
 *  - Images may not be cropped or rotated.
 *  - Images may not overlap other images.
 *  - Images mage may be scaled down to fit inside their parent container.
 *  - Images must maintain their original aspect ratio.
 *  - All images combined must fit inside the defined canvas target size.
 *
 * @param images The list of source images to be placed in the collage.
 * @param config The configuration for the collage.
 * @return The generated layout solution.
 */
fun generateLayoutSolution(
    images: List<SourceImage>,
    config: CollageConfig,
): LayoutSolution {
    val root = PartialLayoutNode(if (nextBoolean()) H else V)

    // Create a tree of n-1 V|H nodes
    val internalNodes = createInternalLayoutNodes(root, images.size - 1)

    // Distribute the n images as ImageNodes in the tree
    distributeImagesToNodes(images, internalNodes)

    return LayoutSolution(toLayoutNode(root), config)
}

/**
 * Creates a list of internal layout nodes.
 *
 * This function generates a tree of (n-1) V|H nodes, starting from the given root node.
 * The slicing direction of each internal node is randomly determined.
 *
 * @param root The root node of the tree.
 * @param count The number of internal nodes to create.
 * @return A mutable list of partial layout nodes representing the tree.
 */
private fun createInternalLayoutNodes(
    root: PartialLayoutNode,
    count: Int,
): MutableList<PartialLayoutNode> {
    val nodes = mutableListOf(root)
    for (i in 1 until count) {
        val newNode = PartialLayoutNode(slicingDirection = if (nextBoolean()) H else V)
        val parent = nodes.random()
        if (parent.left == null) {
            parent.left = newNode
        } else {
            parent.right = newNode
            nodes.remove(parent)
        }
        nodes.add(newNode)
    }
    return nodes
}

/**
 * Distributes the given list of images to the nodes in the provided mutable list.
 *
 * @param images The list of images to be distributed.
 * @param nodes The mutable list of nodes to distribute the images to.
 */
private fun distributeImagesToNodes(
    images: List<SourceImage>,
    nodes: MutableList<PartialLayoutNode>,
) {
    images.forEach { image ->
        val node = nodes.random()
        if (node.left == null) {
            node.left = ImageNode(sourceImage = image)
        } else {
            node.right = ImageNode(sourceImage = image)
            // Node is now completely populated and can be removed from list of nodes
            nodes.remove(node)
        }
    }
}

/**
 * This function performs a crossover operation between two parent solutions and
 * swaps layout directions from a subtree of the same size in the two layout solutions.
 *
 * @param parents A pair of parent layout solutions.
 * @return A new LayoutSolution which is the result of the crossover operation.
 */
fun crossBreedIndividuals(parents: Pair<LayoutSolution, LayoutSolution>): LayoutSolution {
    val (mother, father) = parents

    // Try to find a suitable layout node candidate from the mother's side...
    val motherNode =
        mother
            .layoutNodes()
            .filter { it.imageNodeCount > 3 }
            .takeUnless { it.isEmpty() }
            ?.random()

    // ...and a matching node from the father
    motherNode?.let {
        // Filter nodes with the same imageNodeCount as the mother's node
        father
            .layoutNodes()
            .filter { it.imageNodeCount == motherNode.imageNodeCount }
            .takeUnless { it.isEmpty() }
            ?.random()
            ?.let { fatherNode ->
                // ...then swap the slicing direction for all layout nodes in the subtrees
                collectNodes(motherNode).first.zip(collectNodes(fatherNode).first) { node1, node2 ->
                    node1.slicingDirection =
                        node2.slicingDirection.also { node2.slicingDirection = node1.slicingDirection }
                }
            }
    }

    // Use a clone of the parent that had the best score as offspring
    return if (mother.score < father.score) mother.clone() else father.clone()
}

/**
 * Recursively collects all LayoutNodes and ImageNodes into lists for more efficient access.
 *
 * @param node The current node being visited.
 * @param collectedNodes A pair of mutable lists to store the collected LayoutNodes and ImageNodes.
 * @return A pair of mutable lists containing all the collected LayoutNodes and ImageNodes.
 */
fun collectNodes(
    node: Node,
    collectedNodes: Pair<MutableList<LayoutNode>, MutableList<ImageNode>> = Pair(mutableListOf(), mutableListOf()),
): Pair<MutableList<LayoutNode>, MutableList<ImageNode>> {
    when (node) {
        // If the current node is an ImageNode, simply  it to the list of collected ImageNodes.
        is ImageNode -> collectedNodes.second.add(node)
        // If the current node is a LayoutNode, add it to the list of collected LayoutNodes and recursively
        // collect its left and right child nodes.
        is LayoutNode -> {
            collectedNodes.first.add(node)
            collectNodes(node.left, collectedNodes)
            collectNodes(node.right, collectedNodes)
        }
    }
    return collectedNodes
}

data class ScoringFactors(
    val canvasCoverage: Double = 1.0,
    val relativeImageSize: Double = 1.0,
    val centeredFeature: Double = 1.0,
)

data class SourceImage(
    val fileName: String,
    val dimension: Dimension,
    val desiredRelativeWeight: Int,
    val rotation: Rotation = Rotation.ROT_0,
) {
    val aspectRatio = dimension.width / dimension.height
}

data class Dimension(
    val width: Double,
    val height: Double,
) {
    val widthAsInt get() = width.roundToInt()
    val heightAsInt get() = height.roundToInt()

    val area get() = width * height

    override fun toString(): String = "${widthAsInt}x$heightAsInt"
}

enum class SlicingDirection {
    V,
    H,
}

enum class Rotation(
    val degrees: Int,
    val mirrored: Boolean = false,
) {
    ROT_0(0),
    ROT_CW_90(90),
    ROT_180(180),
    ROT_CW_270(270),
    MIRROR_HORIZONTAL(0, true),
    MIRROR_VERTICAL(180, true),
    MIRROR_HORIZONTAL_ROT_270_CW(270, true),
    MIRROR_HORIZONTAL_ROT_90_CW(90, true),
}

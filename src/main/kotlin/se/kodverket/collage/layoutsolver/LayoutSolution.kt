package se.kodverket.collage.layoutsolver

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
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
data class LayoutSolution(
    val rootNode: LayoutNode,
    val config: CollageConfig,
    val score: Double = 0.0,
) {
    private val totalArea: Double = config.targetWidth.toDouble() * config.targetHeight.toDouble() // S

    fun mutate(random: Random = Random.Default): LayoutSolution {
        // Randomly swap either the slicing direction of two Layout nodes or the source image of two random Image nodes.
        return if (random.nextBoolean()) {
            // Swap slicing directions
            val nodes = layoutNodes()
            if (nodes.size < 2) return this // Not enough nodes to swap
            val (node1, node2) = pickTwoRandomLayoutNodes(nodes, random)
            val newRoot = swapSlicingDirections(rootNode, node1, node2) as LayoutNode
            copy(rootNode = newRoot, score = 0.0)
        } else {
            // Swap source images
            val nodes = imageNodes()
            if (nodes.size < 2) return this // Not enough nodes to swap
            val (node1, node2) = pickTwoRandomImageNodes(nodes, random)
            val newRoot = swapSourceImages(rootNode, node1, node2) as LayoutNode
            copy(rootNode = newRoot, score = 0.0)
        }
    }

    /**
     * Recursively rebuilds tree with swapped slicing directions at target nodes.
     */
    private fun swapSlicingDirections(node: Node, target1: LayoutNode, target2: LayoutNode): Node {
        return when (node) {
            is ImageNode -> node
            is LayoutNode -> {
                val newDirection = when {
                    node === target1 -> target2.slicingDirection
                    node === target2 -> target1.slicingDirection
                    else -> node.slicingDirection
                }
                node.copy(
                    slicingDirection = newDirection,
                    left = swapSlicingDirections(node.left, target1, target2),
                    right = swapSlicingDirections(node.right, target1, target2)
                )
            }
        }
    }

    /**
     * Recursively rebuilds tree with swapped source images at target nodes.
     */
    private fun swapSourceImages(node: Node, target1: ImageNode, target2: ImageNode): Node {
        return when (node) {
            is ImageNode -> {
                when {
                    node === target1 -> node.copy(sourceImage = target2.sourceImage)
                    node === target2 -> node.copy(sourceImage = target1.sourceImage)
                    else -> node
                }
            }
            is LayoutNode -> {
                node.copy(
                    left = swapSourceImages(node.left, target1, target2),
                    right = swapSourceImages(node.right, target1, target2)
                )
            }
        }
    }

    /**
     * Computes a weighted cost score for this solution.
     *
     * Measures (all are costs, lower is better):
     *  - Canvas coverage: fraction of canvas area not covered by images.
     *  - Relative image size mismatch: deviation from each image's desired relative area.
     *  - Centered feature: distance of feature images (weight > 1) from canvas center.
     *
     * Weights are provided via [CollageConfig.scoringFactors]. This function is pure and returns
     * a new [LayoutSolution] instance with computed geometry and score.
     */
    fun score(): ScoredIndividual<LayoutSolution> {
        // 1. Calculate AR recursively and get new root with computed aspect ratios
        val (rootWithAR, _) = rootNode.computeAspectRatio()

        // 2. Calculate dimensions recursively and get new root with computed dimensions
        val (rootWithDimensions, _) = rootWithAR.computeDimensions(
            Dimension(config.targetWidth.toDouble(), config.targetHeight.toDouble()),
            config,
            0.0,
            0.0
        )

        // 3. Now for the actual scoring, we only need to iterate the actual image nodes
        var areaCoveredByImageNodes = 0.0
        var relativeSizeMismatchCost = 0.0
        var offCenterMismatchCost = 0.0

        // Collect image nodes once (avoid allocating layout node list) and iterate in-place
        collectImageNodes(rootWithDimensions).forEach { imageNode ->
            // Measure 1: How well is the wanted relative size realized in this layout solution?
            areaCoveredByImageNodes += imageNode.dimension.area

            // Calculate and accumulate relative image size mismatch cost
            relativeSizeMismatchCost += calculateRelativeImageSizeMismatchCost(imageNode)

            // Measure 3: How centered is the feature images (those with relativeWeight > 1)?
            offCenterMismatchCost += imageNode.offCenterDistance
        }

        // Measure 2: How much of target canvas area was not covered by images? (0..1.00, lower is better)
        val uncoveredCanvasAreaPercentage = 1.0 - areaCoveredByImageNodes / totalArea

        // Calculate the (weighted) cost sum of all the measures
        val computedScore =
            config.scoringFactors.canvasCoverage * uncoveredCanvasAreaPercentage +
            config.scoringFactors.relativeImageSize * relativeSizeMismatchCost +
            config.scoringFactors.centeredFeature * offCenterMismatchCost
        
        // Return a new LayoutSolution with updated root and computed score (single copy)
        val finalSolution = copy(rootNode = rootWithDimensions, score = computedScore)
        return ScoredIndividual(computedScore, finalSolution)
    }

    fun layoutNodes(): List<LayoutNode> {
        return collectNodes(rootNode, Pair(mutableListOf(), mutableListOf())).first
    }

    fun imageNodes(): List<ImageNode> {
        return collectNodes(rootNode, Pair(mutableListOf(), mutableListOf())).second
    }

    /**
     * Calculates the relative size mismatch cost for an image node based on its size compared
     * to its desired size and whether it is a feature image or not. The cost is higher for
     * significant deviations, particularly for undersized feature images as that is a
     * specifically requested aspect of the finished collage.
     *
     * @param imageNode The image node for which the size mismatch cost is calculated.
     * @return The calculated size mismatch cost as a double value.
     */
    private fun calculateRelativeImageSizeMismatchCost(imageNode: ImageNode): Double {
        // Penalty constants - higher values = more severe penalties
        val featureUndersizedMultiplier = SizeMismatchPenalties.FEATURE_UNDERSIZED_MULTIPLIER
        val featureUndersizedExponent = SizeMismatchPenalties.FEATURE_UNDERSIZED_EXPONENT
        val featureOversizedMultiplier = SizeMismatchPenalties.FEATURE_OVERSIZED_MULTIPLIER
        val featureOversizedExponent = SizeMismatchPenalties.FEATURE_OVERSIZED_EXPONENT
        val nonFeatureUndersizedMultiplier = SizeMismatchPenalties.NON_FEATURE_UNDERSIZED_MULTIPLIER
        val nonFeatureUndersizedExponent = SizeMismatchPenalties.NON_FEATURE_UNDERSIZED_EXPONENT
        val nonFeatureOversizedMultiplier = SizeMismatchPenalties.NON_FEATURE_OVERSIZED_MULTIPLIER
        val nonFeatureOversizedExponent = SizeMismatchPenalties.NON_FEATURE_OVERSIZED_EXPONENT

        // Thresholds
        val featureImageThreshold = SizeMismatchPenalties.FEATURE_IMAGE_THRESHOLD
        val perfectSizeRatio = SizeMismatchPenalties.PERFECT_SIZE_RATIO

        val desiredRelativeWeight = imageNode.sourceImage.desiredRelativeWeight / config.desiredRelativeWeightSum.toDouble()
        val actualRelativeWeight = imageNode.dimension.area / totalArea
        val desiredSizeRatio = actualRelativeWeight / desiredRelativeWeight

        val isFeatureImage = imageNode.sourceImage.desiredRelativeWeight > featureImageThreshold

        return when {
            // Feature images: Severe penalties for undersized, moderate for oversized
            isFeatureImage -> {
                if (desiredSizeRatio < perfectSizeRatio) {
                    featureUndersizedMultiplier * (perfectSizeRatio / desiredSizeRatio).pow(featureUndersizedExponent)
                } else {
                    featureOversizedMultiplier * desiredSizeRatio.pow(featureOversizedExponent)
                }
            }

            // Non-feature undersized: Medium penalty
            desiredSizeRatio < perfectSizeRatio -> {
                nonFeatureUndersizedMultiplier * (perfectSizeRatio / desiredSizeRatio).pow(nonFeatureUndersizedExponent)
            }

            // Non-feature oversized: Lowest penalty
            else -> {
                nonFeatureOversizedMultiplier * desiredSizeRatio.pow(nonFeatureOversizedExponent)
            }
        }
    }

    override fun toString(): String = "$rootNode"

    /** Collects only ImageNodes from a tree into a single list to minimize allocations during scoring. */
    private fun collectImageNodes(
        node: Node,
        acc: MutableList<ImageNode> = mutableListOf(),
    ): MutableList<ImageNode> {
        when (node) {
            is ImageNode -> acc.add(node)
            is LayoutNode -> {
                collectImageNodes(node.left, acc)
                collectImageNodes(node.right, acc)
            }
        }
        return acc
    }

    /** Returns two random layout nodes from the provided list (may be the same to preserve original behavior). */
    private fun pickTwoRandomLayoutNodes(nodes: List<LayoutNode>, random: Random): Pair<LayoutNode, LayoutNode> {
        // Note: nodes.size >= 2 is guaranteed by caller
        val first = nodes.random(random)
        val second = nodes.random(random)
        return Pair(first, second)
    }

    /** Returns two random image nodes from the provided list (may be the same to preserve original behavior). */
    private fun pickTwoRandomImageNodes(nodes: List<ImageNode>, random: Random): Pair<ImageNode, ImageNode> {
        // Note: nodes.size >= 2 is guaranteed by caller
        val first = nodes.random(random)
        val second = nodes.random(random)
        return Pair(first, second)
    }
}

/**
 * Named constants for size-mismatch penalty shaping to avoid magic numbers in scoring.
 */
private object SizeMismatchPenalties {
    // Feature images: harsher penalty when undersized
    const val FEATURE_UNDERSIZED_MULTIPLIER: Double = 2.5
    const val FEATURE_UNDERSIZED_EXPONENT: Double = 2.2
    const val FEATURE_OVERSIZED_MULTIPLIER: Double = 0.8
    const val FEATURE_OVERSIZED_EXPONENT: Double = 1.6

    // Non-feature images: softer penalties
    const val NON_FEATURE_UNDERSIZED_MULTIPLIER: Double = 0.4
    const val NON_FEATURE_UNDERSIZED_EXPONENT: Double = 1.8
    const val NON_FEATURE_OVERSIZED_MULTIPLIER: Double = 0.2
    const val NON_FEATURE_OVERSIZED_EXPONENT: Double = 1.5

    // Thresholds
    const val FEATURE_IMAGE_THRESHOLD: Int = 1
    const val PERFECT_SIZE_RATIO: Double = 1.0
}

/**
 * Represents a node in a layout structure, used for computing geometry
 * and layouts within a hierarchical data structure.
 *
 * Its primary responsibilities include
 * managing dimensions, computing aspect ratios, and creating new instances with computed values.
 * Nodes may serve roles as internal or leaf nodes based on specific implementations.
 *
 * @property aspectRatio The aspect ratio of the node.
 * @property dimension The dimension (width and height) of the node.
 */
sealed interface Node {
    val aspectRatio: Double
    val dimension: Dimension

    /**
     * Computes dimensions for this node and returns a new node with updated values.
     * @return Pair of (new node with computed dimensions, count of image nodes)
     */
    fun computeDimensions(
        parentDimension: Dimension,
        config: CollageConfig,
        currentXOffset: Double,
        currentYOffset: Double,
    ): Pair<Node, Int>

    /**
     * Computes aspect ratio for this node and returns a new node with updated value.
     * @return Pair of (new node with computed aspect ratio, the aspect ratio value)
     */
    fun computeAspectRatio(): Pair<Node, Double>
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
    random: Random = Random.Default,
): LayoutSolution {
    val root = PartialLayoutNode(if (random.nextBoolean()) H else V)

    // Create a tree of n-1 V|H nodes
    val internalNodes = createInternalLayoutNodes(root, images.size - 1, random)

    // Distribute the n images as ImageNodes in the tree
    distributeImagesToNodes(images, internalNodes, random)

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
    random: Random,
): MutableList<PartialLayoutNode> {
    val nodes = mutableListOf(root)
    repeat(count - 1) {
        val newNode = PartialLayoutNode(slicingDirection = if (random.nextBoolean()) H else V)
        val parent = nodes.random(random)
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
    random: Random,
) {
    images.forEach { image ->
        val node = nodes.random(random)
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
 * Performs a safe cross-over between two parent solutions.
 *
 * The operator attempts to find a subtree in the mother with more than three image nodes and
 * a matching subtree (same number of image nodes) in the father. If both are found, a new
 * tree is built by copying the mother's structure while replacing slicing directions in the
 * chosen subtree with those from the father's corresponding subtree. Neither input is mutated.
 *
 * Fallback and safety behavior:
 * - If the mother has no candidate subtree (<= 3 image nodes anywhere), the function returns the
 *   mother unchanged (no-op).
 * - If the father has no subtree with the same imageNodeCount as the chosen mother subtree,
 *   the function returns the mother unchanged (no-op).
 * - All copies are immutable data class copies to avoid aliasing/mutation bugs.
 *
 * @param parents A pair of parent layout solutions, where the first is considered the mother.
 * @return A new LayoutSolution if cross-over succeeded, otherwise the unmodified mother.
 */
fun crossBreedIndividuals(parents: Pair<LayoutSolution, LayoutSolution>, random: Random = Random.Default): LayoutSolution {
    val (mother, father) = parents

    // Try to find a suitable layout node candidate from the mother's side...
    val motherNode =
        mother
            .layoutNodes()
            .filter { it.imageNodeCount > 3 }
            .let { list -> if (list.isEmpty()) null else list.random(random) }
            ?: return mother // No suitable node found, return mother unchanged

    // Find a matching node from the father with the same imageNodeCount
    val fatherNode = father
        .layoutNodes()
        .filter { it.imageNodeCount == motherNode.imageNodeCount }
        .let { list -> if (list.isEmpty()) null else list.random(random) }
        ?: return mother // No matching node found, return mother unchanged

    // Create a new root tree by crossing over the subtrees
    val newMotherRoot = crossoverSubtrees(mother.rootNode, motherNode, fatherNode) as LayoutNode
    
    // Return new solution with crossed-over genetics from mother
    return mother.copy(rootNode = newMotherRoot, score = 0.0)
}

/**
 * Recursively traverses the tree and replaces slicing directions from father's subtree
 * when the target mother node is found, creating a new tree structure.
 */
private fun crossoverSubtrees(
    currentNode: Node,
    motherTarget: LayoutNode,
    fatherSource: LayoutNode
): Node {
    return when (currentNode) {
        is ImageNode -> currentNode
        is LayoutNode -> {
            if (currentNode === motherTarget) {
                // Found the target node - replace slicing directions with father's genetics
                replaceSlicingDirections(currentNode, fatherSource)
            } else {
                // Keep searching in children
                currentNode.copy(
                    left = crossoverSubtrees(currentNode.left, motherTarget, fatherSource),
                    right = crossoverSubtrees(currentNode.right, motherTarget, fatherSource)
                )
            }
        }
    }
}

/**
 * Recursively copies slicing directions from source subtree to target subtree,
 * creating a new tree structure without mutating the originals.
 */
private fun replaceSlicingDirections(target: Node, source: Node): Node {
    return when {
        target is ImageNode && source is ImageNode -> target
        target is LayoutNode && source is LayoutNode -> {
            target.copy(
                slicingDirection = source.slicingDirection,
                left = replaceSlicingDirections(target.left, source.left),
                right = replaceSlicingDirections(target.right, source.right)
            )
        }
        else -> target // Mismatched structure, keep target as-is
    }
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

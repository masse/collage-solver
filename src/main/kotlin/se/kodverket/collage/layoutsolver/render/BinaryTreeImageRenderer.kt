package se.kodverket.collage.layoutsolver.render

import java.awt.BasicStroke
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import se.kodverket.collage.concurrently
import se.kodverket.collage.layoutsolver.ImageNode
import se.kodverket.collage.layoutsolver.LayoutNode
import se.kodverket.collage.layoutsolver.LayoutSolution
import se.kodverket.collage.layoutsolver.Node
import se.kodverket.collage.layoutsolver.SlicingDirection.V

open class BinaryTreeImageRenderer(
    open val solution: LayoutSolution,
) {
    fun render(): BufferedImage {
        print("\nCollage render started..")
        val (renderedImage, duration) =
            measureTimedValue {
                val image = BufferedImage(solution.config.targetWidth, solution.config.targetHeight, TYPE_INT_RGB)
                val renderNodes = collectRenderNodes(this.solution.rootNode)
                runBlocking(Dispatchers.Default) { renderNodes.concurrently { renderNode(image, it) } }
                image
            }
        println("\nTotal rendering time $duration")
        return renderedImage
    }

    protected open fun renderNode(
        image: BufferedImage,
        renderNode: RenderNode,
    ) {
        print(".")
        val sourceImage = ImageIO.read(Path(renderNode.imageNode.sourceImage.fileName).toFile())
        image.createGraphics().apply {
            drawImage(
                sourceImage.getScaledInstance(
                    renderNode.imageNode.dimension.widthAsInt
                        .coerceAtLeast(1),
                    renderNode.imageNode.dimension.heightAsInt
                        .coerceAtLeast(1),
                    Image.SCALE_SMOOTH
                ),
                renderNode.xOffset,
                renderNode.yOffset,
                null
            )

            // Add border?
            if (solution.config.borderWidth > 0) {
                color = solution.config.borderColor
                stroke = BasicStroke(solution.config.borderWidth.toFloat())
                drawRect(
                    renderNode.xOffset,
                    renderNode.yOffset,
                    renderNode.imageNode.dimension.widthAsInt,
                    renderNode.imageNode.dimension.heightAsInt
                )
            }
            dispose()
        }
    }

    private fun collectRenderNodes(
        node: Node,
        xOffset: Double = 0.0,
        yOffset: Double = 0.0,
    ): List<RenderNode> =
        when (node) {
            is LayoutNode -> {
                // first child node is either to the left or on top of...
                collectRenderNodes(node.left, xOffset, yOffset) +
                    // ...the second node which is either to the right or below the first, depending on the slicing direction
                    if (node.slicingDirection == V) {
                        collectRenderNodes(node.right, xOffset + node.left.dimension.width, yOffset)
                    } else {
                        collectRenderNodes(node.right, xOffset, yOffset + node.left.dimension.height)
                    }
            }

            is ImageNode -> listOf(RenderNode(xOffset.roundToInt(), yOffset.roundToInt(), node))
        }
}

data class RenderNode(
    val xOffset: Int,
    val yOffset: Int,
    val imageNode: ImageNode,
)

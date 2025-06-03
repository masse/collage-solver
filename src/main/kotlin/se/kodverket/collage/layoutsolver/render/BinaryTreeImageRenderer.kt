package se.kodverket.collage.layoutsolver.render

import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.AffineTransform
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
import se.kodverket.collage.layoutsolver.Rotation
import se.kodverket.collage.layoutsolver.SlicingDirection.V

open class BinaryTreeImageRenderer(
    open val solution: LayoutSolution,
) {
    fun render(): BufferedImage {
        print("\nBuilding image composition...")
        val (renderedImage, duration) =
            measureTimedValue {
                val image = BufferedImage(solution.config.targetWidth, solution.config.targetHeight, TYPE_INT_RGB)
                val renderNodes = collectRenderNodes(this.solution.rootNode)
                runBlocking(Dispatchers.Default) { renderNodes.concurrently { renderNode(image, it) } }
                image
            }
        println("\nTotal composition time $duration")
        return renderedImage
    }

    protected open fun renderNode(
        image: BufferedImage,
        renderNode: RenderNode,
    ) {
        print(".")
        val sourceImage = loadSourceImage(renderNode)

        // Create a graphics context for the image
        val g2d = image.createGraphics() as Graphics2D
        try {
            val width =
                renderNode.imageNode.dimension.widthAsInt
                    .coerceAtLeast(1)
            val height =
                renderNode.imageNode.dimension.heightAsInt
                    .coerceAtLeast(1)
            val rotation = renderNode.imageNode.sourceImage.rotation

            val scaledImage = scaleImage(sourceImage, width, height, rotation)
            val transform = createTransform(renderNode, width, height, rotation)

            // Apply the transform and draw
            g2d.transform = transform
            g2d.drawImage(scaledImage, 0, 0, null)

            // Reset transformations in case of any further processing
            g2d.transform = AffineTransform()

            drawBorderIfNeeded(g2d, renderNode, width, height)
        } finally {
            g2d.dispose()
        }
    }

    private fun loadSourceImage(renderNode: RenderNode): BufferedImage =
        ImageIO.read(Path(renderNode.imageNode.sourceImage.fileName).toFile())

    private fun scaleImage(
        sourceImage: BufferedImage,
        width: Int,
        height: Int,
        rotation: Rotation,
    ): Image {
        // For rotated images, we need to consider the final orientation
        return if (needsDimensionSwap(rotation)) {
            sourceImage.getScaledInstance(height, width, Image.SCALE_SMOOTH)
        } else {
            sourceImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        }
    }

    private fun needsDimensionSwap(rotation: Rotation): Boolean =
        rotation == Rotation.ROT_CW_90 ||
            rotation == Rotation.ROT_CW_270 ||
            rotation == Rotation.MIRROR_HORIZONTAL_ROT_90_CW ||
            rotation == Rotation.MIRROR_HORIZONTAL_ROT_270_CW

    private fun createTransform(
        renderNode: RenderNode,
        width: Int,
        height: Int,
        rotation: Rotation,
    ): AffineTransform {
        val transform = AffineTransform()
        // Calculate the center point of where we want the rotated/mirrored image to appear
        val centerX = renderNode.xOffset.toDouble() + width / 2.0
        val centerY = renderNode.yOffset.toDouble() + height / 2.0

        if (rotation == Rotation.ROT_0 && !rotation.mirrored) {
            // No rotation or mirroring needed, translate to the correct position
            transform.translate(renderNode.xOffset.toDouble(), renderNode.yOffset.toDouble())
        } else {
            // Translate to center point
            transform.translate(centerX, centerY)

            // Apply appropriate transformations based on rotation type
            when {
                rotation == Rotation.MIRROR_HORIZONTAL_ROT_270_CW || rotation == Rotation.MIRROR_HORIZONTAL_ROT_90_CW -> {
                    // Rotate around this center point first
                    transform.rotate(Math.toRadians(rotation.degrees.toDouble()))
                    // Then apply horizontal mirroring
                    transform.scale(-1.0, 1.0)
                }

                rotation == Rotation.MIRROR_VERTICAL -> {
                    // Apply only vertical mirroring (flip along horizontal axis)
                    transform.scale(1.0, -1.0)
                }

                else -> {
                    // For other rotations, apply mirroring first if needed
                    if (rotation.mirrored) {
                        // Horizontal mirroring (flip along vertical axis)
                        transform.scale(-1.0, 1.0)
                    }
                    // Then rotate around this center point
                    transform.rotate(Math.toRadians(rotation.degrees.toDouble()))
                }
            }

            // Translate back so the center of the scaled image aligns with our target center point
            if (needsDimensionSwap(rotation)) {
                // For 90°/270°, the scaled image has swapped dimensions
                transform.translate(-height / 2.0, -width / 2.0)
            } else {
                transform.translate(-width / 2.0, -height / 2.0)
            }
        }

        return transform
    }

    private fun drawBorderIfNeeded(
        g2d: Graphics2D,
        renderNode: RenderNode,
        width: Int,
        height: Int,
    ) {
        if (solution.config.borderWidth > 0) {
            g2d.color = solution.config.borderColor
            g2d.stroke = BasicStroke(solution.config.borderWidth.toFloat())
            g2d.drawRect(renderNode.xOffset, renderNode.yOffset, width, height)
        }
    }

    private fun collectRenderNodes(
        node: Node,
        xOffset: Double = 0.0,
        yOffset: Double = 0.0,
    ): List<RenderNode> =
        when (node) {
            is LayoutNode -> {
                // The first child node is either to the left or on top of...
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

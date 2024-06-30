package se.kodverket.collage.layoutsolver.render

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import se.kodverket.collage.layoutsolver.LayoutSolution

class BinaryTreeDebugRenderer(override val solution: LayoutSolution) : BinaryTreeImageRenderer(solution) {
    override fun renderNode(
        image: BufferedImage,
        renderNode: RenderNode,
    ) {
        image.createGraphics().apply {
            color = if (renderNode.imageNode.sourceImage.desiredRelativeWeight > 1) Color.YELLOW else Color.ORANGE
            fillRect(
                renderNode.xOffset,
                renderNode.yOffset,
                renderNode.imageNode.dimension.widthAsInt,
                renderNode.imageNode.dimension.heightAsInt
            )
            if (solution.config.borderWidth > 0) {
                color = Color.white
                stroke = BasicStroke(solution.config.borderWidth.toFloat())
                drawRect(
                    renderNode.xOffset,
                    renderNode.yOffset,
                    renderNode.imageNode.dimension.widthAsInt,
                    renderNode.imageNode.dimension.heightAsInt
                )
            }
            dispose()

            centeredText(
                image,
                renderNode,
                listOf(
                    renderNode.imageNode.sourceImage.fileName,
                    "${renderNode.imageNode.sourceImage.dimension} -> ${renderNode.imageNode.dimension}"
                )
            )
        }
    }

    private fun centeredText(
        image: BufferedImage,
        renderNode: RenderNode,
        strings: List<String>,
    ) {
        image.createGraphics().apply {
            color = Color.black
            font = Font(Font.SANS_SERIF, Font.PLAIN, 8)
            val centerX = renderNode.xOffset + renderNode.imageNode.dimension.widthAsInt / 2
            val centerY = renderNode.yOffset + renderNode.imageNode.dimension.heightAsInt / 2
            strings.forEachIndexed { index, string ->
                drawString(
                    string,
                    centerX - string.length * 2,
                    centerY + index * 8
                )
            }
            dispose()
        }
    }
}

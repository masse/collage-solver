package se.kodverket.collage.layoutsolver.render

import java.io.PrintStream
import se.kodverket.collage.layoutsolver.ImageNode
import se.kodverket.collage.layoutsolver.LayoutNode
import se.kodverket.collage.layoutsolver.Node

class BinaryTreePrinter(private val tree: Node) {
    fun print(ps: PrintStream) = ps.println("Binary Tree: ${traversePreOrder(tree)}")

    private fun traversePreOrder(root: Node?): String {
        if (root == null || root !is LayoutNode) return ""

        val sb = StringBuilder()
        traverseNodes(sb, "", "   ", root, false)
        return sb.toString()
    }

    private fun traverseNodes(
        sb: StringBuilder,
        padding: String,
        pointer: String,
        node: Node,
        hasRightSibling: Boolean,
    ) {
        sb.appendLine()
        sb.append(padding).append(pointer)

        when (node) {
            is LayoutNode -> {
                sb.append("${node.slicingDirection}: ${node.dimension} ")
                val paddingForBoth = padding + if (hasRightSibling) "│  " else "   "

                traverseNodes(sb, paddingForBoth, "├──", node.left, true)
                traverseNodes(sb, paddingForBoth, "└──", node.right, false)
            }

            is ImageNode -> sb.append("${node.sourceImage.fileName} Size: $node")
        }
    }
}

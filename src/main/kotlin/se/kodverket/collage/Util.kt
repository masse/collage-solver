package se.kodverket.collage

import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import se.kodverket.collage.layoutsolver.LayoutSolution
import se.kodverket.collage.layoutsolver.render.BinaryTreeImageRenderer

suspend fun <T, R> Iterable<T>.concurrently(operation: (T) -> R): List<R> =
    coroutineScope {
        map { async { operation(it) } }.map { it.await() }
    }

// Non async version for comparing running without coroutine support

/*
fun <T, R> Iterable<T>.concurrently(operation: (T) -> R): List<R> = map { operation(it) }
*/

fun renderImage(
    fileName: String,
    layout: LayoutSolution,
) {
    ImageIO.write(
        BinaryTreeImageRenderer(layout).render(),
        "png",
        File("$fileName.png")
    )
}

fun String.toColor(): Color {
    require(matches(Regex("[0-9a-fA-F]{6}"))) { "Invalid color value $this" }

    val (r, g, b) = chunked(2) { it.toString().toInt(16) }

    return Color(r, g, b)
}

package se.kodverket.collage

import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_HORIZONTAL_NORMAL
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_270_CW
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_90_CW
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_MIRROR_VERTICAL
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_ROTATE_180
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_ROTATE_270_CW
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.ORIENTATION_VALUE_ROTATE_90_CW
import se.kodverket.collage.layoutsolver.Dimension
import se.kodverket.collage.layoutsolver.LayoutSolution
import se.kodverket.collage.layoutsolver.Rotation
import se.kodverket.collage.layoutsolver.render.BinaryTreeImageRenderer

suspend fun <T, R> Iterable<T>.concurrently(operation: (T) -> R): List<R> =
    coroutineScope {
        map { async { operation(it) } }.map { it.await() }
    }

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

/**
 * Gets the dimensions and rotation of an image file.
 *
 * @param imageFile The image file to analyze
 * @return A Pair containing the Dimension and Rotation (from exif data) of the image
 */
fun getImageMetadata(imageFile: File): Pair<Dimension, Rotation> {
    // Read image dimensions using ImageIO
    ImageIO.createImageInputStream(imageFile).use { imageInputStream ->
        val imageReader = ImageIO.getImageReaders(imageInputStream).next()
        try {
            imageReader.input = imageInputStream
            val width = imageReader.getWidth(0)
            val height = imageReader.getHeight(0)

            // Read EXIF orientation using Apache Commons Imaging
            val rotation =
                try {
                    val metadata = Imaging.getMetadata(imageFile)

                    // Extract orientation from EXIF metadata
                    val orientation =
                        when (metadata) {
                            is JpegImageMetadata -> {
                                val exif = metadata.exif
                                val orientationField = exif?.findField(TiffTagConstants.TIFF_TAG_ORIENTATION)
                                orientationField?.intValue ?: ORIENTATION_VALUE_HORIZONTAL_NORMAL
                            }
                            else -> ORIENTATION_VALUE_HORIZONTAL_NORMAL
                        }

                    // Convert orientation to a rotation angle
                    when (orientation) {
                        ORIENTATION_VALUE_HORIZONTAL_NORMAL -> Rotation.ROT_0
                        ORIENTATION_VALUE_MIRROR_HORIZONTAL -> Rotation.MIRROR_HORIZONTAL
                        ORIENTATION_VALUE_ROTATE_180 -> Rotation.ROT_180
                        ORIENTATION_VALUE_MIRROR_VERTICAL -> Rotation.MIRROR_VERTICAL
                        ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_270_CW -> Rotation.MIRROR_HORIZONTAL_ROT_270_CW
                        ORIENTATION_VALUE_ROTATE_90_CW -> Rotation.ROT_CW_90
                        ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_90_CW -> Rotation.MIRROR_HORIZONTAL_ROT_90_CW
                        ORIENTATION_VALUE_ROTATE_270_CW -> Rotation.ROT_CW_270
                        else -> Rotation.ROT_0
                    }
                } catch (e: Exception) {
                    println("Warning: Could not read EXIF orientation for ${imageFile.name}: ${e.message}")
                    Rotation.ROT_0 // Default to no rotation on error
                }

            // Swap dimensions if rotated 90 or 270 degrees
            val dimension =
                if (rotation == Rotation.ROT_CW_90 ||
                    rotation == Rotation.ROT_CW_270 ||
                    rotation == Rotation.MIRROR_HORIZONTAL_ROT_90_CW ||
                    rotation == Rotation.MIRROR_HORIZONTAL_ROT_270_CW
                ) {
                    Dimension(height.toDouble(), width.toDouble())
                } else {
                    Dimension(width.toDouble(), height.toDouble())
                }

            return Pair(dimension, rotation)
        } finally {
            imageReader.dispose()
        }
    }
}

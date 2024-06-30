package se.kodverket.collage

import java.awt.Color
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import se.kodverket.collage.layoutsolver.CollageConfig
import se.kodverket.collage.layoutsolver.DEFAULT_IMAGE_RELATIVE_WEIGHT
import se.kodverket.collage.layoutsolver.Dimension
import se.kodverket.collage.layoutsolver.FeatureImage
import se.kodverket.collage.layoutsolver.FeatureImageArgumentParser
import se.kodverket.collage.layoutsolver.ScoringFactors
import se.kodverket.collage.layoutsolver.SourceImage

private const val SOURCE_IMAGES_FILE_PATTERN = "*.{jpg,jpeg,png,JPG,JPEG,PNG}"

class Collage : CliktCommand() {
    init {
        context {
            helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true) }
        }
    }

    private val defaults = CollageConfig()
    private val path by argument(help = "Path to a directory containing source images (png and jpeg supported)")
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
    private val featureImages: List<FeatureImage> by argument()
        .multiple()
        .transformAll { featureImageList -> featureImageList.map { FeatureImageArgumentParser.parse(it) } }
        .help(
            """"Feature image filename(s) and the desired relative size e.g 
            |... sunny_day.jpg:5 ... 
            |The relative size weight determines how large an image should be in the 
            |output image compared to other images (whose relative image size factor by default is 1).
            """.trimMargin()
        )

    private val outputName by option(
        "-o",
        "--output",
        help = "image output name without extension, defaults to the directory name of path argument"
    )
    private val targetWidth by option("-w", "--target-width")
        .int()
        .restrictTo(min = 1)
        .default(defaults.targetWidth)
        .help("Width of output image")

    private val targetHeight by option("-h", "--target-height")
        .int()
        .restrictTo(min = 1)
        .default(defaults.targetHeight)
        .help("Height of output image")

    private val borderWidth by option("-bw", "--border-width")
        .int()
        .restrictTo(min = 0)
        .default(defaults.borderWidth)
        .help("Width of border framing each image")

    private val borderColor: Color by option("-bc", "--border-color")
        .convert { it.toColor() }
        .default(defaults.borderColor)
        .help("Border color in the form of a hexadecimal rgb string (like #ffffff for white)")

    private val maxScaleFactor by option("-msf", "--max-scale-factor")
        .double()
        .restrictTo(min = 0.0)
        .default(defaults.maxScaleFactor)
        .help("Max resize scale factor (1.0 means no larger that original size)")

    private val populationSize by option("-pop", "--population-size")
        .int()
        .restrictTo(min = 1)
        .default(defaults.populationSize)
        .help("Population size")

    private val generations by option("-gen", "--generations")
        .int()
        .restrictTo(min = 1)
        .default(defaults.numGenerations)
        .help("Number of generation to evolve")

    private val mutationProbability by option("-mp", "--mutation-probability")
        .double()
        .restrictTo(min = 0.0, max = 1.0)
        .default(defaults.mutationProbability)
        .help("Mutation probability")

    private val canvasCoverageWeight by option("-cc", "--canvas-coverage")
        .double()
        .default(defaults.scoringFactors.canvasCoverage).help(
            """The weight factor for the canvas area coverage fitness score - or in other words, how important is it that the final image 
            |is completely covered by the images (without any gaps etc)
            """.trimMargin()
        )

    private val relativeAreaCoverageWeight by option("-rac", "--relative-area-coverage")
        .double()
        .default(defaults.scoringFactors.relativeAreaCoverage).help(
            """The weight factor for relative image size preservation fitness score - or in other words, how important is it that 
                |the each image's relative size is preserved in the output image.
            """.trimMargin()
        )

    private val centeredFeatureWeight by option("-cf", "--centered-feature")
        .double()
        .default(defaults.scoringFactors.centeredFeature).help(
            """The weight factor for featured image centering fitness score - or in other words, how important is it that  
                |feature images are close to center in the output image.
            """.trimMargin()
        )

    override fun run() {
        measureTime {
            val sourceImages = readSourceImages(path, featureImages)
            val config =
                CollageConfig(
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    borderWidth = borderWidth,
                    borderColor = borderColor,
                    featureImages = featureImages,
                    maxScaleFactor = maxScaleFactor,
                    desiredRelativeWeightSum = sourceImages.sumOf { it.desiredRelativeWeight },
                    mutationProbability = mutationProbability,
                    numGenerations = generations,
                    populationSize = populationSize,
                    scoringFactors = ScoringFactors(canvasCoverageWeight, relativeAreaCoverageWeight, centeredFeatureWeight)
                )
            renderImage(outputName ?: path.name, CollageRunner().run(config, sourceImages).individual)
        }.also { println("Total execution time: $it") }
    }

    private fun readSourceImages(
        directory: Path,
        featureImages: List<FeatureImage>,
    ): List<SourceImage> {
        val (sourceImages, duration) =
            measureTimedValue {
                print("\nScanning images")
                runBlocking(Dispatchers.Default) {
                    directory.listDirectoryEntries(SOURCE_IMAGES_FILE_PATTERN).concurrently { imagePath ->
                        print(".")
                        val dimension = getImageDimensions(imagePath.toFile())
                        SourceImage(
                            fileName = imagePath.pathString,
                            dimension = dimension,
                            desiredRelativeWeight =
                                featureImages.find { it.name == imagePath.name }?.relativeWeight
                                    ?: DEFAULT_IMAGE_RELATIVE_WEIGHT
                        )
                    }
                }
            }
        println("\nScanning ${sourceImages.size} images took $duration")
        return sourceImages
    }

    private fun getImageDimensions(imageFile: File): Dimension {
        ImageIO.createImageInputStream(imageFile).use { imageInputStream ->
            val imageReader = ImageIO.getImageReaders(imageInputStream).next()
            try {
                imageReader.input = imageInputStream

                val width = imageReader.getWidth(0)
                val height = imageReader.getHeight(0)
                return Dimension(width.toDouble(), height.toDouble())
            } finally {
                imageReader.dispose()
            }
        }
    }
}

fun main(args: Array<String>) = Collage().main(args)

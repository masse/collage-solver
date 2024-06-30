package se.kodverket.collage.layoutsolver

import kotlin.test.Test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class FeatureImageArgumentParserTest {
    @Test
    fun `Parse feature image works for valid patterns`() {
        listOf(
            "image-1.png:3" to FeatureImage("image-1.png", 3),
            "1:341" to FeatureImage("1", 341),
            "_-img.special.åäö-2(2).png1.png:12" to FeatureImage("_-img.special.åäö-2(2).png1.png", 12)
        ).forEach { (input, expected) -> FeatureImageArgumentParser.parse(input) shouldBe expected }
    }

    @Test
    fun `Parse feature image fails for invalid pattern`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                FeatureImageArgumentParser.parse("Image-1.png-3")
            }
        exception.message shouldBe "Bad feature image input 'Image-1.png-3'"
    }
}

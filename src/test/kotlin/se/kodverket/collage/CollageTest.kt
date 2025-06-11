package se.kodverket.collage

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.Test
import com.github.ajalt.clikt.testing.test
import io.kotest.matchers.shouldBe

class CollageTest {
    @Test
    fun testCollageMain() {
        Files.deleteIfExists(Path("build/test-collage-main.png"))
        val command = Collage()
        command.test("docs/sample-images meerkat.jpg:8 -o build/test-collage-main")
        Path("build/test-collage-main.png").exists() shouldBe true
    }
}

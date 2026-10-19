package com.histmersion.server.basemap

import com.histmersion.core.*
import com.histmersion.server.{Deps, Maintenance}
import com.histmersion.server.plugins.Plugins

import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime

/** The served terrain styles: where the file is looked for, the built-in fallback, a file picked up,
  * changed, broken and removed while the server runs (no restart), a config change that names another
  * file or language, the check of a file, and the stylesheet tokens the built-in styles name. No
  * database. */
class BasemapStylesSuite extends munit.FunSuite:

  /** A server's view: its config may change between reads (a reload), its config file is in `dir`. */
  private final class Fixture(dir: Path):
    // `var`: what a config reload would switch to
    var cfg: AppConfig = AppConfig.load()
    val deps  = new Deps(() => cfg, null, null, Maintenance(), () => ConfigFileInfo(Some(dir.resolve("histmersion.conf").toString)),
      () => Plugins.offline)
    val styles = BasemapStyles(deps)
    val file   = dir.resolve("basemap-style.conf")
    /** Write the style file with a modification time `bump` seconds on (two writes in one millisecond
      * must still look changed). */
    def write(text: String, bump: Int = 0): Unit =
      Files.writeString(file, text)
      Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + bump * 1000L))

  test("where the file is looked for: basemap.style (~, relative, absolute), else beside the config file, .conf before .json"):
    val dir  = Files.createTempDirectory("styles-where")
    val conf = Some(dir.resolve("histmersion.conf"))
    assertEquals(BasemapStyles.file("", conf, Some("/h")), (Some(dir.resolve("basemap-style.conf")), false))
    Files.writeString(dir.resolve("basemap-style.json"), "{}")
    assertEquals(BasemapStyles.file("", conf, Some("/h")), (Some(dir.resolve("basemap-style.json")), false))
    Files.writeString(dir.resolve("basemap-style.conf"), "")
    assertEquals(BasemapStyles.file("", conf, Some("/h")), (Some(dir.resolve("basemap-style.conf")), false))
    assertEquals(BasemapStyles.file("~/maps/s.conf", conf, Some("/h")), (Some(Path.of("/h/maps/s.conf")), true))
    assertEquals(BasemapStyles.file("mine.json", conf, Some("/h")), (Some(dir.resolve("mine.json")), true))
    assertEquals(BasemapStyles.file("/etc/s.conf", conf, None), (Some(Path.of("/etc/s.conf")), true))
    assertEquals(BasemapStyles.file("", None, None), (None, false))

  test("no file: the built-in styles; a file appears, changes, breaks and goes — each picked up at the next read"):
    val f = Fixture(Files.createTempDirectory("styles-hot"))
    val s0 = f.styles.current
    assertEquals(s0.styles.map(_.id), List("natural", "contour", "terrain", "terrain-vegetation", "relief", "parchment"))
    assertEquals((s0.source, s0.default, s0.language, s0.warnings), (TerrainStyles.BuiltIn, "natural", "en", Nil))
    assertEquals(s0.file, Some(f.file.toString))
    assertEquals(s0.checkSeconds, f.cfg.basemap.styleCheckSeconds)
    assert(s0.version.nonEmpty)
    assert(f.styles.current eq s0, "unchanged: the same answer, not parsed again")

    f.write("""styles = [ { id = "sepia", extends = "parchment", name = "Sepia", background = "#d8c8a8" } ]
              |default = "sepia"""".stripMargin)
    val s1 = f.styles.current
    assertEquals(s1.styles.last.id, "sepia")
    assertEquals(s1.default, "sepia")
    assert(s1.source.contains("basemap-style.conf over the built-in styles"), s1.source)
    assertNotEquals(s1.version, s0.version)

    f.write("""styles = [ { id = "sepia", extends = "parchment", name = "Sepia", background = "#d8c8a8", tone = "beige" } ]""", bump = 2)
    val s2 = f.styles.current
    assertEquals(s2.styles.map(_.id), s0.styles.map(_.id), "refused: the built-in styles, never a blank map")
    assertEquals(s2.source, TerrainStyles.BuiltIn)
    assert(s2.warnings.exists(_.contains("styles[0].tone: \"beige\" is not one of light, dark")), s2.warnings.toString)
    assertEquals(f.styles.warnings, s2.warnings, "health and about say it")

    Files.delete(f.file)
    val s3 = f.styles.current
    assertEquals((s3.source, s3.warnings), (TerrainStyles.BuiltIn, Nil))
    assertEquals(s3.version, s0.version, "the same styles: the same version")

  test("a config change applies at the next read: another file (basemap.style), another language; a named file that is missing is said"):
    val dir = Files.createTempDirectory("styles-cfg")
    val f   = Fixture(dir)
    val other = dir.resolve("other.json")
    Files.writeString(other, """{"default": "relief"}""")
    f.cfg = f.cfg.copy(basemap = f.cfg.basemap.copy(style = "other.json", labels = f.cfg.basemap.labels.copy(language = "de")))
    val s = f.styles.current
    assertEquals((s.default, s.language, s.file), ("relief", "de", Some(other.toString)))
    f.cfg = f.cfg.copy(basemap = f.cfg.basemap.copy(style = "gone.conf"))
    val g = f.styles.current
    assertEquals(g.source, TerrainStyles.BuiltIn)
    assert(g.warnings.exists(w => w.contains("basemap.style names") && w.contains("gone.conf")), g.warnings.toString)

  test("the file's language wins over basemap.labels.language; a style's own over both (in the page)"):
    val f = Fixture(Files.createTempDirectory("styles-lang"))
    f.write("""language = "local"
              |styles = [ { id = "terrain", extends = "terrain", language = "fr" } ]""".stripMargin)
    val s = f.styles.current
    assertEquals(s.language, "local")
    assertEquals(s.find("terrain").flatMap(_.language), Some("fr"))

  test("checking a file: valid (what it would show), invalid (its errors), unknown keys (warnings); the example is the built-in document"):
    val f = Fixture(Files.createTempDirectory("styles-check"))
    val ok = f.styles.check("""styles = [ { id = "x", extends = "terrain", name = "X", glow = 1 } ]""")
    assert(ok.ok, ok.toString)
    assertEquals(ok.styles.last, "x")
    assertEquals(ok.warnings, List("styles[0].glow: unknown key, ignored (known: background, description, extends, hidden, id, labels, language, layers, name, relief, tone)"))
    val bad = f.styles.check("""styles = [ { id = "x", extends = "terrain", name = "X", background = 7 } ]""")
    assert(!bad.ok)
    assertEquals(bad.errors.size, 1, bad.errors.toString)
    assert(bad.errors.head.startsWith("styles[0].background:"), bad.errors.toString)
    assert(f.styles.check("styles = [").errors.nonEmpty)
    assert(f.styles.example.contains("styles = ["))
    assert(f.styles.example.contains("# The terrain map's built-in styles"))
    assertEquals(f.styles.current.warnings, Nil, "a check never changes what is served")

  test("every stylesheet token the built-in styles name is defined in style.css"):
    val css = String(getClass.getClassLoader.getResourceAsStream("web/style.css").readAllBytes(), "UTF-8")
    val code = TerrainStyleFile.builtInText().linesIterator.map(_.takeWhile(_ != '#')).mkString("\n")   // not the comments
    val used = "var\\((--[a-zA-Z0-9-]+)\\)".r.findAllMatchIn(code).map(_.group(1)).toSet
    assert(used.size >= 8, used.toString)
    used.foreach(t => assert(css.contains(s"$t:"), s"$t is not defined in style.css"))

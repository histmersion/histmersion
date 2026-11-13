package com.histmersion
package cli

import com.histmersion.api.catalogue.Place

import java.nio.file.{Files, Path}
import com.histmersion.config.PlacesConfig

/** `histmersion places seed`'s file walk is a pure function of a directory, so it is tested as one. */
class PlacesCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private def tree(): Path =
    val root = Files.createTempDirectory("histmersion-places")
    List("01-natural-earth.json", "03-pleiades.json", "README.md", "build.py")
      .foreach(n => Files.writeString(root.resolve(n), "{}"))
    val pol = Files.createDirectories(root.resolve("polities"))
    List("eba.geojson", "lba.geojson", "macro_regions.geojson", "README.md")
      .foreach(n => Files.writeString(pol.resolve(n), "{}"))
    val _ = Files.createDirectories(pol.resolve("linked-places")) // nested: not walked
    root

  test("top-level files first, then subdirectories; only *.json / *.geojson"):
    val root = tree()
    assertEquals(
      PlacesCmd.seedFiles(root).map((f, k) => (root.relativize(f).toString, k)),
      List[(String, Option[Place.KindValue])](
        "01-natural-earth.json" -> None,
        "03-pleiades.json" -> None,
        "polities/eba.geojson" -> Some(Place.KindValue("polity")),
        "polities/lba.geojson" -> Some(Place.KindValue("polity")),
        "polities/macro_regions.geojson" -> Some(Place.KindValue("polity"))
      )
    )

  test("the fallback kind comes from the directory name, singularised"):
    val root = Files.createTempDirectory("histmersion-places-kinds")
    List("polities" -> "polity", "sites" -> "site", "regions" -> "region", "misc" -> null)
      .foreach { (dir, _) =>
        val d = Files.createDirectories(root.resolve(dir))
        Files.writeString(d.resolve("a.geojson"), "{}")
      }
    assertEquals(
      PlacesCmd.seedFiles(root).map((f, k) => (f.getParent.getFileName.toString, k)).toMap,
      Map[String, Option[Place.KindValue]](
        "polities" -> Some(Place.KindValue("polity")),
        "sites" -> Some(Place.KindValue("site")),
        "regions" -> Some(Place.KindValue("region")),
        "misc" -> None
      )
    )

  test("an empty directory yields nothing (the caller turns that into a usage error)"):
    assertEquals(PlacesCmd.seedFiles(Files.createTempDirectory("histmersion-places-empty")), Nil)

  test("the curated supplement (CSV under supplement/) is build input, never imported as a seed file"):
    val root = tree()
    val sup = Files.createDirectories(root.resolve("supplement"))
    List("names.csv", "places.csv").foreach(n => Files.writeString(sup.resolve(n), "id\n"))
    Files.writeString(root.resolve("05-curated.json"), "{}")
    val files = PlacesCmd.seedFiles(root).map((f, _) => root.relativize(f).toString)
    assert(files.contains("05-curated.json"), files)
    assert(!files.exists(_.startsWith("supplement")), files)

  // ── #987: names and places added by hand ─────────────────────────────────────────────────────

  private def parses(args: String*) = Parsing.parses(args*)

  test("places name add|rm|list and places add parse; a place needs its coordinates and a source"):
    assert(parses("places", "name", "add", "pleiades_678430", "Lake Kinneret", "--lang", "en"))
    assert(parses("places", "name", "add", "ne_city_1159149709", "Cracow"))
    assert(parses("places", "name", "rm", "pleiades_678430", "Lake Kinneret", "--lang", "en"))
    assert(parses("places", "name", "list"))
    assert(parses("places", "name", "list", "--place", "pleiades_678430", "--csv"))
    assert(!parses("places", "name", "add", "pleiades_678430"), "a name is required")
    assert(parses("places", "add", "Gornji Grad", "--lon", "15.98", "--lat", "45.81", "--source", "Wikidata Q1003020"))
    assert(
      parses(
        "places",
        "add",
        "Gornji Grad",
        "--kind",
        "region",
        "--lon",
        "15.98",
        "--lat",
        "45.81",
        "--source",
        "a map",
        "--id",
        "local_gornji",
        "--name",
        "Gornij",
        "--name",
        "Gornji",
        "--lang",
        "hr",
        "--from",
        "-1300",
        "--to",
        "-1100"
      )
    )
    assert(!parses("places", "add", "Gornji Grad", "--lon", "15.98", "--lat", "45.81"), "--source is required")
    assert(!parses("places", "add", "Gornji Grad", "--source", "x"), "--lon/--lat are required")

  test("#6560: a relative seed dir falls back to the install the executable sits in"):
    val install = Files.createTempDirectory("histmersion-install")
    val _ = Files.createDirectories(install.resolve("db/places"))
    val bin = Files.createDirectories(install.resolve("bin"))
    val exe = Files.writeString(bin.resolve("histmersion"), "")
    val elsewhere = Files.createTempDirectory("histmersion-cwd")
    val (d, tried) = PlacesCmd.resolveSeedDir(PlacesConfig.SeedDir("db/places"), elsewhere, Some(exe))
    assertEquals(d, install.resolve("db/places").toRealPath())
    assertEquals(tried.size, 2)

  test("#6560: the working directory wins when it holds the seed dir; absolute paths are kept"):
    val cwd = Files.createTempDirectory("histmersion-checkout")
    val _ = Files.createDirectories(cwd.resolve("db/places"))
    assertEquals(PlacesCmd.resolveSeedDir(PlacesConfig.SeedDir("db/places"), cwd, None)._1, cwd.resolve("db/places"))
    val abs = cwd.resolve("elsewhere").toAbsolutePath
    assertEquals(PlacesCmd.resolveSeedDir(PlacesConfig.SeedDir(abs.toString), Path.of("/"), None), (abs, List(abs)))

  test("#6560: nothing found — the first candidate, and every one tried for the message"):
    val cwd = Files.createTempDirectory("histmersion-empty")
    val (d, tried) = PlacesCmd.resolveSeedDir(PlacesConfig.SeedDir("db/places"), cwd, None)
    assertEquals(d, cwd.resolve("db/places"))
    assertEquals(tried, List(cwd.resolve("db/places")))

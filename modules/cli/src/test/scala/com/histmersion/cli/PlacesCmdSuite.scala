package com.histmersion.cli

import java.nio.file.{Files, Path}

/** `histmersion places seed`'s file walk is a pure function of a directory, so it is tested as one. */
class PlacesCmdSuite extends munit.FunSuite:

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
      List(
        "01-natural-earth.json"           -> None,
        "03-pleiades.json"                -> None,
        "polities/eba.geojson"            -> Some("polity"),
        "polities/lba.geojson"            -> Some("polity"),
        "polities/macro_regions.geojson"  -> Some("polity")))

  test("the fallback kind comes from the directory name, singularised"):
    val root = Files.createTempDirectory("histmersion-places-kinds")
    List("polities" -> "polity", "sites" -> "site", "regions" -> "region", "misc" -> null)
      .foreach { (dir, _) =>
        val d = Files.createDirectories(root.resolve(dir))
        Files.writeString(d.resolve("a.geojson"), "{}")
      }
    assertEquals(
      PlacesCmd.seedFiles(root).map((f, k) => (f.getParent.getFileName.toString, k)).toMap,
      Map("polities" -> Some("polity"), "sites" -> Some("site"), "regions" -> Some("region"),
        "misc" -> None))

  test("an empty directory yields nothing (the caller turns that into a usage error)"):
    assertEquals(PlacesCmd.seedFiles(Files.createTempDirectory("histmersion-places-empty")), Nil)

  test("the curated supplement (CSV under supplement/) is build input, never imported as a seed file"):
    val root = tree()
    val sup  = Files.createDirectories(root.resolve("supplement"))
    List("names.csv", "places.csv").foreach(n => Files.writeString(sup.resolve(n), "id\n"))
    Files.writeString(root.resolve("05-curated.json"), "{}")
    val files = PlacesCmd.seedFiles(root).map((f, _) => root.relativize(f).toString)
    assert(files.contains("05-curated.json"), files)
    assert(!files.exists(_.startsWith("supplement")), files)

  // ── #987: names and places added by hand ─────────────────────────────────────────────────────

  private val cmd = com.monovore.decline.Command("histmersion", "test")(PlacesCmd.command)
  private def parses(args: String*) = cmd.parse(args.toList, Map.empty).isRight

  test("places name add|rm|list and places add parse; a place needs its coordinates and a source"):
    assert(parses("places", "name", "add", "pleiades_678430", "Lake Kinneret", "--lang", "en"))
    assert(parses("places", "name", "add", "ne_city_1159149709", "Cracow"))
    assert(parses("places", "name", "rm", "pleiades_678430", "Lake Kinneret", "--lang", "en"))
    assert(parses("places", "name", "list"))
    assert(parses("places", "name", "list", "--place", "pleiades_678430", "--csv"))
    assert(!parses("places", "name", "add", "pleiades_678430"), "a name is required")
    assert(parses("places", "add", "Gornji Grad", "--lon", "15.98", "--lat", "45.81", "--source", "Wikidata Q1003020"))
    assert(parses("places", "add", "Gornji Grad", "--kind", "region", "--lon", "15.98", "--lat", "45.81",
      "--source", "a map", "--id", "local_gornji", "--name", "Gornij", "--name", "Gornji", "--lang", "hr",
      "--from", "-1300", "--to", "-1100"))
    assert(!parses("places", "add", "Gornji Grad", "--lon", "15.98", "--lat", "45.81"), "--source is required")
    assert(!parses("places", "add", "Gornji Grad", "--source", "x"), "--lon/--lat are required")

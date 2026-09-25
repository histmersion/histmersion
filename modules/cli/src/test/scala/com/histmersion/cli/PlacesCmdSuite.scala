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

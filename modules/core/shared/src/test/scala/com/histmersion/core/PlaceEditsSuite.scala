package com.histmersion.core

import io.circe.parser.parse

/** A person's gazetteer edits (#987): what a name, a language tag and a hand-added place may be. */
class PlaceEditsSuite extends munit.FunSuite:

  private def add(label: String = "Gornji Grad", kind: String = PlaceKind.Site, lon: Double = 15.98,
      lat: Double = 45.81, source: String = "Wikidata Q1003020") =
    PlaceAdd(label, kind, lon, lat, source)

  test("a name is trimmed and its inner whitespace collapsed; blank and overlong are refused"):
    assertEquals(PlaceEdits.name("  Lake   Kinneret "), Right("Lake Kinneret"))
    assert(PlaceEdits.name("   ").isLeft)
    assert(PlaceEdits.name("x" * (PlaceEdits.MaxName + 1)).isLeft)

  test("a language tag is lower-cased; absent or blank is unspecified; junk is refused"):
    assertEquals(PlaceEdits.lang(Some("EN")), Right("en"))
    assertEquals(PlaceEdits.lang(Some("sr-Latn")), Right("sr-latn"))
    assertEquals(PlaceEdits.lang(Some("grc")), Right("grc"))
    assertEquals(PlaceEdits.lang(None), Right(""))
    assertEquals(PlaceEdits.lang(Some("  ")), Right(""))
    assertEquals(PlaceEdits.lang(Some("English")).left.map(_.code), Left(Some(Write.CodeInvalid)))

  test("the default id is local_ + the folded label"):
    assertEquals(PlaceEdits.defaultId("Gornji Grad"), "local_gornji_grad")
    assertEquals(PlaceEdits.defaultId("Kraków – Wawel"), "local_krakow_wawel")
    assertEquals(PlaceEdits.defaultId("Ze'elim Gully"), "local_zeelim_gully")

  test("a hand-added place becomes a point import row with its label, names, period and source"):
    val in = add().copy(names = List("Gornij", " Gornji  Grad "), lang = Some("HR"), periodStart = Some(-1300), periodEnd = Some(-1100))
    val r  = PlaceEdits.toInput(in).fold(e => fail(e.message), identity)
    assertEquals(r.place.id, "local_gornji_grad")
    assertEquals(r.place.kind, PlaceKind.Site)
    assertEquals(r.place.period, Some(Period(-1300, -1100)))
    assertEquals(r.place.source, Some("Wikidata Q1003020"))
    assertEquals(r.place.geometry.flatMap(g => parse(g).toOption).flatMap(_.hcursor.downField("coordinates").as[List[Double]].toOption),
      Some(List(15.98, 45.81)))
    assertEquals(r.names, List(PlaceName("Gornji Grad"), PlaceName("Gornij", "hr"), PlaceName("Gornji Grad", "hr")))
    assertEquals(r.relations, Nil)

  test("an explicit id is kept; one end of a period is a single year"):
    val r = PlaceEdits.toInput(add().copy(id = Some("my.place-1"), periodEnd = Some(-700))).toOption.get
    assertEquals((r.place.id, r.place.period), ("my.place-1", Some(Period(-700, -700))))

  test("a hand-added place needs a source, a known kind, real coordinates, a usable id and an ordered period"):
    def refused(in: PlaceAdd, says: String) =
      val e = PlaceEdits.toInput(in).swap.toOption.getOrElse(fail(s"accepted: $in"))
      assertEquals(e.code, Some(Write.CodeInvalid))
      assert(e.message.contains(says), e.message)
    refused(add(source = "  "), "where the coordinates come from")
    refused(add(kind = "city"), "kind 'city'")
    refused(add(lon = 200), "longitude/latitude")
    refused(add(lat = -91), "longitude/latitude")
    refused(add(lat = Double.NaN), "longitude/latitude")
    refused(add(label = " "), "label is empty")
    refused(add(label = "Ἀθῆναι"), "gives no id")
    refused(add().copy(id = Some("Bad Id")), "is not a place id")
    refused(add().copy(periodStart = Some(-100), periodEnd = Some(-200)), "starts (-100) after it ends (-200)")
    refused(add().copy(lang = Some("??")), "language tag")

  test("the CSV export is the supplement's format, quoted where it must be"):
    val csv = PlaceEdits.csv(List(
      ManualPlaceName("ne_lake_1", "Sea of Galilee", "Lake Kinneret", "en"),
      ManualPlaceName("p2", "Tell, \"the\" mound", "Tall", "")))
    assertEquals(csv.linesIterator.toList, List(
      "place_id,name,lang,why",
      "ne_lake_1,Lake Kinneret,en,added by hand to Sea of Galilee",
      "p2,Tall,,\"added by hand to Tell, \"\"the\"\" mound\""))
    assertEquals(PlaceEdits.csv(Nil), "place_id,name,lang,why\n")

  test("the Markdown of an edit says what changed and that it survives a re-seed"):
    val added = PlaceNameChanged("ne_lake_1", "Sea of Galilee", "Lake Kinneret", "en", added = true, changed = true)
    assertEquals(Markdown.placeNameChanged(added),
      "added the name **Lake Kinneret** (en) to **Sea of Galilee** `ne_lake_1` — names added by hand survive every re-seed")
    assert(Markdown.placeNameChanged(added.copy(changed = false)).contains("was already a name added by hand"))
    assert(Markdown.placeNameChanged(added.copy(wasImported = true)).contains("(a name the seed gives)"))
    assert(Markdown.placeNameChanged(added.copy(added = false)).startsWith("removed the name **Lake Kinneret** (en) from"))
    assertEquals(Markdown.manualPlaceNames(Nil), "_No names added by hand._")
    assert(Markdown.manualPlaceNames(List(ManualPlaceName("p", "P", "N", ""))).contains("- **N** → P `p`"))

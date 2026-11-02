package com.histmersion.api

import com.histmersion.api.catalogue.places.PlacesMarkdown

import io.circe.parser.parse
import com.histmersion.values.PlaceKind

/** A person's gazetteer edits (#987): what a name, a language tag and a hand-added place may be. */
class PlaceEditsSuite extends munit.FunSuite:

  private def add(
      label: String = "Gornji Grad",
      kind: String = PlaceKind.Site,
      lon: Double = 15.98,
      lat: Double = 45.81,
      source: String = "Wikidata Q1003020"
  ) =
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
    val in = add().copy(
      names = List("Gornij", " Gornji  Grad "),
      lang = Some("HR"),
      periodStart = Some(-1300),
      periodEnd = Some(-1100)
    )
    val r = PlaceEdits.toInput(in).fold(e => fail(e.message), identity)
    assertEquals(r.place.id, "local_gornji_grad")
    assertEquals(r.place.kind, PlaceKind.Site)
    assertEquals(r.place.period, Some(Period(-1300, -1100)))
    assertEquals(r.place.source, Some("Wikidata Q1003020"))
    assertEquals(
      r.place.geometry
        .flatMap(g => parse(g).toOption)
        .flatMap(_.hcursor.downField("coordinates").as[List[Double]].toOption),
      Some(List(15.98, 45.81))
    )
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
    val csv = PlaceEdits.csv(
      List(
        ManualPlaceName("ne_lake_1", "Sea of Galilee", "Lake Kinneret", "en"),
        ManualPlaceName("p2", "Tell, \"the\" mound", "Tall", "")
      )
    )
    assertEquals(
      csv.linesIterator.toList,
      List(
        "place_id,name,lang,why",
        "ne_lake_1,Lake Kinneret,en,added by hand to Sea of Galilee",
        "p2,Tall,,\"added by hand to Tell, \"\"the\"\" mound\""
      )
    )
    assertEquals(PlaceEdits.csv(Nil), "place_id,name,lang,why\n")

  test("the Markdown of an edit says what changed and that it survives a re-seed"):
    val added = PlaceNameChanged("ne_lake_1", "Sea of Galilee", "Lake Kinneret", "en", added = true, changed = true)
    assertEquals(
      PlacesMarkdown.placeNameChanged(added),
      "added the name **Lake Kinneret** (en) to **Sea of Galilee** `ne_lake_1` — names added by hand survive every re-seed"
    )
    assert(PlacesMarkdown.placeNameChanged(added.copy(changed = false)).contains("was already a name added by hand"))
    assert(PlacesMarkdown.placeNameChanged(added.copy(wasImported = true)).contains("(a name the seed gives)"))
    assert(
      PlacesMarkdown
        .placeNameChanged(added.copy(added = false))
        .startsWith("removed the name **Lake Kinneret** (en) from")
    )
    assertEquals(PlacesMarkdown.manualPlaceNames(Nil), "_No names added by hand._")
    assert(PlacesMarkdown.manualPlaceNames(List(ManualPlaceName("p", "P", "N", ""))).contains("- **N** → P `p`"))

  // ── a person's kind and period for a place (V080) ───────────────────────────────────────────────

  test("an override: a kind (lower-cased), a period with one or both sides, a reason; or a reset alone"):
    assertEquals(
      PlaceOverrides.edit(PlaceOverrideSet(kind = Some(" Region "), reason = Some("  one of   14 "))),
      Right(PlaceOverrideEdit.Put(Some("region"), None, Some("one of 14")))
    )
    assertEquals(
      PlaceOverrides.edit(PlaceOverrideSet(periodStart = Some(-7))),
      Right(PlaceOverrideEdit.Put(None, Some((Some(-7L), None)), None))
    )
    assertEquals(
      PlaceOverrides.edit(PlaceOverrideSet(kind = Some("polity"), periodStart = Some(-27), periodEnd = Some(410))),
      Right(PlaceOverrideEdit.Put(Some("polity"), Some((Some(-27L), Some(410L))), None))
    )
    assertEquals(PlaceOverrides.edit(PlaceOverrideSet(reset = true)), Right(PlaceOverrideEdit.Reset))

  test("an override is refused when it says nothing, names no kind, runs backwards or mixes a reset in"):
    def refused(in: PlaceOverrideSet) = PlaceOverrides.edit(in).left.map(_.code)
    assertEquals(refused(PlaceOverrideSet()), Left(Some(Write.CodeInvalid)))
    assertEquals(refused(PlaceOverrideSet(kind = Some("  "), reason = Some("why"))), Left(Some(Write.CodeInvalid)))
    assertEquals(refused(PlaceOverrideSet(kind = Some("town"))), Left(Some(Write.CodeInvalid)))
    assertEquals(
      refused(PlaceOverrideSet(periodStart = Some(10), periodEnd = Some(-10))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(refused(PlaceOverrideSet(kind = Some("region"), reset = true)), Left(Some(Write.CodeInvalid)))
    assertEquals(
      refused(PlaceOverrideSet(kind = Some("region"), reason = Some("x" * (PlaceOverrides.MaxReason + 1)))),
      Left(Some(Write.CodeInvalid))
    )

  test("an override body: unknown keys refused, absent ones defaulted"):
    assert(parse("""{"kind":"region","why":"x"}""").flatMap(_.as[PlaceOverrideSet]).isLeft)
    assertEquals(parse("""{"reset":true}""").flatMap(_.as[PlaceOverrideSet]), Right(PlaceOverrideSet(reset = true)))

  test("a period as a reader sees it, and the Markdown of an override"):
    assertEquals(PlaceOverrides.periodText(Some(-27), Some(410)), "27 BCE–410 CE")
    assertEquals(PlaceOverrides.periodText(Some(-7), None), "from 7 BCE")
    assertEquals(PlaceOverrides.periodText(None, Some(410)), "until 410 CE")
    assertEquals(PlaceOverrides.periodText(None, None), "undated")
    val set = PlaceOverrideChanged(
      "pleiades_886355306",
      "Regio XII (Piscina Publica)",
      reset = false,
      "site",
      "region",
      "undated",
      "undated",
      59,
      0,
      0
    )
    assertEquals(
      PlacesMarkdown.placeOverrideChanged(set),
      "set **Regio XII (Piscina Publica)** `pleiades_886355306`: kind site → region, period undated — kept by every " +
        "re-seed and re-import\n\nfindings matched again: 59 → 0"
    )
    assert(
      PlacesMarkdown.placeOverrideChanged(set.copy(unlocated = 2)).endsWith("; **2** of them now have no place at all")
    )
    assert(
      PlacesMarkdown
        .placeOverrideChanged(set.copy(reset = true, kindBefore = "region", kindAfter = "site"))
        .startsWith(
          "reset **Regio XII (Piscina Publica)** `pleiades_886355306` to what its source says: kind region → site"
        )
    )
    assertEquals(PlacesMarkdown.placeOverrides(Nil), "_No place has a kind, period or point set by hand._")
    val row = PlaceOverride(
      "pleiades_886355306",
      "Regio XII (Piscina Publica)",
      Some("region"),
      Some(-7),
      None,
      periodSet = true,
      Some("one of the 14 regiones"),
      "site",
      None,
      None,
      "2026-10-09T16:00:00Z"
    )
    assertEquals(
      PlacesMarkdown.placeOverrides(List(row)),
      "1 place(s) with a kind, period or point set by hand:\n\n- **Regio XII (Piscina Publica)** `pleiades_886355306`: " +
        "region, from 7 BCE _(the source says: site, undated)_ — one of the 14 regiones"
    )

  // ── a person's point for a place (V081) ─────────────────────────────────────────────────────────

  test("a point override: both coordinates, in range, with the reason that names its source"):
    assertEquals(
      PlaceOverrides.edit(
        PlaceOverrideSet(pointLat = Some(38.5), pointLon = Some(57.5), reason = Some("Pleiades reprPoint"))
      ),
      Right(PlaceOverrideEdit.Put(None, None, Some("Pleiades reprPoint"), Some(GeoPoint(57.5, 38.5))))
    )
    assertEquals(
      PlaceOverrides.edit(
        PlaceOverrideSet(
          kind = Some("region"),
          pointLat = Some(-16.1),
          pointLon = Some(31.4),
          reason = Some("Wikidata P625")
        )
      ),
      Right(PlaceOverrideEdit.Put(Some("region"), None, Some("Wikidata P625"), Some(GeoPoint(31.4, -16.1))))
    )
    def refused(in: PlaceOverrideSet) = PlaceOverrides.edit(in).left.map(_.code)
    assertEquals(
      refused(PlaceOverrideSet(pointLat = Some(38.5), pointLon = Some(57.5))),
      Left(Some(Write.CodeInvalid)),
      "no reason: a point must name where it comes from"
    )
    assertEquals(refused(PlaceOverrideSet(pointLat = Some(38.5), reason = Some("x"))), Left(Some(Write.CodeInvalid)))
    assertEquals(
      refused(PlaceOverrideSet(pointLat = Some(91), pointLon = Some(0), reason = Some("x"))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(PlaceOverrideSet(pointLat = Some(0), pointLon = Some(-181), reason = Some("x"))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(PlaceOverrideSet(pointLat = Some(Double.NaN), pointLon = Some(0), reason = Some("x"))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(PlaceOverrideSet(pointLat = Some(1), pointLon = Some(1), reason = Some("x"), reset = true)),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      parse("""{"pointLat":38.5,"pointLon":57.5,"reason":"r"}""").flatMap(_.as[PlaceOverrideSet]),
      Right(PlaceOverrideSet(reason = Some("r"), pointLat = Some(38.5), pointLon = Some(57.5)))
    )

  test("--point is latitude,longitude"):
    assertEquals(PlaceOverrides.parsePoint("38.5,57.5"), Right((38.5, 57.5)))
    assertEquals(PlaceOverrides.parsePoint(" -16.10108 , 31.45915 "), Right((-16.10108, 31.45915)))
    assert(PlaceOverrides.parsePoint("38.5").isLeft)
    assert(PlaceOverrides.parsePoint("38.5,57.5,1").isLeft)
    assert(PlaceOverrides.parsePoint("north,east").isLeft)
    assert(PlaceOverrides.parsePoint("95,10").isLeft)
    assert(PlaceOverrides.parsePoint("10,190").isLeft)

  test("a point as a reader sees it, and in the Markdown of a set and of the overrides"):
    assertEquals(PlaceOverrides.pointText(38.5, 57.5), "38.5000 N 57.5000 E")
    assertEquals(PlaceOverrides.pointText(-16.10108, -31.45915), "16.1011 S 31.4592 W")
    val moved = PlaceOverrideChanged(
      "pleiades_952097",
      "Parthyene/Parthia",
      reset = false,
      "region",
      "region",
      "900 BCE–300 CE",
      "900 BCE–300 CE",
      0,
      0,
      0,
      Some(33.09361),
      Some(44.58083),
      Some(38.5),
      Some(57.5)
    )
    val md = PlacesMarkdown.placeOverrideChanged(moved)
    assert(
      md.startsWith(
        "set **Parthyene/Parthia** `pleiades_952097`: kind region, period 900 BCE–300 CE, " +
          "point 33.0936 N 44.5808 E → 38.5000 N 57.5000 E ("
      ),
      md
    )
    assert(md.contains(" km) — kept by every re-seed and re-import"), md)
    val km = md.substring(md.indexOf("E (") + 3, md.indexOf(" km)")).toInt
    assert(km > 1200 && km < 1400, s"Ctesiphon to the grid square is about 1 300 km, not $km")
    assert(
      !PlacesMarkdown
        .placeOverrideChanged(moved.copy(latAfter = Some(33.09361), lonAfter = Some(44.58083)))
        .contains("→ 33"),
      "an unmoved point is stated once"
    )
    val row = PlaceOverride(
      "pleiades_952097",
      "Parthyene/Parthia",
      None,
      None,
      None,
      periodSet = false,
      Some("Pleiades reprPoint"),
      "region",
      Some(-900),
      Some(300),
      "2026-10-10T12:00:00Z",
      Some(38.5),
      Some(57.5),
      Some(33.09361),
      Some(44.58083)
    )
    assertEquals(
      PlacesMarkdown.placeOverrides(List(row)),
      "1 place(s) with a kind, period or point set by hand:\n\n- **Parthyene/Parthia** `pleiades_952097`: " +
        "point 38.5000 N 57.5000 E _(the source says: region, 900 BCE–300 CE, point 33.0936 N 44.5808 E)_ — Pleiades reprPoint"
    )

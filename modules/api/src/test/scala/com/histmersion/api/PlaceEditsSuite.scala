package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.PlacesMarkdown

import io.circe.parser.parse
import com.histmersion.api.catalogue.{ManualPlaceName, Period}
import com.histmersion.api.catalogue.Place

/** A person's gazetteer edits (#987): what a name, a language tag and a hand-added place may be. */
class PlaceEditsSuite extends munit.FunSuite:

  private def add(
      label: String = "Gornji Grad",
      kind: String = Place.Kind.Site,
      lon: Double = 15.98,
      lat: Double = 45.81,
      source: String = "Wikidata Q1003020"
  ) =
    com.histmersion.api.catalogue.Place.Add(label, kind, lon, lat, source)

  test("a name is trimmed and its inner whitespace collapsed; blank and overlong are refused"):
    assertEquals(com.histmersion.api.catalogue.Place.Edits.name(com.histmersion.api.catalogue.Place.Name.Value("  Lake   Kinneret ")), Right("Lake Kinneret"))
    assert(com.histmersion.api.catalogue.Place.Edits.name(com.histmersion.api.catalogue.Place.Name.Value("   ")).isLeft)
    assert(
      com.histmersion.api.catalogue.Place.Edits
        .name("x" * (com.histmersion.api.catalogue.Place.Edits.MaxName + 1))
        .isLeft
    )

  test("a language tag is lower-cased; absent or blank is unspecified; junk is refused"):
    assertEquals(com.histmersion.api.catalogue.Place.Edits.lang(Some(com.histmersion.api.catalogue.Place.Name.Lang("EN"))), Right("en"))
    assertEquals(com.histmersion.api.catalogue.Place.Edits.lang(Some(com.histmersion.api.catalogue.Place.Name.Lang("sr-Latn"))), Right("sr-latn"))
    assertEquals(com.histmersion.api.catalogue.Place.Edits.lang(Some(com.histmersion.api.catalogue.Place.Name.Lang("grc"))), Right("grc"))
    assertEquals(com.histmersion.api.catalogue.Place.Edits.lang(None), Right(""))
    assertEquals(com.histmersion.api.catalogue.Place.Edits.lang(Some(com.histmersion.api.catalogue.Place.Name.Lang("  "))), Right(""))
    assertEquals(
      com.histmersion.api.catalogue.Place.Edits.lang(Some(com.histmersion.api.catalogue.Place.Name.Lang("English"))).left.map(_.code),
      Left(Some(Write.CodeInvalid))
    )

  test("the default id is local_ + the folded label"):
    assertEquals(com.histmersion.api.catalogue.Place.Edits.defaultId(com.histmersion.api.catalogue.Place.Label("Gornji Grad")), "local_gornji_grad")
    assertEquals(com.histmersion.api.catalogue.Place.Edits.defaultId(com.histmersion.api.catalogue.Place.Label("Kraków – Wawel")), "local_krakow_wawel")
    assertEquals(com.histmersion.api.catalogue.Place.Edits.defaultId("Ze'elim Gully"), "local_zeelim_gully")

  test("a hand-added place becomes a point import row with its label, names, period and source"):
    val in = add().modify(
      _.names).setTo(List("Gornij", " Gornji  Grad ")).modify(_.lang).setTo(Some("HR")).modify(_.periodStart).setTo((Some(-1300): _root_.scala.Option[_root_.scala.Long])).modify(_.periodEnd).setTo((Some(-1100): _root_.scala.Option[_root_.scala.Long])
    )
    val r = com.histmersion.api.catalogue.Place.Edits.toInput(in).fold(e => fail(e.message), identity)
    assertEquals(r.place.id, com.histmersion.api.catalogue.Place.Id("local_gornji_grad"))
    assertEquals(r.place.kind, Place.Kind.Site)
    assertEquals(r.place.period, Some(Period(com.histmersion.api.catalogue.Year(-1300), com.histmersion.api.catalogue.Year(-1100))))
    assertEquals(r.place.source, Some("Wikidata Q1003020"))
    assertEquals(
      r.place.geometry
        .flatMap(g => parse(g).toOption)
        .flatMap(_.hcursor.downField("coordinates").as[List[Double]].toOption),
      Some(List(15.98, 45.81))
    )
    assertEquals(
      r.names,
      List(
        com.histmersion.api.catalogue.Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Gornji Grad")),
        com.histmersion.api.catalogue.Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Gornij"), com.histmersion.api.catalogue.Place.Name.Lang("hr")),
        com.histmersion.api.catalogue.Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Gornji Grad"), com.histmersion.api.catalogue.Place.Name.Lang("hr"))
      )
    )
    assertEquals(r.relations, Nil)

  test("an explicit id is kept; one end of a period is a single year"):
    val r = com.histmersion.api.catalogue.Place.Edits
      .toInput(add().modify(_.id).setTo(Some(com.histmersion.api.catalogue.Place.Id("my.place-1"))).modify(_.periodEnd).setTo((Some(-700): _root_.scala.Option[_root_.scala.Long])))
      .toOption
      .get
    assertEquals((r.place.id, r.place.period), (com.histmersion.api.catalogue.Place.Id("my.place-1"), Some(Period(com.histmersion.api.catalogue.Year(-700), com.histmersion.api.catalogue.Year(-700)))))

  test("a hand-added place needs a source, a known kind, real coordinates, a usable id and an ordered period"):
    def refused(in: com.histmersion.api.catalogue.Place.Add, says: String) =
      val e = com.histmersion.api.catalogue.Place.Edits.toInput(in).swap.toOption.getOrElse(fail(s"accepted: $in"))
      assertEquals(e.code, Some(Write.CodeInvalid))
      assert(e.message.contains(says), e.message)
    refused(add(source = "  "), "where the coordinates come from")
    refused(add(kind = "city"), "kind 'city'")
    refused(add(lon = 200), "longitude/latitude")
    refused(add(lat = -91), "longitude/latitude")
    refused(add(lat = Double.NaN), "longitude/latitude")
    refused(add(label = " "), "label is empty")
    refused(add(label = "Ἀθῆναι"), "gives no id")
    refused(add().modify(_.id).setTo(Some(com.histmersion.api.catalogue.Place.Id("Bad Id"))), "is not a place id")
    refused(add().modify(_.periodStart).setTo((Some(-100): _root_.scala.Option[_root_.scala.Long])).modify(_.periodEnd).setTo((Some(-200): _root_.scala.Option[_root_.scala.Long])), "starts (-100) after it ends (-200)")
    refused(add().modify(_.lang).setTo(Some("??")), "language tag")

  test("the CSV export is the supplement's format, quoted where it must be"):
    val csv = com.histmersion.api.catalogue.Place.Edits.csv(
      List(
        ManualPlaceName(com.histmersion.api.catalogue.Place.Id("ne_lake_1"), com.histmersion.api.catalogue.ManualPlaceName.Label("Sea of Galilee"), com.histmersion.api.catalogue.ManualPlaceName.Name("Lake Kinneret"), com.histmersion.api.catalogue.ManualPlaceName.Lang("en")),
        ManualPlaceName(com.histmersion.api.catalogue.Place.Id("p2"), com.histmersion.api.catalogue.ManualPlaceName.Label("Tell, \"the\" mound"), com.histmersion.api.catalogue.ManualPlaceName.Name("Tall"), com.histmersion.api.catalogue.ManualPlaceName.Lang(""))
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
    assertEquals(com.histmersion.api.catalogue.Place.Edits.csv(Nil), "place_id,name,lang,why\n")

  test("the Markdown of an edit says what changed and that it survives a re-seed"):
    val added = com.histmersion.api.catalogue.Place.NameChanged(
      com.histmersion.api.catalogue.Place.Id("ne_lake_1"),
      com.histmersion.api.catalogue.Place.NameChanged.Label("Sea of Galilee"),
      com.histmersion.api.catalogue.Place.NameChanged.Name("Lake Kinneret"),
      com.histmersion.api.catalogue.Place.NameChanged.Lang("en"),
      added = com.histmersion.api.catalogue.Place.NameChanged.Added(true),
      changed = com.histmersion.api.catalogue.Place.NameChanged.Changed(true)
    )
    assertEquals(
      PlacesMarkdown.placeNameChanged(added),
      "added the name **Lake Kinneret** (en) to **Sea of Galilee** `ne_lake_1` — names added by hand survive every re-seed"
    )
    assert(PlacesMarkdown.placeNameChanged(added.modify(_.changed).setTo(false)).contains("was already a name added by hand"))
    assert(PlacesMarkdown.placeNameChanged(added.modify(_.wasImported).setTo(true)).contains("(a name the seed gives)"))
    assert(
      PlacesMarkdown
        .placeNameChanged(added.modify(_.added).setTo(false))
        .startsWith("removed the name **Lake Kinneret** (en) from")
    )
    assertEquals(PlacesMarkdown.manualPlaceNames(Nil), "_No names added by hand._")
    assert(PlacesMarkdown.manualPlaceNames(List(ManualPlaceName(com.histmersion.api.catalogue.Place.Id("p"), com.histmersion.api.catalogue.ManualPlaceName.Label("P"), com.histmersion.api.catalogue.ManualPlaceName.Name("N"), com.histmersion.api.catalogue.ManualPlaceName.Lang("")))).contains("- **N** → P `p`"))

  // ── a person's kind and period for a place (V080) ───────────────────────────────────────────────

  test("an override: a kind (lower-cased), a period with one or both sides, a reason; or a reset alone"):
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides.edit(
        com.histmersion.api.catalogue.Place.OverrideSet(kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind(" Region ")), reason = Some("  one of   14 "))
      ),
      Right(com.histmersion.api.catalogue.Place.OverrideEdit.Put(Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Kind("region")), None, Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Reason("one of 14"))))
    )
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides
        .edit(com.histmersion.api.catalogue.Place.OverrideSet(periodStart = Some(com.histmersion.api.catalogue.Year(-7)))),
      Right(com.histmersion.api.catalogue.Place.OverrideEdit.Put(None, Some((Some(com.histmersion.api.catalogue.Year(-7L)), None)), None))
    )
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides.edit(
        com.histmersion.api.catalogue.Place
          .OverrideSet(kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("polity")), periodStart = Some(com.histmersion.api.catalogue.Year(-27)), periodEnd = Some(com.histmersion.api.catalogue.Year(410)))
      ),
      Right(com.histmersion.api.catalogue.Place.OverrideEdit.Put(Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Kind("polity")), Some((Some(com.histmersion.api.catalogue.Year(-27L)), Some(com.histmersion.api.catalogue.Year(410L)))), None))
    )
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides.edit(com.histmersion.api.catalogue.Place.OverrideSet(reset = com.histmersion.api.catalogue.Place.OverrideSet.Reset(true))),
      Right(com.histmersion.api.catalogue.Place.OverrideEdit.Reset)
    )

  test("an override is refused when it says nothing, names no kind, runs backwards or mixes a reset in"):
    def refused(in: com.histmersion.api.catalogue.Place.OverrideSet) =
      com.histmersion.api.catalogue.Place.Overrides.edit(in).left.map(_.code)
    assertEquals(refused(com.histmersion.api.catalogue.Place.OverrideSet()), Left(Some(Write.CodeInvalid)))
    assertEquals(
      refused(com.histmersion.api.catalogue.Place.OverrideSet(kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("  ")), reason = Some("why"))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(com.histmersion.api.catalogue.Place.OverrideSet(kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("town")))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(com.histmersion.api.catalogue.Place.OverrideSet(periodStart = Some(com.histmersion.api.catalogue.Year(10)), periodEnd = Some(com.histmersion.api.catalogue.Year(-10)))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(com.histmersion.api.catalogue.Place.OverrideSet(kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("region")), reset = true)),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        com.histmersion.api.catalogue.Place.OverrideSet(
          kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("region")),
          reason = Some("x" * (com.histmersion.api.catalogue.Place.Overrides.MaxReason + 1))
        )
      ),
      Left(Some(Write.CodeInvalid))
    )

  test("an override body: unknown keys refused, absent ones defaulted"):
    assert(
      parse("""{"kind":"region","why":"x"}""").flatMap(_.as[com.histmersion.api.catalogue.Place.OverrideSet]).isLeft
    )
    assertEquals(
      parse("""{"reset":true}""").flatMap(_.as[com.histmersion.api.catalogue.Place.OverrideSet]),
      Right(com.histmersion.api.catalogue.Place.OverrideSet(reset = com.histmersion.api.catalogue.Place.OverrideSet.Reset(true)))
    )

  test("a period as a reader sees it, and the Markdown of an override"):
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.periodText(Some(com.histmersion.api.catalogue.Year(-27)), Some(com.histmersion.api.catalogue.Year(410))), "27 BCE–410 CE")
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.periodText(Some(com.histmersion.api.catalogue.Year(-7)), None), "from 7 BCE")
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.periodText(None, Some(com.histmersion.api.catalogue.Year(410))), "until 410 CE")
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.periodText(None, None), "undated")
    val set = com.histmersion.api.catalogue.Place.OverrideChanged(
      com.histmersion.api.catalogue.Place.Id("pleiades_886355306"),
      com.histmersion.api.catalogue.Place.OverrideChanged.Label("Regio XII (Piscina Publica)"),
      reset = com.histmersion.api.catalogue.Place.OverrideChanged.Reset(false),
      com.histmersion.api.catalogue.Place.OverrideChanged.KindBefore("site"),
      com.histmersion.api.catalogue.Place.OverrideChanged.KindAfter("region"),
      com.histmersion.api.catalogue.Place.OverrideChanged.PeriodBefore("undated"),
      com.histmersion.api.catalogue.Place.OverrideChanged.PeriodAfter("undated"),
      com.histmersion.api.catalogue.Place.OverrideChanged.FindingsBefore(59),
      com.histmersion.api.catalogue.Place.OverrideChanged.FindingsAfter(0),
      com.histmersion.api.catalogue.Place.OverrideChanged.Unlocated(0)
    )
    assertEquals(
      PlacesMarkdown.placeOverrideChanged(set),
      "set **Regio XII (Piscina Publica)** `pleiades_886355306`: kind site → region, period undated — kept by every " +
        "re-seed and re-import\n\nfindings matched again: 59 → 0"
    )
    assert(
      PlacesMarkdown.placeOverrideChanged(set.modify(_.unlocated).setTo(2)).endsWith("; **2** of them now have no place at all")
    )
    assert(
      PlacesMarkdown
        .placeOverrideChanged(set.modify(_.reset).setTo(true).modify(_.kindBefore).setTo("region").modify(_.kindAfter).setTo("site"))
        .startsWith(
          "reset **Regio XII (Piscina Publica)** `pleiades_886355306` to what its source says: kind region → site"
        )
    )
    assertEquals(PlacesMarkdown.placeOverrides(Nil), "_No place has a kind, period or point set by hand._")
    val row = com.histmersion.api.catalogue.Place.Override(
      com.histmersion.api.catalogue.Place.Id("pleiades_886355306"),
      com.histmersion.api.catalogue.Place.Override.Label("Regio XII (Piscina Publica)"),
      Some(com.histmersion.api.catalogue.Place.Override.Kind("region")),
      Some(com.histmersion.api.catalogue.Year(-7)),
      None,
      periodSet = com.histmersion.api.catalogue.Place.Override.PeriodSet(true),
      Some(com.histmersion.api.catalogue.Place.Override.Reason("one of the 14 regiones")),
      com.histmersion.api.catalogue.Place.Override.SourceKind("site"),
      None,
      None,
      com.histmersion.api.catalogue.Place.Override.UpdatedAt("2026-10-09T16:00:00Z")
    )
    assertEquals(
      PlacesMarkdown.placeOverrides(List(row)),
      "1 place(s) with a kind, period or point set by hand:\n\n- **Regio XII (Piscina Publica)** `pleiades_886355306`: " +
        "region, from 7 BCE _(the source says: site, undated)_ — one of the 14 regiones"
    )

  // ── a person's point for a place (V081) ─────────────────────────────────────────────────────────

  test("a point override: both coordinates, in range, with the reason that names its source"):
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides.edit(
        com.histmersion.api.catalogue.Place
          .OverrideSet(pointLat = Some(38.5), pointLon = Some(57.5), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("Pleiades reprPoint")))
      ),
      Right(
        com.histmersion.api.catalogue.Place.OverrideEdit
          .Put(None, None, Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Reason("Pleiades reprPoint")), Some(GeoPoint(57.5, 38.5)))
      )
    )
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides.edit(
        com.histmersion.api.catalogue.Place.OverrideSet(
          kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("region")),
          pointLat = Some(-16.1),
          pointLon = Some(31.4),
          reason = Some("Wikidata P625")
        )
      ),
      Right(
        com.histmersion.api.catalogue.Place.OverrideEdit
          .Put(Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Kind("region")), None, Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Reason("Wikidata P625")), Some(GeoPoint(31.4, -16.1)))
      )
    )
    def refused(in: com.histmersion.api.catalogue.Place.OverrideSet) =
      com.histmersion.api.catalogue.Place.Overrides.edit(in).left.map(_.code)
    assertEquals(
      refused(com.histmersion.api.catalogue.Place.OverrideSet(pointLat = Some(38.5), pointLon = Some(57.5))),
      Left(Some(Write.CodeInvalid)),
      "no reason: a point must name where it comes from"
    )
    assertEquals(
      refused(com.histmersion.api.catalogue.Place.OverrideSet(pointLat = Some(38.5), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("x")))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        com.histmersion.api.catalogue.Place.OverrideSet(pointLat = Some(91), pointLon = Some(0), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("x")))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        com.histmersion.api.catalogue.Place.OverrideSet(pointLat = Some(0), pointLon = Some(-181), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("x")))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        com.histmersion.api.catalogue.Place
          .OverrideSet(pointLat = Some(Double.NaN), pointLon = Some(0), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("x")))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        com.histmersion.api.catalogue.Place
          .OverrideSet(pointLat = Some(com.histmersion.api.catalogue.Latitude(1)), pointLon = Some(com.histmersion.api.catalogue.Longitude(1)), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("x")), reset = com.histmersion.api.catalogue.Place.OverrideSet.Reset(true))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      parse("""{"pointLat":38.5,"pointLon":57.5,"reason":"r"}""").flatMap(
        _.as[com.histmersion.api.catalogue.Place.OverrideSet]
      ),
      Right(
        com.histmersion.api.catalogue.Place
          .OverrideSet(reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("r")), pointLat = Some(38.5), pointLon = Some(57.5))
      )
    )

  test("--point is latitude,longitude"):
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText("38.5,57.5")), Right((38.5, 57.5)))
    assertEquals(
      com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText(" -16.10108 , 31.45915 ")),
      Right((-16.10108, 31.45915))
    )
    assert(com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText("38.5")).isLeft)
    assert(com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText("38.5,57.5,1")).isLeft)
    assert(com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText("north,east")).isLeft)
    assert(com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText("95,10")).isLeft)
    assert(com.histmersion.api.catalogue.Place.Overrides.parsePoint(com.histmersion.api.catalogue.Place.Overrides.PointText("10,190")).isLeft)

  test("a point as a reader sees it, and in the Markdown of a set and of the overrides"):
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.pointText(38.5, 57.5), "38.5000 N 57.5000 E")
    assertEquals(com.histmersion.api.catalogue.Place.Overrides.pointText(-16.10108, -31.45915), "16.1011 S 31.4592 W")
    val moved = com.histmersion.api.catalogue.Place.OverrideChanged(
      com.histmersion.api.catalogue.Place.Id("pleiades_952097"),
      com.histmersion.api.catalogue.Place.OverrideChanged.Label("Parthyene/Parthia"),
      reset = com.histmersion.api.catalogue.Place.OverrideChanged.Reset(false),
      com.histmersion.api.catalogue.Place.OverrideChanged.KindBefore("region"),
      com.histmersion.api.catalogue.Place.OverrideChanged.KindAfter("region"),
      com.histmersion.api.catalogue.Place.OverrideChanged.PeriodBefore("900 BCE–300 CE"),
      com.histmersion.api.catalogue.Place.OverrideChanged.PeriodAfter("900 BCE–300 CE"),
      com.histmersion.api.catalogue.Place.OverrideChanged.FindingsBefore(0),
      com.histmersion.api.catalogue.Place.OverrideChanged.FindingsAfter(0),
      com.histmersion.api.catalogue.Place.OverrideChanged.Unlocated(0),
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
        .placeOverrideChanged(moved.modify(_.latAfter).setTo((Some(33.09361): _root_.scala.Option[_root_.scala.Double])).modify(_.lonAfter).setTo((Some(44.58083): _root_.scala.Option[_root_.scala.Double])))
        .contains("→ 33"),
      "an unmoved point is stated once"
    )
    val row = com.histmersion.api.catalogue.Place.Override(
      com.histmersion.api.catalogue.Place.Id("pleiades_952097"),
      com.histmersion.api.catalogue.Place.Override.Label("Parthyene/Parthia"),
      None,
      None,
      None,
      periodSet = com.histmersion.api.catalogue.Place.Override.PeriodSet(false),
      Some(com.histmersion.api.catalogue.Place.Override.Reason("Pleiades reprPoint")),
      com.histmersion.api.catalogue.Place.Override.SourceKind("region"),
      Some(com.histmersion.api.catalogue.Year(-900)),
      Some(com.histmersion.api.catalogue.Year(300)),
      com.histmersion.api.catalogue.Place.Override.UpdatedAt("2026-10-10T12:00:00Z"),
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

package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.PlacesMarkdown

import io.circe.parser.parse
import com.histmersion.api.catalogue.{Latitude, Longitude, ManualPlaceName, Period, Year}
import com.histmersion.api.catalogue.Place

/** A person's gazetteer edits (#987): what a name, a language tag and a hand-added place may be. */
class PlaceEditsSuite extends munit.FunSuite:

  private def add(
      label: Place.Add.Label = Place.Add.Label("Gornji Grad"),
      kind: Place.Add.Kind = Place.Add.Kind(Place.Kind.Site),
      lon: Longitude = Longitude(15.98),
      lat: Latitude = Latitude(45.81),
      source: Place.Add.Source = Place.Add.Source("Wikidata Q1003020")
  ) =
    Place.Add(label, kind, lon, lat, source)

  test("a name is trimmed and its inner whitespace collapsed; blank and overlong are refused"):
    assertEquals(Place.Edits.name(Place.Name.Value("  Lake   Kinneret ")), Right("Lake Kinneret"))
    assert(Place.Edits.name(Place.Name.Value("   ")).isLeft)
    assert(
      Place.Edits
        .name(Place.Name.Value("x" * (Place.Edits.MaxName + 1)))
        .isLeft
    )

  test("a language tag is lower-cased; absent or blank is unspecified; junk is refused"):
    assertEquals(Place.Edits.lang(Some(Place.Name.Lang("EN"))), Right("en"))
    assertEquals(Place.Edits.lang(Some(Place.Name.Lang("sr-Latn"))), Right("sr-latn"))
    assertEquals(Place.Edits.lang(Some(Place.Name.Lang("grc"))), Right("grc"))
    assertEquals(Place.Edits.lang(None), Right(""))
    assertEquals(Place.Edits.lang(Some(Place.Name.Lang("  "))), Right(""))
    assertEquals(
      Place.Edits.lang(Some(Place.Name.Lang("English"))).left.map(_.code),
      Left(Some(Write.CodeInvalid))
    )

  test("the default id is local_ + the folded label"):
    assertEquals(Place.Edits.defaultId(Place.Label("Gornji Grad")), "local_gornji_grad")
    assertEquals(Place.Edits.defaultId(Place.Label("Kraków – Wawel")), "local_krakow_wawel")
    assertEquals(Place.Edits.defaultId(Place.Label("Ze'elim Gully")), "local_zeelim_gully")

  test("a hand-added place becomes a point import row with its label, names, period and source"):
    val in = add().modify(
      _.names).setTo(List(Place.Add.Names("Gornij"), Place.Add.Names(" Gornji  Grad "))).modify(_.lang).setTo(Some(Place.Add.Lang("HR"))).modify(_.periodStart).setTo(Some(Year(-1300))).modify(_.periodEnd).setTo(Some(Year(-1100))
    )
    val r = Place.Edits.toInput(in).fold(e => fail(e.message.value), identity)
    assertEquals(r.place.id, Place.Id("local_gornji_grad"))
    assertEquals(r.place.kind, Place.Kind.Site)
    assertEquals(r.place.period, Some(Period(Year(-1300), Year(-1100))))
    assertEquals(r.place.source, Some("Wikidata Q1003020"))
    assertEquals(
      r.place.geometry
        .flatMap(g => parse(g.value).toOption)
        .flatMap(_.hcursor.downField("coordinates").as[List[Double]].toOption),
      Some(List(15.98, 45.81))
    )
    assertEquals(
      r.names,
      List(
        Place.Name(Place.Name.Value("Gornji Grad")),
        Place.Name(Place.Name.Value("Gornij"), Place.Name.Lang("hr")),
        Place.Name(Place.Name.Value("Gornji Grad"), Place.Name.Lang("hr"))
      )
    )
    assertEquals(r.relations, Nil)

  test("an explicit id is kept; one end of a period is a single year"):
    val r = Place.Edits
      .toInput(add().modify(_.id).setTo(Some(Place.Id("my.place-1"))).modify(_.periodEnd).setTo(Some(Year(-700))))
      .toOption
      .get
    assertEquals((r.place.id, r.place.period), (Place.Id("my.place-1"), Some(Period(Year(-700), Year(-700)))))

  test("a hand-added place needs a source, a known kind, real coordinates, a usable id and an ordered period"):
    def refused(in: Place.Add, says: String) =
      val e = Place.Edits.toInput(in).swap.toOption.getOrElse(fail(s"accepted: $in"))
      assertEquals(e.code, Some(Write.CodeInvalid))
      assert(e.message.value.contains(says), e.message)
    refused(add(source = Place.Add.Source("  ")), "where the coordinates come from")
    refused(add(kind = Place.Add.Kind("city")), "kind 'city'")
    refused(add(lon = Longitude(200)), "longitude/latitude")
    refused(add(lat = Latitude(-91)), "longitude/latitude")
    refused(add(lat = Latitude(Double.NaN)), "longitude/latitude")
    refused(add(label = Place.Add.Label(" ")), "label is empty")
    refused(add(label = Place.Add.Label("Ἀθῆναι")), "gives no id")
    refused(add().modify(_.id).setTo(Some(Place.Id("Bad Id"))), "is not a place id")
    refused(add().modify(_.periodStart).setTo(Some(Year(-100))).modify(_.periodEnd).setTo(Some(Year(-200))), "starts (-100) after it ends (-200)")
    refused(add().modify(_.lang).setTo(Some(Place.Add.Lang("??"))), "language tag")

  test("the CSV export is the supplement's format, quoted where it must be"):
    val csv = Place.Edits.csv(
      List(
        ManualPlaceName(Place.Id("ne_lake_1"), ManualPlaceName.Label("Sea of Galilee"), ManualPlaceName.Name("Lake Kinneret"), ManualPlaceName.Lang("en")),
        ManualPlaceName(Place.Id("p2"), ManualPlaceName.Label("Tell, \"the\" mound"), ManualPlaceName.Name("Tall"), ManualPlaceName.Lang(""))
      )
    )
    assertEquals(
      csv.value.linesIterator.toList,
      List(
        "place_id,name,lang,why",
        "ne_lake_1,Lake Kinneret,en,added by hand to Sea of Galilee",
        "p2,Tall,,\"added by hand to Tell, \"\"the\"\" mound\""
      )
    )
    assertEquals(Place.Edits.csv(Nil), "place_id,name,lang,why\n")

  test("the Markdown of an edit says what changed and that it survives a re-seed"):
    val added = Place.NameChanged(
      Place.Id("ne_lake_1"),
      Place.NameChanged.Label("Sea of Galilee"),
      Place.NameChanged.Name("Lake Kinneret"),
      Place.NameChanged.Lang("en"),
      added = Place.NameChanged.Added(true),
      changed = Place.NameChanged.Changed(true)
    )
    assertEquals(
      PlacesMarkdown.placeNameChanged(added),
      "added the name **Lake Kinneret** (en) to **Sea of Galilee** `ne_lake_1` — names added by hand survive every re-seed"
    )
    assert(PlacesMarkdown.placeNameChanged(added.modify(_.changed).setTo(Place.NameChanged.Changed(false))).contains("was already a name added by hand"))
    assert(PlacesMarkdown.placeNameChanged(added.modify(_.wasImported).setTo(Place.NameChanged.WasImported(true))).contains("(a name the seed gives)"))
    assert(
      PlacesMarkdown
        .placeNameChanged(added.modify(_.added).setTo(Place.NameChanged.Added(false)))
        .startsWith("removed the name **Lake Kinneret** (en) from")
    )
    assertEquals(PlacesMarkdown.manualPlaceNames(Nil), "_No names added by hand._")
    assert(PlacesMarkdown.manualPlaceNames(List(ManualPlaceName(Place.Id("p"), ManualPlaceName.Label("P"), ManualPlaceName.Name("N"), ManualPlaceName.Lang("")))).contains("- **N** → P `p`"))

  // ── a person's kind and period for a place (V080) ───────────────────────────────────────────────

  test("an override: a kind (lower-cased), a period with one or both sides, a reason; or a reset alone"):
    assertEquals(
      Place.Overrides.edit(
        Place.OverrideSet(kind = Some(Place.OverrideSet.Kind(" Region ")), reason = Some(Place.OverrideSet.Reason("  one of   14 ")))
      ),
      Right(Place.OverrideEdit.Put(Some(Place.OverrideEdit.Put.Kind("region")), None, Some(Place.OverrideEdit.Put.Reason("one of 14"))))
    )
    assertEquals(
      Place.Overrides
        .edit(Place.OverrideSet(periodStart = Some(Year(-7)))),
      Right(Place.OverrideEdit.Put(None, Some((Some(Year(-7L)), None)), None))
    )
    assertEquals(
      Place.Overrides.edit(
        Place
          .OverrideSet(kind = Some(Place.OverrideSet.Kind("polity")), periodStart = Some(Year(-27)), periodEnd = Some(Year(410)))
      ),
      Right(Place.OverrideEdit.Put(Some(Place.OverrideEdit.Put.Kind("polity")), Some((Some(Year(-27L)), Some(Year(410L)))), None))
    )
    assertEquals(
      Place.Overrides.edit(Place.OverrideSet(reset = Place.OverrideSet.Reset(true))),
      Right(Place.OverrideEdit.Reset)
    )

  test("an override is refused when it says nothing, names no kind, runs backwards or mixes a reset in"):
    def refused(in: Place.OverrideSet) =
      Place.Overrides.edit(in).left.map(_.code)
    assertEquals(refused(Place.OverrideSet()), Left(Some(Write.CodeInvalid)))
    assertEquals(
      refused(Place.OverrideSet(kind = Some(Place.OverrideSet.Kind("  ")), reason = Some(Place.OverrideSet.Reason("why")))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(Place.OverrideSet(kind = Some(Place.OverrideSet.Kind("town")))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(Place.OverrideSet(periodStart = Some(Year(10)), periodEnd = Some(Year(-10)))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(Place.OverrideSet(kind = Some(Place.OverrideSet.Kind("region")), reset = Place.OverrideSet.Reset(true))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        Place.OverrideSet(
          kind = Some(Place.OverrideSet.Kind("region")),
          reason = Some(Place.OverrideSet.Reason("x" * (Place.Overrides.MaxReason + 1)))
        )
      ),
      Left(Some(Write.CodeInvalid))
    )

  test("an override body: unknown keys refused, absent ones defaulted"):
    assert(
      parse("""{"kind":"region","why":"x"}""").flatMap(_.as[Place.OverrideSet]).isLeft
    )
    assertEquals(
      parse("""{"reset":true}""").flatMap(_.as[Place.OverrideSet]),
      Right(Place.OverrideSet(reset = Place.OverrideSet.Reset(true)))
    )

  test("a period as a reader sees it, and the Markdown of an override"):
    assertEquals(Place.Overrides.periodText(Some(Year(-27)), Some(Year(410))), "27 BCE–410 CE")
    assertEquals(Place.Overrides.periodText(Some(Year(-7)), None), "from 7 BCE")
    assertEquals(Place.Overrides.periodText(None, Some(Year(410))), "until 410 CE")
    assertEquals(Place.Overrides.periodText(None, None), "undated")
    val set = Place.OverrideChanged(
      Place.Id("pleiades_886355306"),
      Place.OverrideChanged.Label("Regio XII (Piscina Publica)"),
      reset = Place.OverrideChanged.Reset(false),
      Place.OverrideChanged.KindBefore("site"),
      Place.OverrideChanged.KindAfter("region"),
      Place.OverrideChanged.PeriodBefore("undated"),
      Place.OverrideChanged.PeriodAfter("undated"),
      Place.OverrideChanged.FindingsBefore(59),
      Place.OverrideChanged.FindingsAfter(0),
      Place.OverrideChanged.Unlocated(0)
    )
    assertEquals(
      PlacesMarkdown.placeOverrideChanged(set),
      "set **Regio XII (Piscina Publica)** `pleiades_886355306`: kind site → region, period undated — kept by every " +
        "re-seed and re-import\n\nfindings matched again: 59 → 0"
    )
    assert(
      PlacesMarkdown.placeOverrideChanged(set.modify(_.unlocated).setTo(Place.OverrideChanged.Unlocated(2))).endsWith("; **2** of them now have no place at all")
    )
    assert(
      PlacesMarkdown
        .placeOverrideChanged(set.modify(_.reset).setTo(Place.OverrideChanged.Reset(true)).modify(_.kindBefore).setTo(Place.OverrideChanged.KindBefore("region")).modify(_.kindAfter).setTo(Place.OverrideChanged.KindAfter("site")))
        .startsWith(
          "reset **Regio XII (Piscina Publica)** `pleiades_886355306` to what its source says: kind region → site"
        )
    )
    assertEquals(PlacesMarkdown.placeOverrides(Nil), "_No place has a kind, period or point set by hand._")
    val row = Place.Override(
      Place.Id("pleiades_886355306"),
      Place.Override.Label("Regio XII (Piscina Publica)"),
      Some(Place.Override.Kind("region")),
      Some(Year(-7)),
      None,
      periodSet = Place.Override.PeriodSet(true),
      Some(Place.Override.Reason("one of the 14 regiones")),
      Place.Override.SourceKind("site"),
      None,
      None,
      Place.Override.UpdatedAt("2026-10-09T16:00:00Z")
    )
    assertEquals(
      PlacesMarkdown.placeOverrides(List(row)),
      "1 place(s) with a kind, period or point set by hand:\n\n- **Regio XII (Piscina Publica)** `pleiades_886355306`: " +
        "region, from 7 BCE _(the source says: site, undated)_ — one of the 14 regiones"
    )

  // ── a person's point for a place (V081) ─────────────────────────────────────────────────────────

  test("a point override: both coordinates, in range, with the reason that names its source"):
    assertEquals(
      Place.Overrides.edit(
        Place
          .OverrideSet(pointLat = Some(Latitude(38.5)), pointLon = Some(Longitude(57.5)), reason = Some(Place.OverrideSet.Reason("Pleiades reprPoint")))
      ),
      Right(
        Place.OverrideEdit
          .Put(None, None, Some(Place.OverrideEdit.Put.Reason("Pleiades reprPoint")), Some(GeoPoint(Longitude(57.5), Latitude(38.5))))
      )
    )
    assertEquals(
      Place.Overrides.edit(
        Place.OverrideSet(
          kind = Some(Place.OverrideSet.Kind("region")),
          pointLat = Some(Latitude(-16.1)),
          pointLon = Some(Longitude(31.4)),
          reason = Some(Place.OverrideSet.Reason("Wikidata P625"))
        )
      ),
      Right(
        Place.OverrideEdit
          .Put(Some(Place.OverrideEdit.Put.Kind("region")), None, Some(Place.OverrideEdit.Put.Reason("Wikidata P625")), Some(GeoPoint(Longitude(31.4), Latitude(-16.1))))
      )
    )
    def refused(in: Place.OverrideSet) =
      Place.Overrides.edit(in).left.map(_.code)
    assertEquals(
      refused(Place.OverrideSet(pointLat = Some(Latitude(38.5)), pointLon = Some(Longitude(57.5)))),
      Left(Some(Write.CodeInvalid)),
      "no reason: a point must name where it comes from"
    )
    assertEquals(
      refused(Place.OverrideSet(pointLat = Some(Latitude(38.5)), reason = Some(Place.OverrideSet.Reason("x")))),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        Place.OverrideSet(pointLat = Some(Latitude(91)), pointLon = Some(Longitude(0)), reason = Some(Place.OverrideSet.Reason("x")))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        Place.OverrideSet(pointLat = Some(Latitude(0)), pointLon = Some(Longitude(-181)), reason = Some(Place.OverrideSet.Reason("x")))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        Place
          .OverrideSet(pointLat = Some(Latitude(Double.NaN)), pointLon = Some(Longitude(0)), reason = Some(Place.OverrideSet.Reason("x")))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(
        Place
          .OverrideSet(pointLat = Some(Latitude(1)), pointLon = Some(Longitude(1)), reason = Some(Place.OverrideSet.Reason("x")), reset = Place.OverrideSet.Reset(true))
      ),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      parse("""{"pointLat":38.5,"pointLon":57.5,"reason":"r"}""").flatMap(
        _.as[Place.OverrideSet]
      ),
      Right(
        Place
          .OverrideSet(reason = Some(Place.OverrideSet.Reason("r")), pointLat = Some(Latitude(38.5)), pointLon = Some(Longitude(57.5)))
      )
    )

  test("--point is latitude,longitude"):
    assertEquals(Place.Overrides.parsePoint(Place.Overrides.PointText("38.5,57.5")), Right((38.5, 57.5)))
    assertEquals(
      Place.Overrides.parsePoint(Place.Overrides.PointText(" -16.10108 , 31.45915 ")),
      Right((-16.10108, 31.45915))
    )
    assert(Place.Overrides.parsePoint(Place.Overrides.PointText("38.5")).isLeft)
    assert(Place.Overrides.parsePoint(Place.Overrides.PointText("38.5,57.5,1")).isLeft)
    assert(Place.Overrides.parsePoint(Place.Overrides.PointText("north,east")).isLeft)
    assert(Place.Overrides.parsePoint(Place.Overrides.PointText("95,10")).isLeft)
    assert(Place.Overrides.parsePoint(Place.Overrides.PointText("10,190")).isLeft)

  test("a point as a reader sees it, and in the Markdown of a set and of the overrides"):
    assertEquals(Place.Overrides.pointText(Latitude(38.5), Longitude(57.5)), "38.5000 N 57.5000 E")
    assertEquals(Place.Overrides.pointText(Latitude(-16.10108), Longitude(-31.45915)), "16.1011 S 31.4592 W")
    val moved = Place.OverrideChanged(
      Place.Id("pleiades_952097"),
      Place.OverrideChanged.Label("Parthyene/Parthia"),
      reset = Place.OverrideChanged.Reset(false),
      Place.OverrideChanged.KindBefore("region"),
      Place.OverrideChanged.KindAfter("region"),
      Place.OverrideChanged.PeriodBefore("900 BCE–300 CE"),
      Place.OverrideChanged.PeriodAfter("900 BCE–300 CE"),
      Place.OverrideChanged.FindingsBefore(0),
      Place.OverrideChanged.FindingsAfter(0),
      Place.OverrideChanged.Unlocated(0),
      Some(Latitude(33.09361)),
      Some(Longitude(44.58083)),
      Some(Latitude(38.5)),
      Some(Longitude(57.5))
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
        .placeOverrideChanged(moved.modify(_.latAfter).setTo(Some(Latitude(33.09361))).modify(_.lonAfter).setTo(Some(Longitude(44.58083))))
        .contains("→ 33"),
      "an unmoved point is stated once"
    )
    val row = Place.Override(
      Place.Id("pleiades_952097"),
      Place.Override.Label("Parthyene/Parthia"),
      None,
      None,
      None,
      periodSet = Place.Override.PeriodSet(false),
      Some(Place.Override.Reason("Pleiades reprPoint")),
      Place.Override.SourceKind("region"),
      Some(Year(-900)),
      Some(Year(300)),
      Place.Override.UpdatedAt("2026-10-10T12:00:00Z"),
      Some(Latitude(38.5)),
      Some(Longitude(57.5)),
      Some(Latitude(33.09361)),
      Some(Longitude(44.58083))
    )
    assertEquals(
      PlacesMarkdown.placeOverrides(List(row)),
      "1 place(s) with a kind, period or point set by hand:\n\n- **Parthyene/Parthia** `pleiades_952097`: " +
        "point 38.5000 N 57.5000 E _(the source says: region, 900 BCE–300 CE, point 33.0936 N 44.5808 E)_ — Pleiades reprPoint"
    )

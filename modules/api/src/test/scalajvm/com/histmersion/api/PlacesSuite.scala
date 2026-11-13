package com.histmersion
package api

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.PlacesMarkdown
import com.histmersion.api.library.SearchMarkdown

import com.histmersion.api.catalogue.{Lpf, Period}
import com.histmersion.api.library.Facet
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey

/** Pure tests for the gazetteer's Linked-Places / GeoJSON reader-writer (plan §1.6a). No database. */
class PlacesSuite extends munit.FunSuite:

  private def one(json: String, kind: Place.KindValue = Place.KindValue(Place.Kind.Region)): Place.Input =
    Lpf.parse(json, kind) match
      case Right(x :: Nil) => x
      case other           => fail(s"expected exactly one place, got $other")

  // parseYear and parseWhen: api LpfTimeSuite (run on the JVM and under Scala.js, #1403)

  // ── features ────────────────────────────────────────────────────────────────────────────────
  private val hattusa =
    """{"type":"FeatureCollection","features":[
      | {"@id":"pleiades_550595","type":"Feature",
      |  "properties":{"title":"Hattusa","ccodes":["TR"]},
      |  "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]},
      |  "names":[{"toponym":"Hattusa","lang":"en"},{"toponym":"Ḫattuša","lang":"hit"},
      |           {"toponym":"Boğazkale","lang":"tr","when":{"timespans":[{"start":{"in":"1900"}}]}}],
      |  "types":[{"label":"settlement"}],
      |  "geometry":{"type":"Point","coordinates":[34.615,40.019]},
      |  "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"hittite_empire",
      |                "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]}}]}]}""".stripMargin

  test("LPF point: id, title, types→kind, when→period, geometry passthrough"):
    val p = one(hattusa)
    assertEquals(p.place.id, Place.Id("pleiades_550595"))
    assertEquals(p.place.label, "Hattusa")
    assertEquals(p.place.kind, Place.Kind.Site, "types[].label 'settlement' maps to site")
    assertEquals(p.place.period, Some(Period(Year(-1650), Year(-1180))))
    assert(p.place.geometry.exists(_.value.contains("\"Point\"")), p.place.geometry.toString)
    assert(p.place.properties.exists(_.value.contains("ccodes")), "unmodelled properties are kept verbatim")

  test("names in several languages, deduped and sorted, with their own validity"):
    val p = one(hattusa)
    assertEquals(p.names.map(_.name).toSet, Set("Hattusa", "Ḫattuša", "Boğazkale"))
    assertEquals(p.names.find(_.name.value == "Hattusa").map(_.lang), Some("en"))
    assertEquals(
      p.names.find(_.name.value == "Boğazkale").map(_.bounds),
      Some((Some(1900L), None)),
      "a name used from 1900 on: the end stays open, not the one year 1900"
    )
    assertEquals(p.names, p.names.sortBy(n => (n.name.value.toLowerCase, n.lang.value)), "deterministic order")

  test("relations carry their OWN when"):
    val r = one(hattusa).relations
    assertEquals(
      r.map(x => (x.childId, x.parentId, x.kind)),
      List(("pleiades_550595", "hittite_empire", Place.Relation.PartOf))
    )
    assertEquals(r.head.period, Some(Period(Year(-1650), Year(-1180))))

  test("multipolygon with no `when` is an always-valid place"):
    val json =
      """{"type":"FeatureCollection","features":[
        | {"@id":"ne_country_tur","type":"Feature","properties":{"NAME":"Turkey","CONTINENT":"Asia"},
        |  "geometry":{"type":"MultiPolygon","coordinates":[[[[26.0,36.0],[45.0,36.0],[45.0,42.0],[26.0,42.0],[26.0,36.0]]]]}}]}""".stripMargin
    val p = one(json)
    assertEquals(p.place.period, None, "no `when` = valid at every date")
    assertEquals(p.place.label, "Turkey", "plain GeoJSON falls back to properties.NAME")
    assertEquals(p.place.kind, Place.Kind.Region, "the caller's --kind wins when nothing declares one")
    assert(p.place.geometry.exists(_.value.contains("MultiPolygon")))
    assertEquals(p.names.map(_.name), List("Turkey"))
    assertEquals(p.relations, Nil)

  test("plain GeoJSON: properties.period / parent / source (what stream 2.2's polygons carry)"):
    val json =
      """{"type":"FeatureCollection","features":[
        | {"type":"Feature","properties":{"title":"Hittite Empire","kind":"polity",
        |   "period":[-1400,-1180],"parent":"anatolia","source":"AWMC LBA map"},
        |  "geometry":{"type":"Polygon","coordinates":[[[30.0,38.0],[36.0,38.0],[36.0,41.0],[30.0,41.0],[30.0,38.0]]]}}]}""".stripMargin
    val p = one(json, Place.KindValue(Place.Kind.Site))
    assertEquals(p.place.id, Place.Id("hittite_empire"), "no @id → a slug of the title")
    assertEquals(p.place.kind, Place.Kind.Polity)
    assertEquals(p.place.period, Some(Period(Year(-1400), Year(-1180))))
    assertEquals(p.place.source, Some("AWMC LBA map"))
    assertEquals(p.relations.map(_.parentId), List("anatolia"))

  test("properties.period as a string, and start/end pairs"):
    def per(props: String) =
      one(
        s"""{"type":"FeatureCollection","features":[{"type":"Feature","properties":$props,"geometry":null}]}"""
      ).place.period
    assertEquals(per("""{"title":"a","period":"-3300,-2600"}"""), Some(Period(Year(-3300), Year(-2600))))
    assertEquals(per("""{"title":"b","periodStart":-1200,"periodEnd":-800}"""), Some(Period(Year(-1200), Year(-800))))
    assertEquals(per("""{"title":"c","minDate":-750,"maxDate":-330}"""), Some(Period(Year(-750), Year(-330))))
    assertEquals(per("""{"title":"d"}"""), None)

  test("a feature list and a single feature parse like a FeatureCollection"):
    val f = """{"@id":"x","type":"Feature","properties":{"title":"X"},"geometry":null}"""
    assertEquals(Lpf.parse(s"[$f]").map(_.map(_.place.id)), Right(List(Place.Id("x"))))
    assertEquals(Lpf.parse(f).map(_.map(_.place.id)), Right(List(Place.Id("x"))))

  test("a malformed document is a Left, never a partial import"):
    assert(Lpf.parse("{").isLeft)
    assert(Lpf.parse("""{"type":"Topology"}""").isLeft)
    assert(Lpf.parse("""{"type":"FeatureCollection","features":[1]}""").isLeft)
    assertEquals(Lpf.parse("""{"type":"FeatureCollection","features":[]}"""), Right(Nil))

  // ── round trip ──────────────────────────────────────────────────────────────────────────────
  test("render → parse is the identity (the plan's LPF round-trip)"):
    val all = List(
      hattusa,
      """{"type":"FeatureCollection","features":[
        | {"@id":"ne_country_tur","type":"Feature","properties":{"NAME":"Turkey","CONTINENT":"Asia"},
        |  "geometry":{"type":"MultiPolygon","coordinates":[[[[26.0,36.0],[45.0,36.0],[45.0,42.0],[26.0,42.0],[26.0,36.0]]]]}},
        | {"@id":"ne_continent_asia","type":"Feature","properties":{"title":"Asia","kind":"region"},
        |  "geometry":null}]}""".stripMargin
    )
    all.foreach { doc =>
      val parsed = Lpf.parse(doc).fold(e => fail(e.sentence), identity)
      assertEquals(Lpf.parse(Lpf.render(parsed).value), Right(parsed))
      // and idempotent: rendering the reparse gives byte-identical text
      assertEquals(
        Lpf.render(Lpf.parse(Lpf.render(parsed).value).fold(e => fail(e.sentence), identity)),
        Lpf.render(parsed)
      )
    }

  // ── the dialect the hand-traced polygons under db/places/polities use ───────────────────────
  private val tracedPolity =
    """{"type":"FeatureCollection","features":[
      | {"type":"Feature","properties":{
      |   "id":"hittite_empire_lba","label":"Hittite Empire","kind":"polity",
      |   "period_start":-1400,"period_end":-1180,
      |   "names":[{"name":"Hatti","lang":"hit"},{"name":"Hittite Empire","lang":"en"}],
      |   "part_of":[{"parent_id":"anatolia","period_start":-1400,"period_end":-1180},
      |              {"parent_id":"levant","period_start":-1400,"period_end":-1180}],
      |   "source":"traced by hand after Bryce (2005)","licence":"CC0-1.0","contested":true},
      |  "geometry":{"type":"Polygon","coordinates":[[[30.0,38.0],[36.0,38.0],[36.0,41.0],[30.0,41.0],[30.0,38.0]]]}}]}""".stripMargin

  test("plain-GeoJSON dialect: properties.{id,label,kind,period_start,part_of[],names[]}"):
    val p = one(tracedPolity)
    assertEquals(p.place.id, Place.Id("hittite_empire_lba"))
    assertEquals(p.place.label, "Hittite Empire")
    assertEquals(p.place.kind, Place.Kind.Polity)
    assertEquals(p.place.period, Some(Period(Year(-1400), Year(-1180))))
    assertEquals(p.place.source, Some("traced by hand after Bryce (2005)"))
    assertEquals(p.names.map(n => (n.name, n.lang)).toSet, Set(("Hatti", "hit"), ("Hittite Empire", "en")))
    assertEquals(
      p.relations.map(r => (r.parentId, r.period)),
      List(("anatolia", Some(Period(Year(-1400), Year(-1180)))), ("levant", Some(Period(Year(-1400), Year(-1180))))),
      "part_of is an ARRAY of objects, each with its own validity"
    )

  test("unmodelled properties (contested, licence) survive the import verbatim"):
    val props = one(tracedPolity).place.properties.map(_.value).getOrElse("")
    assert(props.contains("\"contested\":true"), props)
    assert(props.contains("CC0-1.0"), props)

  test("a macro-region's null period_start/period_end means unbounded, not year zero"):
    val json =
      """{"type":"FeatureCollection","features":[
        | {"type":"Feature","properties":{"id":"anatolia","label":"Anatolia","kind":"region",
        |   "period_start":null,"period_end":null,
        |   "names":[{"name":"Anatolia","lang":"en"},{"name":"Asia Minor","lang":"en"}],
        |   "part_of":[{"parent_id":"continent_asia","period_start":null,"period_end":null}]},
        |  "geometry":{"type":"Polygon","coordinates":[[[26.0,36.0],[45.0,36.0],[45.0,42.0],[26.0,42.0],[26.0,36.0]]]}}]}""".stripMargin
    val p = one(json)
    assertEquals(p.place.period, None, "unbounded = valid at every date")
    assertEquals(
      p.relations,
      List(
        Place
          .Relation(
            Place.Relation.ChildId("anatolia"),
            Place.Relation.ParentId("continent_asia"),
            Place.Relation.Kind(Place.Relation.PartOf),
            None
          )
      )
    )
    assertEquals(p.names.map(_.name), List("Anatolia", "Asia Minor"))

  test("the traced polygons round-trip through LPF too"):
    val parsed = Lpf.parse(tracedPolity).fold(e => fail(e.sentence), identity)
    assertEquals(Lpf.parse(Lpf.render(parsed).value), Right(parsed))

  test("PlaceKind.parse maps foreign vocabularies onto the four kinds"):
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("settlement")), Some(Place.Kind.Site))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("Kingdom")), Some(Place.Kind.Polity))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("Sovereign country")), Some(Place.Kind.Polity))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("continent")), Some(Place.Kind.Region))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("river")), Some(Place.Kind.Feature))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("zzz")), None)
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("")), None)

  // ── rendering ───────────────────────────────────────────────────────────────────────────────
  private val turkey =
    Place.Summary(
      Place.Id("ne_country_tur"),
      Place.Summary.Label("Turkey"),
      Place.Summary.Kind(Place.Kind.Region),
      None,
      None,
      Some(Place.Source("Natural Earth")),
      Place.Summary.Names("Turkey, Türkiye")
    )
  private val disputed = Place.Summary(
    Place.Id("israel_united_monarchy_ia1"),
    Place.Summary.Label("Israel (united monarchy, as narrated) (contested)"),
    Place.Summary.Kind(Place.Kind.Polity),
    Some(Year(-1030)),
    Some(Year(-930)),
    Some(Place.Source("Carta Bible Atlas")),
    Place.Summary.Names("Israel, Yisrael"),
    contested = Place.Summary.Contested(true)
  )
  private val hatti =
    Place.Summary(
      Place.Id("hatti_lba"),
      Place.Summary.Label("Hatti"),
      Place.Summary.Kind(Place.Kind.Polity),
      Some(Year(-1400)),
      Some(Year(-1180)),
      None,
      Place.Summary.Names("Hatti"),
      contested = Place.Summary.Contested(true)
    )

  test("a disputed extent is marked wherever its label is drawn"):
    assertEquals(Markdown.contestedMark(true, "Hatti"), " (contested)")
    assertEquals(Markdown.contestedMark(false, "Hatti"), "")
    assertEquals(
      Markdown.contestedMark(true, "Israel (contested)"),
      "",
      "never twice — some traced polygons carry the word in the label itself"
    )

  test("places search output carries the id (that is what --place wants) and the period"):
    val md = PlacesMarkdown.places(
      Vector(
        Place
          .Hit(
            turkey.id,
            turkey.label,
            turkey.kind,
            None,
            None,
            turkey.source,
            turkey.names,
            Some(Place.Hit.MatchedName("Türkiye")),
            Place.Hit.Findings(122)
          ),
        Place.Hit(
          hatti.id,
          hatti.label,
          hatti.kind,
          hatti.periodStart,
          hatti.periodEnd,
          None,
          hatti.names,
          None,
          Place.Hit.Findings(44),
          contested = Place.Hit.Contested(true)
        )
      )
    )
    assert(md.contains("`ne_country_tur`"), md)
    assert(md.contains("matched “Türkiye”"), md)
    assert(md.contains("122 finding(s)"), md)
    assert(md.contains("**Hatti** (contested)"), md)
    assert(md.contains("1400 BCE–1180 BCE"), md)
    assertEquals(PlacesMarkdown.places(Vector.empty), "_No matching places._")

  test("the hierarchy renders as an indented list, one level of indent per depth"):
    val md = PlacesMarkdown.placeTree(
      Vector(
        Place.TreeRow(
          Place.Id("continent_asia"),
          Place.TreeRow.Label("Asia"),
          Place.TreeRow.Kind(Place.Kind.Region),
          Place.TreeRow.Depth(0),
          Place.TreeRow.Findings(400)
        ),
        Place.TreeRow(
          Place.Id("ne_country_tur"),
          Place.TreeRow.Label("Turkey"),
          Place.TreeRow.Kind(Place.Kind.Region),
          Place.TreeRow.Depth(1),
          Place.TreeRow.Findings(122)
        ),
        Place.TreeRow(
          Place.Id("hatti_lba"),
          Place.TreeRow.Label("Hatti"),
          Place.TreeRow.Kind(Place.Kind.Polity),
          Place.TreeRow.Depth(2),
          Place.TreeRow.Findings(44),
          contested = Place.TreeRow.Contested(true)
        )
      )
    )
    assertEquals(md.linesIterator.map(_.takeWhile(_ == ' ').length).toList, List(0, 2, 4))
    assert(md.contains("**Hatti** (contested)"), md)
    assertEquals(PlacesMarkdown.placeTree(Vector.empty), "_No hierarchy._")

  test("place detail names its validity, its parents and what it contains"):
    val md = PlacesMarkdown.place(
      PlaceDetail(
        disputed,
        Some(Place.Geometry("""{"type":"Polygon","coordinates":[]}""")),
        List(
          Place.Summary(
            Place.Id("levant"),
            Place.Summary.Label("Levant"),
            Place.Summary.Kind(Place.Kind.Region),
            None,
            None,
            None,
            Place.Summary.Names("Levant")
          )
        ),
        List(turkey),
        Place.Counts.Findings(5),
        Some(Year(-1000)),
        contested = Place.Entry.Contested(true)
      ),
      Vector(
        Place
          .TreeRow(
            Place.Id("israel_united_monarchy_ia1"),
            disputed.label,
            Place.TreeRow.Kind(Place.Kind.Polity),
            Place.TreeRow.Depth(0),
            Place.TreeRow.Findings(5),
            Place.TreeRow.Contested(true)
          )
      )
    )
    assert(md.startsWith("# Israel (united monarchy, as narrated) (contested)\n"), md.take(80))
    assert(!md.contains("(contested) (contested)"), md.take(120))
    assert(md.contains("as of 1000 BCE"), md)
    assert(md.contains("**Part of**\n- Levant `levant`"), md)
    assert(md.contains("**Contains**\n- Turkey `ne_country_tur`"), md)
    assert(md.contains("## Hierarchy"), md)

  test("place detail renders footprints, links and types when present"):
    val md = PlacesMarkdown.place(
      PlaceDetail(
        hatti,
        Some(Place.Geometry("""{"type":"Polygon","coordinates":[]}""")),
        Nil,
        Nil,
        Place.Counts.Findings(44),
        None,
        contested = Place.Entry.Contested(true),
        footprints = List(
          Place.Footprint(
            Place.Footprint.Id(1),
            Place.Id("hatti_lba"),
            Place.Footprint.Role("extent"),
            Some(Period(Year(-1400), Year(-1180))),
            Some(Place.Footprint.Certainty("uncertain")),
            None,
            Some(Citekey("bryce2005_kingdom"))
          ),
          Place
            .Footprint(
              Place.Footprint.Id(2),
              Place.Id("hatti_lba"),
              Place.Footprint.Role("core"),
              Some(Period(Year(-1400), Year(-1200))),
              Some(Place.Footprint.Certainty("approximate"))
            )
        ),
        links = List(
          Place.Link(
            Place.Id("hatti_lba"),
            Place.Link.Scheme("pleiades"),
            Place.Link.ExternalId("550595"),
            Place.Link.Relation("close_match")
          )
        ),
        types = List(
          Place.Type(Place.Id("hatti_lba"), Place.Type.TypeLabel("polity"), Some(Period(Year(-1400), Year(-1180))))
        ),
        hasBoundary = Place.Entry.HasBoundary(true)
      )
    )
    assert(md.contains("**Footprints (2)**"), md)
    assert(md.contains("extent · 1400 BCE–1180 BCE · _uncertain_ [[bryce2005_kingdom]]"), md)
    assert(md.contains("core · 1400 BCE–1200 BCE · _approximate_"), md)
    assert(md.contains("pleiades: [550595](https://pleiades.stoa.org/places/550595) (close_match)"), md)
    assert(!md.contains(Place.Shape.NoBoundaryNote), "a polygon has its boundary")
    val point = PlacesMarkdown.place(
      PlaceDetail(
        hatti.modify(_.dating).setTo(Place.Dating.EndOnly).modify(_.periodStart).setTo((None: Option[Year])),
        Some(Place.Geometry("""{"type":"Point","coordinates":[34,40]}""")),
        Nil,
        Nil,
        Place.Counts.Findings(0),
        None,
        descriptions = List(
          Place.Description(Place.Description.Text("former country"), Place.Name.Lang("en"), Place.Source("wikidata"))
        )
      )
    )
    assert(point.contains(s"**Shape:** ${Place.Shape.NoBoundaryNote}"), point)
    assert(point.contains("start unknown"), point)
    assert(point.contains("former country"), point)
    assert(md.contains("polity · 1400 BCE–1180 BCE"), md)

  test("the places facet prints the gazetteer id so a chip can filter by it"):
    assertEquals(
      SearchMarkdown.facets(
        Facet.FieldText("places"),
        Vector(
          Facet(Facet.Value("Anatolia"), Facet.Count(110), Some(Facet.Id("anatolia"))),
          Facet(Facet.Value("Aegean"), Facet.Count(112))
        )
      ),
      "### places (2)\n\n- Anatolia (110) · `anatolia`\n- Aegean (112)"
    )

  test("import and rebuild report what they did"):
    assertEquals(
      PlacesMarkdown.placeImport(
        Place.ImportResult(Place.ImportResult.Places(3), Place.ImportResult.Names(7), Place.ImportResult.Relations(2))
      ),
      "imported 3 place(s), 7 name(s), 2 relation(s)"
    )
    assert(
      PlacesMarkdown
        .placeImport(
          Place.ImportResult(
            Place.ImportResult.Places(1),
            Place.ImportResult.Names(1),
            Place.ImportResult.Relations(0),
            List(Place.ImportResult.Failed("x: no such parent 'y'"))
          )
        )
        .contains("no such parent")
    )
    assert(
      PlacesMarkdown
        .placeRebuild(
          Place.RebuildResult(
            Place.RebuildResult.Links(9199),
            Place.RebuildResult.ByGeometry(9199),
            Place.RebuildResult.ByName(0),
            Place.RebuildResult.Located(938),
            Place.RebuildResult.Unlocated(26),
            Place.RebuildResult.Places(16779)
          )
        )
        .contains("938 finding(s) located, 26 unlocated")
    )

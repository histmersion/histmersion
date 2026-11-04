package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{BBox, TimeSpan}
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.catalogue.Historicity
import com.histmersion.api.catalogue.{CatalogRecord, EventCount, EventImportReport, EventTaxonomy}

/** #1449, the host's pure half: the kind taxonomy, the thresholds, and the merge of records into one event per thing.
  * The catalogues' own answers are their plugins' suites'.
  */
class EventCatalogSuite extends munit.FunSuite:

  // ── taxonomy ────────────────────────────────────────────────────────────────────────────────

  test("every kind of the first fifty events is in the taxonomy, with a group and an icon"):
    val first = List(
      "destruction",
      "migration",
      "technological_change",
      "polity_formation",
      "natural_disaster",
      "battle",
      "war",
      "cultural_shift",
      "collapse",
      "diplomatic_era",
      "invasion"
    )
    first.foreach { k =>
      val e = EventTaxonomy.of(k)
      assert(EventTaxonomy.kinds.exists(_.kind == e.kind), s"$k → ${e.kind} is not a kind of the table")
      assert(e.icon.nonEmpty)
    }
    assertEquals(EventTaxonomy.canonical("diplomatic_era"), EventTaxonomy.Era)
    assertEquals(EventTaxonomy.canonical("Naval battle"), EventTaxonomy.NavalBattle)
    assertEquals(EventTaxonomy.canonical("Rebellion"), EventTaxonomy.Revolt)
    assertEquals(EventTaxonomy.group("eruption"), EventTaxonomy.Natural)
    assertEquals(EventTaxonomy.group("siege"), EventTaxonomy.Human)
    assertEquals(EventTaxonomy.group("coronation"), EventTaxonomy.Human, "an unknown kind is human, generic icon")
    assertEquals(EventTaxonomy.icon("coronation"), "circle-dot")

  test("a natural_disaster becomes the sub-kind its label names, else stays generic"):
    assertEquals(EventTaxonomy.refine("natural_disaster", "Minoan eruption of Thera"), "eruption")
    assertEquals(EventTaxonomy.refine("natural_disaster", "4.2 kiloyear climate event"), "climate_event")
    assertEquals(EventTaxonomy.refine("natural_disaster", "Severe drought coincident with Hittite collapse"), "drought")
    assertEquals(EventTaxonomy.refine("natural_disaster", "Late Bronze Age environmental crisis"), "natural_disaster")
    assertEquals(EventTaxonomy.refine("battle", "Battle near a volcano"), "battle", "only the generic kind is refined")

  test("a kind filter: a group selects its kinds (human = every non-natural one), a kind its aliases"):
    val nat = EventTaxonomy.condition(Some("natural")).get
    assert(!nat.negate && nat.kinds.contains("eruption") && nat.kinds.contains("plague"))
    val hum = EventTaxonomy.condition(Some("human")).get
    assert(hum.negate && hum.kinds == nat.kinds)
    assertEquals(EventTaxonomy.condition(Some("revolt")).get.kinds.toSet, Set("revolt", "rebellion", "uprising"))
    assertEquals(EventTaxonomy.condition(Some(" ")), None)
    assert(EventTaxonomy.matches("natural", "tsunami") && !EventTaxonomy.matches("human", "tsunami"))

  test("thresholds: all inside the region, large ones outside, nothing without a position"):
    val region = BBox.parse("-25,20,90,70")
    val t = EventProviders.Thresholds(region, 0, 4, 0, 6.5)
    def erup(lat: Double, lon: Double, vei: Option[Int]) =
      CatalogRecord(
        "gvp",
        "1",
        "",
        "x",
        "eruption",
        TimeSpan(Some(-1), None, None, Some(-1)),
        Some(lat),
        Some(lon),
        vei = vei
      )
    assert(EventProviders.keep(erup(37, 25, Some(1)), t).isRight)
    assert(EventProviders.keep(erup(-10, 120, Some(3)), t).isLeft)
    assert(EventProviders.keep(erup(-10, 120, Some(4)), t).isRight)
    assert(EventProviders.keep(erup(-10, 120, None), t).isLeft)
    assertEquals(EventProviders.keep(erup(37, 25, None).modify(_.lat).setTo((None: _root_.scala.Option[_root_.scala.Double])), t), Left("no position"))
    assertEquals(
      EventProviders.keep(erup(37, 25, Some(5)), t.modify(_.area).setTo(BBox.parse("0,0,10,10"))),
      Left("outside --bbox")
    )

  test("thresholds over the default region, the world: a small eruption or quake anywhere is kept"):
    val world = EventProviders.Thresholds(BBox.parse("-180,-90,180,90"), 0, 4, 0, 6.5)
    def at(kind: String, lat: Double, lon: Double) =
      CatalogRecord(
        "x",
        "1",
        "",
        "x",
        kind,
        TimeSpan(Some(-1), None, None, Some(-1)),
        Some(lat),
        Some(lon),
        vei = Some(1),
        magnitude = Some(4.0)
      )
    for (lat, lon) <- List((-39.3, -71.9), (19.4, -155.3), (-6.1, 105.4), (35.4, 138.7), (-3.1, 37.4), (64.0, -19.0))
    do
      assert(EventProviders.keep(at("eruption", lat, lon), world).isRight, s"eruption at $lat,$lon")
      assert(EventProviders.keep(at("earthquake", lat, lon), world).isRight, s"earthquake at $lat,$lon")

  // ── merging ─────────────────────────────────────────────────────────────────────────────────

  private val tol = CatalogMerge.Tolerance(60, 30)
  private def rec(
      p: String,
      id: String,
      kind: String,
      y: Long,
      lat: Double,
      lon: Double,
      wd: Option[String] = None,
      group: Option[String] = None
  ) =
    CatalogRecord(
      p,
      id,
      "",
      s"$p $id",
      kind,
      TimeSpan(Some(y), Some(y), Some(y), Some(y)),
      Some(lat),
      Some(lon),
      wikidata = wd,
      group = group
    )

  test("merge: one catalogue's two records are two events; another catalogue's record of the same thing joins"):
    val a = rec("gvp", "1", "eruption", -1610, 36.4, 25.4, group = Some("volcano:212040"))
    val b = rec("gvp", "2", "eruption", -1600, 36.4, 25.4, group = Some("volcano:212040"))
    val c = rec("noaa", "volcano:1351", "eruption", -1610, 36.404, 25.396, group = Some("volcano:212040"))
    val placed = CatalogMerge.place(List(a, b, c), Nil, tol)
    assertEquals(placed.map(_._2.eventId), List("gvp_1", "gvp_2", "gvp_1"))
    assert(placed(2)._2.isInstanceOf[CatalogMerge.Placement.New])

  test("merge: a stored event is matched by key, by Wikidata item, or by kind+time+space — never by another kind"):
    val compiled = StoredEvent(
      "thera_eruption",
      "eruption",
      TimeSpan(Some(-1620), None, None, Some(-1600)),
      Some(36.4),
      Some(25.4),
      None,
      Set.empty,
      catalogue = false
    )
    val battle = StoredEvent(
      "alesia",
      "battle",
      TimeSpan(Some(-52), None, None, Some(-52)),
      Some(47.5),
      Some(4.5),
      Some("Q131386"),
      Set.empty,
      catalogue = false
    )
    val known = StoredEvent(
      "gvp_9",
      "eruption",
      TimeSpan(Some(-79), None, None, Some(-79)),
      Some(40.8),
      Some(14.4),
      None,
      Set("gvp" -> "9"),
      catalogue = true
    )
    val stored = List(compiled, battle, known)
    val placed = CatalogMerge.place(
      List(
        rec("gvp", "1", "eruption", -1610, 36.41, 25.39),
        rec("wikidata", "Q131386", "siege", -52, 10, 10, wd = Some("Q131386")),
        rec("gvp", "9", "eruption", -79, 0, 0),
        rec("noaa", "earthquake:5", "earthquake", -1610, 36.4, 25.4)
      ),
      stored,
      tol
    )
    assertEquals(placed.map(_._2.eventId), List("thera_eruption", "alesia", "gvp_9", "noaa_earthquake_5"))
    assert(placed.take(3).forall(_._2.isInstanceOf[CatalogMerge.Placement.Existing]))

  test("merge: a stored event that a record of the SAME catalogue made is no match for another of its records"):
    val g1 = StoredEvent(
      "gvp_1",
      "eruption",
      TimeSpan(Some(-1610), None, None, Some(-1610)),
      Some(36.4),
      Some(25.4),
      None,
      Set("gvp" -> "1"),
      catalogue = true
    )
    val placed = CatalogMerge.place(List(rec("gvp", "2", "eruption", -1605, 36.4, 25.4)), List(g1), tol)
    assertEquals(placed.head._2.eventId, "gvp_2")

  test("event ids from records are stable and safe"):
    assertEquals(CatalogRecord.eventId(rec("noaa", "volcano:1351", "eruption", 0, 0, 0)), "noaa_volcano_1351")
    assertEquals(CatalogRecord.eventId(rec("wikidata", "Q131386", "battle", 0, 0, 0)), "wikidata_q131386")

  test("an import report adds up and reads as one line"):
    val a = EventImportReport(
      "gvp",
      dryRun = true,
      records = 10,
      kept = 4,
      created = 3,
      merged = 1,
      byKind = List(EventCount("eruption", 4)),
      skipped = List(EventCount("no position", 6)),
      parts = 1
    )
    val b =
      a.modify(_.records).setTo(5).modify(_.kept).setTo(5).modify(_.created).setTo(5).modify(_.merged).setTo(0).modify(_.byKind).setTo(List(EventCount("eruption", 5))).modify(_.skipped).setTo(Nil)
    val c = EventImportReport.combine(a, b)
    assertEquals((c.records, c.kept, c.created, c.merged, c.parts), (15, 9, 8, 1, 2))
    assertEquals(c.byKind, List(EventCount("eruption", 9)))
    assert(EventImportReport.summary(c).contains("would create 8 event(s)"))
    assert(EventImportReport.markdown(c).contains("dry run: nothing stored"))
    assertEquals(CatalogProvider.parse(" Smithsonian "), Right("gvp"))
    assert(CatalogProvider.parse("usgs").isLeft)

  test("#6561: a part read under other thresholds is not read in this scope"):
    val world = EventProviders.Thresholds(None, 0, 4, 0.0, 6.5)
    val window = world.modify(_.region).setTo(Some(BBox(-25, 5, 100, 72)))
    val (w, n) = (EventProviders.scope(world), EventProviders.scope(window))
    assertNotEquals(w, n)
    val recorded = Set(EventProviders.scopedPart("years:-3000..-2000", n), "years:-2000..-1000")
    assertEquals(EventProviders.partsIn(recorded, n), Set("years:-3000..-2000"))
    assertEquals(EventProviders.partsIn(recorded, w), Set.empty[String], "old-scope and unscoped keys are due again")

package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{BBox, TimeSpan}
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.catalogue.Historicity
import com.histmersion.api.catalogue.{CatalogRecord, EventCount, EventImportReport, EventTaxonomy}
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.domain.catalogue.events.EventProviders
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.events.CatalogMerge
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.events.StoredEvent
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.CatalogProviderInfo
import com.histmersion.lint.{tolerated, LintRule}

/** #1449, the host's pure half: the kind taxonomy, the thresholds, and the merge of records into one event per thing.
  * The catalogues' own answers are their plugins' suites'.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
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
      val e = EventTaxonomy.of(EventSummary.Kind(k))
      assert(EventTaxonomy.kinds.exists(_.kind == e.kind), s"$k → ${e.kind} is not a kind of the table")
      assert(e.icon.value.nonEmpty)
    }
    assertEquals(EventTaxonomy.canonical(EventSummary.Kind("diplomatic_era")), EventTaxonomy.Era)
    assertEquals(EventTaxonomy.canonical(EventSummary.Kind("Naval battle")), EventTaxonomy.NavalBattle)
    assertEquals(EventTaxonomy.canonical(EventSummary.Kind("Rebellion")), EventTaxonomy.Revolt)
    assertEquals(EventTaxonomy.group(EventSummary.Kind("eruption")), EventTaxonomy.Natural)
    assertEquals(EventTaxonomy.group(EventSummary.Kind("siege")), EventTaxonomy.Human)
    assertEquals(
      EventTaxonomy.group(EventSummary.Kind("coronation")),
      EventTaxonomy.Human,
      "an unknown kind is human, generic icon"
    )
    assertEquals(EventTaxonomy.icon(EventSummary.Kind("coronation")), "circle-dot")

  test("a natural_disaster becomes the sub-kind its label names, else stays generic"):
    assertEquals(
      EventTaxonomy.refine(EventSummary.Kind("natural_disaster"), EventSummary.Label("Minoan eruption of Thera")),
      "eruption"
    )
    assertEquals(
      EventTaxonomy.refine(EventSummary.Kind("natural_disaster"), EventSummary.Label("4.2 kiloyear climate event")),
      "climate_event"
    )
    assertEquals(
      EventTaxonomy.refine(
        EventSummary.Kind("natural_disaster"),
        EventSummary.Label("Severe drought coincident with Hittite collapse")
      ),
      "drought"
    )
    assertEquals(
      EventTaxonomy
        .refine(EventSummary.Kind("natural_disaster"), EventSummary.Label("Late Bronze Age environmental crisis")),
      "natural_disaster"
    )
    assertEquals(
      EventTaxonomy.refine(EventSummary.Kind("battle"), EventSummary.Label("Battle near a volcano")),
      "battle",
      "only the generic kind is refined"
    )

  test("a kind filter: a group selects its kinds (human = every non-natural one), a kind its aliases"):
    val nat = EventTaxonomy.condition(Some(EventTaxonomy.KindCondition.Filter("natural"))).get
    assert(!nat.negate.value && nat.kinds.contains("eruption") && nat.kinds.contains("plague"))
    val hum = EventTaxonomy.condition(Some(EventTaxonomy.KindCondition.Filter("human"))).get
    assert(hum.negate.value && hum.kinds == nat.kinds)
    assertEquals(
      EventTaxonomy.condition(Some(EventTaxonomy.KindCondition.Filter("revolt"))).get.kinds.toSet,
      Set("revolt", "rebellion", "uprising")
    )
    assertEquals(EventTaxonomy.condition(Some(EventTaxonomy.KindCondition.Filter(" "))), None)
    assert(
      EventTaxonomy.matches(
        EventTaxonomy.KindCondition.Filter("natural"),
        EventSummary.Kind("tsunami")
      ) && !EventTaxonomy.matches(EventTaxonomy.KindCondition.Filter("human"), EventSummary.Kind("tsunami"))
    )

  test("thresholds: all inside the region, large ones outside, nothing without a position"):
    val region = BBox.parse(BBox.Text("-25,20,90,70"))
    val t = EventProviders.Thresholds(
      region,
      EventProviders.Thresholds.RegionVei(0),
      EventProviders.Thresholds.OutsideVei(4),
      EventProviders.Thresholds.RegionMagnitude(0),
      EventProviders.Thresholds.OutsideMagnitude(6.5)
    )
    def erup(lat: Latitude, lon: Longitude, vei: Option[CatalogRecord.Vei]) =
      CatalogRecord(
        CatalogRecord.Provider("gvp"),
        CatalogRecord.ProviderId("1"),
        CatalogRecord.Url(""),
        CatalogRecord.Label("x"),
        CatalogRecord.Kind("eruption"),
        TimeSpan(Some(Year(-1)), None, None, Some(Year(-1))),
        Some(lat),
        Some(lon),
        vei = vei
      )
    assert(EventProviders.keep(erup(Latitude(37), Longitude(25), Some(CatalogRecord.Vei(1))), t).isRight)
    assert(EventProviders.keep(erup(Latitude(-10), Longitude(120), Some(CatalogRecord.Vei(3))), t).isLeft)
    assert(EventProviders.keep(erup(Latitude(-10), Longitude(120), Some(CatalogRecord.Vei(4))), t).isRight)
    assert(EventProviders.keep(erup(Latitude(-10), Longitude(120), None), t).isLeft)
    assertEquals(
      EventProviders
        .keep(erup(Latitude(37), Longitude(25), None).modify(_.lat).setTo(None: Option[Latitude]), t)
        .left
        .map(_.said),
      Left("no position")
    )
    assertEquals(
      EventProviders
        .keep(
          erup(Latitude(37), Longitude(25), Some(CatalogRecord.Vei(5))),
          t.modify(_.area).setTo(BBox.parse(BBox.Text("0,0,10,10")))
        )
        .left
        .map(_.said),
      Left("outside --bbox")
    )
    assertEquals(
      EventProviders.keep(erup(Latitude(-10), Longitude(120), Some(CatalogRecord.Vei(3))), t).left.map(_.said),
      Left("VEI below 4 outside the region")
    )

  test("thresholds over the default region, the world: a small eruption or quake anywhere is kept"):
    val world = EventProviders.Thresholds(
      BBox.parse(BBox.Text("-180,-90,180,90")),
      EventProviders.Thresholds.RegionVei(0),
      EventProviders.Thresholds.OutsideVei(4),
      EventProviders.Thresholds.RegionMagnitude(0),
      EventProviders.Thresholds.OutsideMagnitude(6.5)
    )
    def at(kind: CatalogRecord.Kind, lat: Latitude, lon: Longitude) =
      CatalogRecord(
        CatalogRecord.Provider("x"),
        CatalogRecord.ProviderId("1"),
        CatalogRecord.Url(""),
        CatalogRecord.Label("x"),
        kind,
        TimeSpan(Some(Year(-1)), None, None, Some(Year(-1))),
        Some(lat),
        Some(lon),
        vei = Some(CatalogRecord.Vei(1)),
        magnitude = Some(CatalogRecord.Magnitude(4.0))
      )
    for (lat, lon) <- List(
        (-39.3, -71.9),
        (19.4, -155.3),
        (-6.1, 105.4),
        (35.4, 138.7),
        (-3.1, 37.4),
        (64.0, -19.0)
      ).map((la, lo) => (Latitude(la), Longitude(lo)))
    do
      assert(EventProviders.keep(at(CatalogRecord.Kind("eruption"), lat, lon), world).isRight, s"eruption at $lat,$lon")
      assert(
        EventProviders.keep(at(CatalogRecord.Kind("earthquake"), lat, lon), world).isRight,
        s"earthquake at $lat,$lon"
      )

  // ── merging ─────────────────────────────────────────────────────────────────────────────────

  private val tol = CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(60), CatalogMerge.Tolerance.Years(30))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def rec(
      p: CatalogRecord.Provider,
      id: CatalogRecord.ProviderId,
      kind: CatalogRecord.Kind,
      y: Year,
      lat: Latitude,
      lon: Longitude,
      wd: Option[CatalogRecord.Wikidata] = None,
      group: Option[CatalogRecord.Group] = None
  ) =
    CatalogRecord(
      p,
      id,
      CatalogRecord.Url(""),
      CatalogRecord.Label(s"$p $id"),
      kind,
      TimeSpan(Some(y), Some(y), Some(y), Some(y)),
      Some(lat),
      Some(lon),
      wikidata = wd,
      group = group
    )

  test("merge: one catalogue's two records are two events; another catalogue's record of the same thing joins"):
    val a = rec(
      CatalogRecord.Provider("gvp"),
      CatalogRecord.ProviderId("1"),
      CatalogRecord.Kind("eruption"),
      Year(-1610),
      Latitude(36.4),
      Longitude(25.4),
      group = Some(CatalogRecord.Group("volcano:212040"))
    )
    val b = rec(
      CatalogRecord.Provider("gvp"),
      CatalogRecord.ProviderId("2"),
      CatalogRecord.Kind("eruption"),
      Year(-1600),
      Latitude(36.4),
      Longitude(25.4),
      group = Some(CatalogRecord.Group("volcano:212040"))
    )
    val c = rec(
      CatalogRecord.Provider("noaa"),
      CatalogRecord.ProviderId("volcano:1351"),
      CatalogRecord.Kind("eruption"),
      Year(-1610),
      Latitude(36.404),
      Longitude(25.396),
      group = Some(CatalogRecord.Group("volcano:212040"))
    )
    val placed = CatalogMerge.place(List(a, b, c), Nil, tol)
    assertEquals(placed.map(_._2.eventId), List("gvp_1", "gvp_2", "gvp_1"))
    assert(placed(2)._2.isInstanceOf[CatalogMerge.Placement.New])

  test("merge: a stored event is matched by key, by Wikidata item, or by kind+time+space — never by another kind"):
    val compiled = StoredEvent(
      Event.Id("thera_eruption"),
      StoredEvent.Kind("eruption"),
      TimeSpan(Some(Year(-1620)), None, None, Some(Year(-1600))),
      Some(com.histmersion.Latitude(36.4)),
      Some(com.histmersion.Longitude(25.4)),
      None,
      Set.empty,
      catalogue = StoredEvent.Catalogue(false)
    )
    val battle = StoredEvent(
      Event.Id("alesia"),
      StoredEvent.Kind("battle"),
      TimeSpan(Some(Year(-52)), None, None, Some(Year(-52))),
      Some(com.histmersion.Latitude(47.5)),
      Some(com.histmersion.Longitude(4.5)),
      Some(EventEntry.Wikidata("Q131386")),
      Set.empty,
      catalogue = StoredEvent.Catalogue(false)
    )
    val known = StoredEvent(
      Event.Id("gvp_9"),
      StoredEvent.Kind("eruption"),
      TimeSpan(Some(Year(-79)), None, None, Some(Year(-79))),
      Some(com.histmersion.Latitude(40.8)),
      Some(com.histmersion.Longitude(14.4)),
      None,
      Set(CatalogRecord.Provider("gvp") -> CatalogRecord.ProviderId("9")),
      catalogue = StoredEvent.Catalogue(true)
    )
    val stored = List(compiled, battle, known)
    val placed = CatalogMerge.place(
      List(
        rec(
          CatalogRecord.Provider("gvp"),
          CatalogRecord.ProviderId("1"),
          CatalogRecord.Kind("eruption"),
          Year(-1610),
          Latitude(36.41),
          Longitude(25.39)
        ),
        rec(
          CatalogRecord.Provider("wikidata"),
          CatalogRecord.ProviderId("Q131386"),
          CatalogRecord.Kind("siege"),
          Year(-52),
          Latitude(10),
          Longitude(10),
          wd = Some(EventEntry.Wikidata("Q131386"))
        ),
        rec(
          CatalogRecord.Provider("gvp"),
          CatalogRecord.ProviderId("9"),
          CatalogRecord.Kind("eruption"),
          Year(-79),
          Latitude(0),
          Longitude(0)
        ),
        rec(
          CatalogRecord.Provider("noaa"),
          CatalogRecord.ProviderId("earthquake:5"),
          CatalogRecord.Kind("earthquake"),
          Year(-1610),
          Latitude(36.4),
          Longitude(25.4)
        )
      ),
      stored,
      tol
    )
    assertEquals(placed.map(_._2.eventId), List("thera_eruption", "alesia", "gvp_9", "noaa_earthquake_5"))
    assert(placed.take(3).forall(_._2.isInstanceOf[CatalogMerge.Placement.Existing]))

  test("merge: a stored event that a record of the SAME catalogue made is no match for another of its records"):
    val g1 = StoredEvent(
      Event.Id("gvp_1"),
      StoredEvent.Kind("eruption"),
      TimeSpan(Some(Year(-1610)), None, None, Some(Year(-1610))),
      Some(com.histmersion.Latitude(36.4)),
      Some(com.histmersion.Longitude(25.4)),
      None,
      Set(CatalogRecord.Provider("gvp") -> CatalogRecord.ProviderId("1")),
      catalogue = StoredEvent.Catalogue(true)
    )
    val placed = CatalogMerge.place(
      List(
        rec(
          CatalogRecord.Provider("gvp"),
          CatalogRecord.ProviderId("2"),
          CatalogRecord.Kind("eruption"),
          Year(-1605),
          Latitude(36.4),
          Longitude(25.4)
        )
      ),
      List(g1),
      tol
    )
    assertEquals(placed.head._2.eventId, "gvp_2")

  test("event ids from records are stable and safe"):
    assertEquals(
      CatalogRecord.eventId(
        rec(
          CatalogRecord.Provider("noaa"),
          CatalogRecord.ProviderId("volcano:1351"),
          CatalogRecord.Kind("eruption"),
          Year(0),
          Latitude(0),
          Longitude(0)
        )
      ),
      "noaa_volcano_1351"
    )
    assertEquals(
      CatalogRecord.eventId(
        rec(
          CatalogRecord.Provider("wikidata"),
          CatalogRecord.ProviderId("Q131386"),
          CatalogRecord.Kind("battle"),
          Year(0),
          Latitude(0),
          Longitude(0)
        )
      ),
      "wikidata_q131386"
    )

  test("an import report adds up and reads as one line"):
    val a = EventImportReport(
      EventImportReport.Provider("gvp"),
      dryRun = EventImportReport.DryRun(true),
      records = EventImportReport.Records(10),
      kept = EventImportReport.Kept(4),
      created = EventImportReport.Created(3),
      merged = EventImportReport.Merged(1),
      byKind = List(EventCount(EventCount.Key("eruption"), EventCount.N(4))),
      skipped = List(EventCount(EventCount.Key("no position"), EventCount.N(6))),
      parts = EventImportReport.Parts(1)
    )
    val b =
      a.modify(_.records)
        .setTo(EventImportReport.Records(5))
        .modify(_.kept)
        .setTo(EventImportReport.Kept(5))
        .modify(_.created)
        .setTo(EventImportReport.Created(5))
        .modify(_.merged)
        .setTo(EventImportReport.Merged(0))
        .modify(_.byKind)
        .setTo(List(EventCount(EventCount.Key("eruption"), EventCount.N(5))))
        .modify(_.skipped)
        .setTo(Nil)
    val c = EventImportReport.combine(a, b)
    assertEquals((c.records, c.kept, c.created, c.merged, c.parts), (15, 9, 8, 1, 2))
    assertEquals(c.byKind, List(EventCount(EventCount.Key("eruption"), EventCount.N(9))))
    assert(EventImportReport.summary(c).contains("would create 8 event(s)"))
    assert(EventImportReport.markdown(c).value.contains("dry run: nothing stored"))
    assertEquals(CatalogProvider.parse(CatalogProviderInfo.Id(" Smithsonian ")), Right("gvp"))
    assert(CatalogProvider.parse(CatalogProviderInfo.Id("usgs")).isLeft)

  test("#6561: a part read under other thresholds is not read in this scope"):
    val world = EventProviders.Thresholds(
      None,
      EventProviders.Thresholds.RegionVei(0),
      EventProviders.Thresholds.OutsideVei(4),
      EventProviders.Thresholds.RegionMagnitude(0.0),
      EventProviders.Thresholds.OutsideMagnitude(6.5)
    )
    val window = world.modify(_.region).setTo(Some(BBox(Longitude(-25), Latitude(5), Longitude(100), Latitude(72))))
    val (w, n) = (EventProviders.scope(world), EventProviders.scope(window))
    assertNotEquals(w, n)
    val recorded = Set(EventProviders.scopedPart("years:-3000..-2000", n), "years:-2000..-1000")
    assertEquals(EventProviders.partsIn(recorded, n), Set("years:-3000..-2000"))
    assertEquals(EventProviders.partsIn(recorded, w), Set.empty[String], "old-scope and unscoped keys are due again")

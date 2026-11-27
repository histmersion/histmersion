package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Tree

import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.GeoPoint
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.Place.Id as PlaceId
import com.histmersion.api.library.Tree.TreeRow
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Period, Place}
import com.histmersion.api.library.{Bucket, Filter, Finding, Source}
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.lint.{tolerated, LintRule}

/** [[TreeRepo]] against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise.
  *
  * The fixture is the smallest gazetteer that exercises everything the pure [[Tree]] tests cannot: **two OVERLAPPING
  * polity polygons** under one parent region, so a finding in the overlap is materialised into both (non-additive
  * counts), a period on each polity so the as-of walk has something to drop, and **one finding nowhere at all** so the
  * `unlocated` leaf has an occupant.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class TreeRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    Place.Geometry(
      s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""
    )

  //  zzt_world ── zzt_land ─┬─ zzt_north  (-1650…-1180)  lon 171…175
  //                         └─ zzt_south  (-1500…-1300)  lon 174…178   ← overlap: lon 174…175
  //
  // Deliberately in an EMPTY corner of the South Pacific: the db suites share one test database, and
  // a fixture polygon drawn over Anatolia silently changes what `PlaceRepoSuite` (and any seeded
  // gazetteer) materialises. Keep new fixture geometry away from real places.
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val batch = List(
    Place.Input(
      Place(Place.Id("zzt_world"), Place.Label("Zzt World"), Place.KindValue(Place.Kind.Region)),
      List(Place.Name(Place.Name.Value("Zzt World")))
    ),
    Place.Input(
      Place(
        Place.Id("zzt_land"),
        Place.Label("Zzt Land"),
        Place.KindValue(Place.Kind.Region),
        Some(box(170, -50, 179, -40))
      ),
      List(Place.Name(Place.Name.Value("Zzt Land"))),
      List(Place.Relation(Place.Relation.ChildId("zzt_land"), Place.Relation.ParentId("zzt_world")))
    ),
    Place.Input(
      Place(
        Place.Id("zzt_north"),
        Place.Label("Zzt North"),
        Place.KindValue(Place.Kind.Polity),
        Some(box(171, -48, 175, -44)),
        Some(Period(Year(-1650), Year(-1180))),
        Some(Place.Source("test fixture"))
      ),
      List(Place.Name(Place.Name.Value("Zzt North"))),
      List(
        Place.Relation(
          Place.Relation.ChildId("zzt_north"),
          Place.Relation.ParentId("zzt_land"),
          Place.Relation.Kind(Place.Relation.PartOf),
          Some(Period(Year(-1650), Year(-1180)))
        )
      )
    ),
    Place.Input(
      Place(
        Place.Id("zzt_south"),
        Place.Label("Zzt South"),
        Place.KindValue(Place.Kind.Polity),
        Some(box(174, -47, 178, -44)),
        Some(Period(Year(-1500), Year(-1300))),
        Some(Place.Source("test fixture"))
      ),
      List(Place.Name(Place.Name.Value("Zzt South"))),
      List(
        Place.Relation(
          Place.Relation.ChildId("zzt_south"),
          Place.Relation.ParentId("zzt_land"),
          Place.Relation.Kind(Place.Relation.PartOf),
          Some(Period(Year(-1500), Year(-1300)))
        )
      )
    )
  )

  private val Tag = "zzt-tree-fixture"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val src = Source(
    citekey = Citekey("zzt_src"),
    title = api.library.Title("Tree fixture"),
    authors = (List("T")).map(api.library.AuthorName.apply),
    doi = None,
    year = (Some(2021)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = List(Source.Tags(Tag)),
    period = Some(Period(Year(-1400), Year(-1350))),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      // in the OVERLAP of the two polities (and in the region containing both)
      Finding(
        Finding.Id("zzt_src#0"),
        Citekey("zzt_src"),
        Finding.Claim("Frontier hoard"),
        Some(Finding.Region("Frontier")),
        Some(GeoPoint(Longitude(174.5), Latitude(-45.0))),
        Some(Period(Year(-1400), Year(-1350))),
        None,
        Nil,
        Some(Confidence.High)
      ),
      // in the northern polity only
      Finding(
        Finding.Id("zzt_src#1"),
        Citekey("zzt_src"),
        Finding.Claim("Capital archive"),
        Some(Finding.Region("Capital")),
        Some(GeoPoint(Longitude(172.0), Latitude(-45.0))),
        Some(Period(Year(-1400), Year(-1350))),
        None,
        Nil,
        None
      ),
      // nowhere: mid-Atlantic, no place covers it
      Finding(
        Finding.Id("zzt_src#2"),
        Citekey("zzt_src"),
        Finding.Claim("Atlantic core"),
        None,
        Some(GeoPoint(Longitude(-30.0), Latitude(45.0))),
        Some(Period(Year(-1400), Year(-1350))),
        None,
        Nil,
        None
      )
    ),
    edges = Nil
  )

  private def seed(): Unit =
    val _ = PlaceFixtures.upsertPlaces(ds, batch)
    SourceFixtures.upsert(ds, src, cfg.limits.chunkChars)
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)

  /** Only the fixture: the tag rides on the SOURCE, and the finding-level tag predicate unions
    * `findings.tags ∪ sources.tags`, so every one of its findings matches and nothing else does.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val filter = Filter(
    tags = List(Filter.Tags(Tag)),
    period = Some(Period(Year(-1600), Year(-1200)))
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def find(bs: List[Bucket], key: String): Option[Bucket] =
    bs.iterator.map(b => if b.key.value == key then Some(b) else find(b.children, key)).collectFirst { case Some(b) =>
      b
    }

  // ── the rows ─────────────────────────────────────────────────────────────────────────────────

  test("rows(findings): one row per finding, with the places it was materialised into"):
    seed()
    val rows = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("findings"), filter).toList
    assertEquals(rows.map(_.key.value).sorted, List("zzt_src#0", "zzt_src#1", "zzt_src#2"))
    def places(id: String) = rows.find(_.key.value == id).get.placeIds.filter(_.value.startsWith("zzt_")).toSet
    assertEquals(
      places("zzt_src#0"),
      Set("zzt_land", "zzt_north", "zzt_south"),
      "the overlap of two polities, plus the region containing both"
    )
    assertEquals(places("zzt_src#1"), Set("zzt_land", "zzt_north"))
    assertEquals(places("zzt_src#2"), Set.empty[String], "no place covers the mid-Atlantic")
    assertEquals(rows.find(_.key.value == "zzt_src#0").get.citekey, "zzt_src")
    assertEquals(rows.find(_.key.value == "zzt_src#0").get.title, "Tree fixture")
    assertEquals(rows.find(_.key.value == "zzt_src#0").get.start, Some(-1400L))
    assertEquals(rows.find(_.key.value == "zzt_src#0").get.end, Some(-1350L))

  test("rows(sources): one row per SOURCE, with the UNION of its matching findings' places"):
    seed()
    val rows = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("sources"), filter).toList
    assertEquals(rows.map(_.key), List("zzt_src"))
    assertEquals(rows.head.placeIds.filter(_.value.startsWith("zzt_")).toSet, Set("zzt_land", "zzt_north", "zzt_south"))
    assertEquals((rows.head.start, rows.head.end), (Some(-1400L), Some(-1350L)))

  test("rows agree with the histogram about which sources are in the window"):
    seed()
    val keys = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("sources"), filter).map(_.key).toSet
    val periods = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("sources"), filter)
    assertEquals(keys.size, periods.size, "the tree and the timeline strip select the same sources")

  test("a window that excludes the fixture returns no rows"):
    seed()
    assertEquals(
      SearchFixtures
        .treeRows(
          ds,
          SearchFixtures.AggregateUnit("sources"),
          filter.modify(_.period).setTo(Some(Period(Year(-500), Year(-200))))
        )
        .toList,
      Nil
    )

  // ── the gazetteer slice ──────────────────────────────────────────────────────────────────────

  test("gazetteer() closes UPWARD over the hierarchy, whatever an edge's validity"):
    seed()
    val rows = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("findings"), filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    assert(ns.exists(_.id.value == "zzt_world"), "the ancestor nobody is materialised into is still needed")
    assertEquals(ns.find(_.id.value == "zzt_north").flatMap(_.period), Some(Period(Year(-1650), Year(-1180))))
    assertEquals(ns.find(_.id.value == "zzt_world").flatMap(_.period), None)
    assertEquals(
      ns.find(_.id.value == "zzt_land").map(_.kind),
      Some(domain.catalogue.Place.Kind.Region)
    )
    assert(rels.exists(r => r.childId.value == "zzt_north" && r.parentId.value == "zzt_land"))
    assertEquals(rels.find(_.childId.value == "zzt_south").flatMap(_.period), Some(Period(Year(-1500), Year(-1300))))
    assert(rels.forall(_.kind.value == Place.Relation.PartOf))

  test("gazetteer() runs no query at all when nothing is located"):
    assertEquals(
      PlaceFixtures
        .gazetteer(ds, Vector(TreeRow(TreeRow.Key("x"), Citekey("x"), api.library.Title("X")))),
      (Nil, Nil)
    )

  // ── repo + pure tree, end to end ─────────────────────────────────────────────────────────────

  test("the region tree over real rows: non-additive counts, overlap, and an unlocated leaf"):
    seed()
    val rows = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("findings"), filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    val tree = Tree.regionTree(rows, ns, rels, Some(Year(-1400)), Tree.RegionTreeDepth(3), Tree.RegionTreeSamples(2))

    assertEquals(find(tree, "zzt_world").map(_.count), Some(2), "2 of the 3 findings are located")
    assertEquals(find(tree, "zzt_land").map(_.count), Some(2))
    assertEquals(find(tree, "zzt_north").map(_.count), Some(2))
    assertEquals(find(tree, "zzt_south").map(_.count), Some(1))
    assertEquals(find(tree, "zzt_land").map(_.overlap), Some(1), "the frontier hoard is inside BOTH polities")
    assertEquals(
      find(tree, "zzt_land").map(_.children.map(_.count.value).sum),
      Some(3),
      "children sum to 3 while the parent holds 2 — counts are non-additive"
    )
    assertEquals(find(tree, Tree.KeyUnlocated).map(_.count), Some(1))
    assertEquals(find(tree, "zzt_north").map(_.samples.map(_.citekey)), Some(List("zzt_src")))

  test("as of -1250 the southern polity is gone, so the frontier hoard sits under the northern one alone"):
    seed()
    val rows = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("findings"), filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    val tree = Tree.regionTree(rows, ns, rels, Some(Year(-1250)), Tree.RegionTreeDepth(3))
    assertEquals(find(tree, "zzt_south"), None)
    assertEquals(find(tree, "zzt_north").map(_.count), Some(2))
    assertEquals(find(tree, "zzt_land").map(_.overlap), Some(0))

  test("by=time,region over real rows: the gazetteer inside each time bucket"):
    seed()
    val rows = SearchFixtures.treeRows(ds, SearchFixtures.AggregateUnit("sources"), filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    val tree = Tree.timeRegionTree(
      rows,
      Year(-1600),
      Year(-1200),
      ns,
      rels,
      Tree.TimeRegionTreeDepth(2),
      Tree.TimeRegionTreeSamples(1)
    )
    val bucket = tree.find(_.start.contains(-1400L)).get
    assertEquals(bucket.kind, Tree.KindTime)
    assertEquals(bucket.count, 1)
    assert(bucket.children.map(_.key.value).contains("zzt_world"), bucket.children.map(_.key).toString)
    assertEquals(bucket.children.find(_.key.value == "zzt_world").map(_.children.map(_.key)), Some(List("zzt_land")))

  // ── the SQL itself, without a database ───────────────────────────────────────────────────────

  test("rowsQuery threads the PlaceScope through, so a place chip narrows the tree"):
    val scope = PlaceScope(
      Map(PlaceScope.Value("zzt_north") -> List(PlaceId("zzt_north"), PlaceId("zzt_land")))
    )
    val sql = SearchFixtures.treeRowsQuery(
      SearchFixtures.AggregateUnit("findings"),
      Filter(places = List(Filter.Places("zzt_north"))),
      scope
    )
    assert(sql.sqlString.contains("finding_places"), sql.sqlString)
    val lists = sql.params.map(_.asInstanceOf[Matchable]).collect { case l: List[?] =>
      l.map(_.asInstanceOf[Matchable]).collect { case s: String => s }
    }
    assert(lists.exists(_ == List("zzt_north", "zzt_land")), sql.params.toString)

  test("rowsQuery(sources) gates on a matching finding exactly as the histogram does"):
    val f = Filter(places = List(Filter.Places("anywhere")), period = Some(Period(Year(-1600), Year(-1200))))
    assert(
      SearchFixtures.treeRowsQuery(SearchFixtures.AggregateUnit("sources"), f).sqlString.contains("fp.matched > 0")
    )
    assert(
      !SearchFixtures
        .treeRowsQuery(SearchFixtures.AggregateUnit("sources"), Filter(period = Some(Period(Year(-1600), Year(-1200)))))
        .sqlString
        .contains("fp.matched > 0")
    )

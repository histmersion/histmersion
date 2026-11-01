package com.histmersion.db

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Tree.TreeRow
import com.histmersion.values.{Confidence, PlaceKind, SourceStatus}

/** [[TreeRepo]] against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise.
  *
  * The fixture is the smallest gazetteer that exercises everything the pure [[Tree]] tests cannot: **two OVERLAPPING
  * polity polygons** under one parent region, so a finding in the overlap is materialised into both (non-additive
  * counts), a period on each polity so the as-of walk has something to drop, and **one finding nowhere at all** so the
  * `unlocated` leaf has an occupant.
  */
class TreeRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""

  //  zzt_world ── zzt_land ─┬─ zzt_north  (-1650…-1180)  lon 171…175
  //                         └─ zzt_south  (-1500…-1300)  lon 174…178   ← overlap: lon 174…175
  //
  // Deliberately in an EMPTY corner of the South Pacific: the db suites share one test database, and
  // a fixture polygon drawn over Anatolia silently changes what `PlaceRepoSuite` (and any seeded
  // gazetteer) materialises. Keep new fixture geometry away from real places.
  private val batch = List(
    PlaceInput(Place("zzt_world", "Zzt World", PlaceKind.Region), List(PlaceName("Zzt World"))),
    PlaceInput(
      Place("zzt_land", "Zzt Land", PlaceKind.Region, Some(box(170, -50, 179, -40))),
      List(PlaceName("Zzt Land")),
      List(PlaceRelation("zzt_land", "zzt_world"))
    ),
    PlaceInput(
      Place(
        "zzt_north",
        "Zzt North",
        PlaceKind.Polity,
        Some(box(171, -48, 175, -44)),
        Some(Period(-1650, -1180)),
        Some("test fixture")
      ),
      List(PlaceName("Zzt North")),
      List(PlaceRelation("zzt_north", "zzt_land", PlaceRelation.PartOf, Some(Period(-1650, -1180))))
    ),
    PlaceInput(
      Place(
        "zzt_south",
        "Zzt South",
        PlaceKind.Polity,
        Some(box(174, -47, 178, -44)),
        Some(Period(-1500, -1300)),
        Some("test fixture")
      ),
      List(PlaceName("Zzt South")),
      List(PlaceRelation("zzt_south", "zzt_land", PlaceRelation.PartOf, Some(Period(-1500, -1300))))
    )
  )

  private val Tag = "zzt-tree-fixture"

  private val src = Source(
    citekey = "zzt_src",
    title = "Tree fixture",
    authors = List("T"),
    doi = None,
    year = Some(2021),
    venue = None,
    status = SourceStatus.Current,
    topics = Nil,
    regions = Nil,
    tags = List(Tag),
    period = Some(Period(-1400, -1350)),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      // in the OVERLAP of the two polities (and in the region containing both)
      Finding(
        "zzt_src#0",
        "zzt_src",
        "Frontier hoard",
        Some("Frontier"),
        Some(GeoPoint(174.5, -45.0)),
        Some(Period(-1400, -1350)),
        None,
        Nil,
        Some(Confidence.High)
      ),
      // in the northern polity only
      Finding(
        "zzt_src#1",
        "zzt_src",
        "Capital archive",
        Some("Capital"),
        Some(GeoPoint(172.0, -45.0)),
        Some(Period(-1400, -1350)),
        None,
        Nil,
        None
      ),
      // nowhere: mid-Atlantic, no place covers it
      Finding(
        "zzt_src#2",
        "zzt_src",
        "Atlantic core",
        None,
        Some(GeoPoint(-30.0, 45.0)),
        Some(Period(-1400, -1350)),
        None,
        Nil,
        None
      )
    ),
    edges = Nil
  )

  private def seed(): Unit =
    val _ = PlaceFixtures.upsertPlaces(ds, batch)
    SourceRepo.upsert(ds, src, cfg.limits.chunkChars)
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)

  /** Only the fixture: the tag rides on the SOURCE, and the finding-level tag predicate unions
    * `findings.tags ∪ sources.tags`, so every one of its findings matches and nothing else does.
    */
  private val filter = Filter(tags = List(Tag), period = Some(Period(-1600, -1200)))

  private def find(bs: List[Bucket], key: String): Option[Bucket] =
    bs.iterator.map(b => if b.key == key then Some(b) else find(b.children, key)).collectFirst { case Some(b) =>
      b
    }

  // ── the rows ─────────────────────────────────────────────────────────────────────────────────

  test("rows(findings): one row per finding, with the places it was materialised into"):
    seed()
    val rows = AggregateRepo.treeRows(ds, "findings", filter).toList
    assertEquals(rows.map(_.key).sorted, List("zzt_src#0", "zzt_src#1", "zzt_src#2"))
    def places(id: String) = rows.find(_.key == id).get.placeIds.filter(_.startsWith("zzt_")).toSet
    assertEquals(
      places("zzt_src#0"),
      Set("zzt_land", "zzt_north", "zzt_south"),
      "the overlap of two polities, plus the region containing both"
    )
    assertEquals(places("zzt_src#1"), Set("zzt_land", "zzt_north"))
    assertEquals(places("zzt_src#2"), Set.empty[String], "no place covers the mid-Atlantic")
    assertEquals(rows.find(_.key == "zzt_src#0").get.citekey, "zzt_src")
    assertEquals(rows.find(_.key == "zzt_src#0").get.title, "Tree fixture")
    assertEquals(rows.find(_.key == "zzt_src#0").get.start, Some(-1400L))
    assertEquals(rows.find(_.key == "zzt_src#0").get.end, Some(-1350L))

  test("rows(sources): one row per SOURCE, with the UNION of its matching findings' places"):
    seed()
    val rows = AggregateRepo.treeRows(ds, "sources", filter).toList
    assertEquals(rows.map(_.key), List("zzt_src"))
    assertEquals(rows.head.placeIds.filter(_.startsWith("zzt_")).toSet, Set("zzt_land", "zzt_north", "zzt_south"))
    assertEquals((rows.head.start, rows.head.end), (Some(-1400L), Some(-1350L)))

  test("rows agree with the histogram about which sources are in the window"):
    seed()
    val keys = AggregateRepo.treeRows(ds, "sources", filter).map(_.key).toSet
    val periods = AggregateRepo.periods(ds, "sources", filter)
    assertEquals(keys.size, periods.size, "the tree and the timeline strip select the same sources")

  test("a window that excludes the fixture returns no rows"):
    seed()
    assertEquals(AggregateRepo.treeRows(ds, "sources", filter.copy(period = Some(Period(-500, -200)))).toList, Nil)

  // ── the gazetteer slice ──────────────────────────────────────────────────────────────────────

  test("gazetteer() closes UPWARD over the hierarchy, whatever an edge's validity"):
    seed()
    val rows = AggregateRepo.treeRows(ds, "findings", filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    assert(ns.exists(_.id == "zzt_world"), "the ancestor nobody is materialised into is still needed")
    assertEquals(ns.find(_.id == "zzt_north").flatMap(_.period), Some(Period(-1650, -1180)))
    assertEquals(ns.find(_.id == "zzt_world").flatMap(_.period), None)
    assertEquals(ns.find(_.id == "zzt_land").map(_.kind), Some(PlaceKind.Region))
    assert(rels.exists(r => r.childId == "zzt_north" && r.parentId == "zzt_land"))
    assertEquals(rels.find(_.childId == "zzt_south").flatMap(_.period), Some(Period(-1500, -1300)))
    assert(rels.forall(_.kind == PlaceRelation.PartOf))

  test("gazetteer() runs no query at all when nothing is located"):
    assertEquals(PlaceFixtures.gazetteer(ds, Vector(TreeRow("x", "x", "X"))), (Nil, Nil))

  // ── repo + pure tree, end to end ─────────────────────────────────────────────────────────────

  test("the region tree over real rows: non-additive counts, overlap, and an unlocated leaf"):
    seed()
    val rows = AggregateRepo.treeRows(ds, "findings", filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    val tree = Tree.regionTree(rows, ns, rels, Some(-1400), 3, 2)

    assertEquals(find(tree, "zzt_world").map(_.count), Some(2), "2 of the 3 findings are located")
    assertEquals(find(tree, "zzt_land").map(_.count), Some(2))
    assertEquals(find(tree, "zzt_north").map(_.count), Some(2))
    assertEquals(find(tree, "zzt_south").map(_.count), Some(1))
    assertEquals(find(tree, "zzt_land").map(_.overlap), Some(1), "the frontier hoard is inside BOTH polities")
    assertEquals(
      find(tree, "zzt_land").map(_.children.map(_.count).sum),
      Some(3),
      "children sum to 3 while the parent holds 2 — counts are non-additive"
    )
    assertEquals(find(tree, Tree.KeyUnlocated).map(_.count), Some(1))
    assertEquals(find(tree, "zzt_north").map(_.samples.map(_.citekey)), Some(List("zzt_src")))

  test("as of -1250 the southern polity is gone, so the frontier hoard sits under the northern one alone"):
    seed()
    val rows = AggregateRepo.treeRows(ds, "findings", filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    val tree = Tree.regionTree(rows, ns, rels, Some(-1250), 3)
    assertEquals(find(tree, "zzt_south"), None)
    assertEquals(find(tree, "zzt_north").map(_.count), Some(2))
    assertEquals(find(tree, "zzt_land").map(_.overlap), Some(0))

  test("by=time,region over real rows: the gazetteer inside each time bucket"):
    seed()
    val rows = AggregateRepo.treeRows(ds, "sources", filter).toList
    val (ns, rels) = PlaceFixtures.gazetteer(ds, rows)
    val tree = Tree.timeRegionTree(rows, -1600, -1200, ns, rels, 2, 1)
    val bucket = tree.find(_.start.contains(-1400L)).get
    assertEquals(bucket.kind, Tree.KindTime)
    assertEquals(bucket.count, 1)
    assert(bucket.children.map(_.key).contains("zzt_world"), bucket.children.map(_.key).toString)
    assertEquals(bucket.children.find(_.key == "zzt_world").map(_.children.map(_.key)), Some(List("zzt_land")))

  // ── the SQL itself, without a database ───────────────────────────────────────────────────────

  test("rowsQuery threads the PlaceScope through, so a place chip narrows the tree"):
    val scope = PlaceScope(Map("zzt_north" -> List("zzt_north", "zzt_land")))
    val sql = AggregateRepo.treeRowsQuery("findings", Filter(places = List("zzt_north")), scope)
    assert(sql.text.contains("finding_places"), sql.text)
    assert(sql.params.exists(_ == Param.A(List("zzt_north", "zzt_land"))), sql.params.toString)

  test("rowsQuery(sources) gates on a matching finding exactly as the histogram does"):
    val f = Filter(places = List("anywhere"), period = Some(Period(-1600, -1200)))
    assert(AggregateRepo.treeRowsQuery("sources", f).text.contains("fp.matched > 0"))
    assert(
      !AggregateRepo
        .treeRowsQuery("sources", Filter(period = Some(Period(-1600, -1200))))
        .text
        .contains("fp.matched > 0")
    )

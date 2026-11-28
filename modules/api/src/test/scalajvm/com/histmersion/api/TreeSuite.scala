package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.Tree

import com.histmersion.api.library.SearchMarkdown

import com.histmersion.api.library.Tree.TreeRow
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.{Bucket, Citekey, TimeHistogram, Title}
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Place.Relation.{ChildId, ParentId}
import com.histmersion.lint.{tolerated, LintRule}

/** The §1.6 / §1.6a nesting, purely: adaptive time levels, the as-of hierarchy walk, non-additive counts with their
  * `overlap`, the `unlocated` leaf, samples and the depth clamp. No database.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class TreeSuite extends munit.FunSuite:

  // ── a tiny gazetteer, exactly the §1.6a acceptance shape ────────────────────────────────────
  //   asia ─┬─ anatolia ─┬─ hatti    (-1650…-1180) ─┬─ hattusa   (edge -1650…-1180)
  //         │            └─ phrygia  (-1200… -700) ─┘           (edge -1200… -700)
  //         └─ near_east ── mitanni  (-1500…-1300)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val asia = RegionNode(RegionNode.Id("asia"), RegionNode.Label("Asia"), RegionNode.Kind(Place.Kind.Region))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val anatolia =
    RegionNode(RegionNode.Id("anatolia"), RegionNode.Label("Anatolia"), RegionNode.Kind(Place.Kind.Region))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val nearEast =
    RegionNode(RegionNode.Id("near_east"), RegionNode.Label("Near East"), RegionNode.Kind(Place.Kind.Region))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val hatti = RegionNode(
    RegionNode.Id("hatti"),
    RegionNode.Label("Hittite Empire"),
    RegionNode.Kind(Place.Kind.Polity),
    Some(Period(Year(-1650), Year(-1180)))
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val phrygia = RegionNode(
    RegionNode.Id("phrygia"),
    RegionNode.Label("Phrygia"),
    RegionNode.Kind(Place.Kind.Polity),
    Some(Period(Year(-1200), Year(-700)))
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val mitanni =
    RegionNode(
      RegionNode.Id("mitanni"),
      RegionNode.Label("Mitanni"),
      RegionNode.Kind(Place.Kind.Polity),
      Some(Period(Year(-1500), Year(-1300))),
      contested = RegionNode.Contested.Contested
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val hattusa =
    RegionNode(RegionNode.Id("hattusa"), RegionNode.Label("Hattusa"), RegionNode.Kind(Place.Kind.Site))

  private val nodes = List(asia, anatolia, nearEast, hatti, phrygia, mitanni, hattusa)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def part(child: ChildId, parent: ParentId, p: Option[Period] = None) =
    Place.Relation(child, parent, Place.Relation.Kind(Place.Relation.PartOf), p)

  private val relations = List(
    part(ChildId("anatolia"), ParentId("asia")),
    part(ChildId("near_east"), ParentId("asia")),
    part(ChildId("hatti"), ParentId("anatolia"), Some(Period(Year(-1650), Year(-1180)))),
    part(ChildId("phrygia"), ParentId("anatolia"), Some(Period(Year(-1200), Year(-700)))),
    part(ChildId("mitanni"), ParentId("near_east"), Some(Period(Year(-1500), Year(-1300)))),
    // the SAME site, under a different polity depending on when you look
    part(ChildId("hattusa"), ParentId("hatti"), Some(Period(Year(-1650), Year(-1180)))),
    part(ChildId("hattusa"), ParentId("phrygia"), Some(Period(Year(-1200), Year(-700))))
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def row(key: TreeRow.Key, start: Year, end: Year, places: RegionNode.Id*) =
    TreeRow(key, Citekey(key.value), Title(s"Title of ${key.value}"), Some(start), Some(end), places.toList)

  /** A Hattusa dig dated −1400; materialised into the site AND the polity that covered it then. */
  private val dig = row(TreeRow.Key("dig"), Year(-1400), Year(-1400), RegionNode.Id("hattusa"), RegionNode.Id("hatti"))

  /** A find on the Hittite/Mitanni frontier: inside two polities under two different macro-regions. */
  private val frontier =
    row(TreeRow.Key("frontier"), Year(-1400), Year(-1350), RegionNode.Id("hatti"), RegionNode.Id("mitanni"))

  /** No gazetteer place at all. */
  private val floating = row(TreeRow.Key("floating"), Year(-1400), Year(-1300))

  private def find(bs: List[Bucket], key: Bucket.Key): Option[Bucket] =
    bs.iterator.map(b => if b.key == key then Some(b) else find(b.children, key)).collectFirst { case Some(b) =>
      b
    }
  private def path(bs: List[Bucket], keys: Bucket.Key*): Option[Bucket] =
    keys.toList match
      case Nil     => None
      case k :: ks => bs.find(_.key == k).flatMap(b => if ks.isEmpty then Some(b) else path(b.children, ks*))

  // ── the time axis ────────────────────────────────────────────────────────────────────────────

  test("timeTree buckets at the adaptive size and nests at the next finer one"):
    // a 3000-year window buckets at 100 (Aggregate.bucketSize), and nests at 10.
    // Two sources, because a finer level is only drawn when it SEPARATES them (Tree.splitWorthShowing).
    val t = Tree.timeTree(
      List(row(TreeRow.Key("a"), Year(-1550), Year(-1450)), row(TreeRow.Key("b"), Year(-1520), Year(-1511))),
      Tree.TimeTreeFrom(-3500),
      Tree.TimeTreeTo(-500),
      Tree.TimeTreeDepth(2)
    )
    assertEquals(t.map(_.key), List("time:-1600:-1501", "time:-1500:-1401"))
    assertEquals(t.head.start, Some(-1600L))
    assertEquals(t.head.end, Some(-1501L))
    assertEquals(t.head.kind, Tree.KindTime)
    assertEquals(t.head.label, "1600 BCE–1501 BCE")
    // 400-year span → 100-year buckets, children at 10
    assertEquals(t.head.children.map(_.key).take(2), List("time:-1550:-1541", "time:-1540:-1531"))

  test("a unit is counted in EVERY bucket it overlaps, so counts do not sum"):
    val rows =
      List(row(TreeRow.Key("long"), Year(-1600), Year(-1200)), row(TreeRow.Key("short"), Year(-1250), Year(-1240)))
    val t = Tree.timeTree(rows, Tree.TimeTreeFrom(-3500), Tree.TimeTreeTo(-500), Tree.TimeTreeDepth(1))
    assertEquals(t.map(_.count), List(1, 1, 1, 2, 1))
    assertEquals(t.map(_.count.value).sum, 6, "6 > the 2 distinct sources — non-additive by design")

  test("empty buckets are dropped from the tree (the strip keeps its gaps, a tree row of 0 is noise)"):
    val t = Tree.timeTree(
      List(row(TreeRow.Key("a"), Year(-1550), Year(-1540))),
      Tree.TimeTreeFrom(-3500),
      Tree.TimeTreeTo(-500),
      Tree.TimeTreeDepth(1)
    )
    assertEquals(t.map(_.key), List("time:-1600:-1501"))

  test("undated units never appear in a time level"):
    assertEquals(
      Tree.timeTree(
        List(TreeRow(Tree.TreeRow.Key("u"), Citekey("u"), Title("U"))),
        Tree.TimeTreeFrom(-3500),
        Tree.TimeTreeTo(-500),
        Tree.TimeTreeDepth(2)
      ),
      Nil
    )

  test("depth is clamped to 1..MaxDepth"):
    def levels(b: Bucket): Int = 1 + b.children.map(levels).maxOption.getOrElse(0)
    // an 11 500-year window buckets at 1000, so there are three sizes to nest through: 1000/100/10.
    // Sources spread so that each level genuinely splits its parent — otherwise the level is pruned
    // as uninformative (item 7), which the next test covers.
    val rows = List(
      row(TreeRow.Key("a"), Year(-1550), Year(-1541)),
      row(TreeRow.Key("b"), Year(-1520), Year(-1511)),
      row(TreeRow.Key("c"), Year(-1050), Year(-1041))
    )
    assertEquals(
      levels(Tree.timeTree(rows, Tree.TimeTreeFrom(-12000), Tree.TimeTreeTo(-500), Tree.TimeTreeDepth(0)).head),
      1,
      "depth 0 → one level"
    )
    assertEquals(
      levels(Tree.timeTree(rows, Tree.TimeTreeFrom(-12000), Tree.TimeTreeTo(-500), Tree.TimeTreeDepth(99)).head),
      Tree.MaxDepth
    )
    // …and when the finest size is already reached there is simply nothing to nest
    assertEquals(
      levels(
        Tree
          .timeTree(
            List(row(TreeRow.Key("a"), Year(-1550), Year(-1540))),
            Tree.TimeTreeFrom(-1600),
            Tree.TimeTreeTo(-1200),
            Tree.TimeTreeDepth(99)
          )
          .head
      ),
      1,
      "a 400-yr window IS 10-yr buckets"
    )

  test("samples land on the LEAVES only, deduplicated by citekey, in row order"):
    // the two sources sit in different 10-yr buckets, so the finer level is worth drawing (item 7)
    val t = Tree.timeTree(
      List(row(TreeRow.Key("a"), Year(-1550), Year(-1541)), row(TreeRow.Key("b"), Year(-1530), Year(-1521))),
      Tree.TimeTreeFrom(-3500),
      Tree.TimeTreeTo(-500),
      Tree.TimeTreeDepth(2),
      Tree.TimeTreeSamples(5)
    )
    assertEquals(t.head.samples, Nil, "an inner node lists no samples")
    val leaf = t.head.children.head
    assertEquals(leaf.samples.map(_.citekey), List("a"))
    assertEquals(leaf.samples.head.title, "Title of a")
    assertEquals(
      Tree
        .timeTree(
          List(row(TreeRow.Key("a"), Year(-1550), Year(-1540))),
          Tree.TimeTreeFrom(-3500),
          Tree.TimeTreeTo(-500),
          Tree.TimeTreeDepth(1),
          Tree.TimeTreeSamples(1)
        )
        .head
        .samples
        .size,
      1
    )
    assertEquals(
      Tree
        .timeTree(
          List(row(TreeRow.Key("a"), Year(-1550), Year(-1540))),
          Tree.TimeTreeFrom(-3500),
          Tree.TimeTreeTo(-500),
          Tree.TimeTreeDepth(1),
          Tree.TimeTreeSamples(0)
        )
        .head
        .samples,
      Nil
    )

  test("finer TIME children never report an overlap (spanning two buckets is the normal case)"):
    val t = Tree.timeTree(
      List(row(TreeRow.Key("a"), Year(-1550), Year(-1450))),
      Tree.TimeTreeFrom(-12000),
      Tree.TimeTreeTo(-500),
      Tree.TimeTreeDepth(3)
    )
    assert(t.forall(_.overlap.value == 0))

  // ── the region axis ──────────────────────────────────────────────────────────────────────────

  test("§1.6a: Hattusa at -1400 sits under Hittite Empire → Anatolia → Asia"):
    val t = Tree.regionTree(List(dig), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(t.map(_.key), List("asia"))
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).map(_.label),
      Some("Hittite Empire")
    )
    assertEquals(path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).map(_.count), Some(1))
    assertEquals(path(t, Bucket.Key("asia")).map(_.count), Some(1))
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).flatMap(_.placeId),
      Some("hatti")
    )
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("phrygia")),
      None,
      "Phrygia does not exist yet at -1400"
    )

  test("§1.6a: the SAME source sits under Phrygia → Anatolia → Asia at -700"):
    val t = Tree.regionTree(List(dig), nodes, relations, Some(Year(-700)), Tree.RegionTreeDepth(3))
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("phrygia")).map(_.label),
      Some("Phrygia")
    )
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")),
      None,
      "the Hittite Empire is not valid at -700, so its direct assignment is dropped too"
    )

  test("§1.6a: a frontier find counts once at the macro level and once under EACH polity"):
    val t = Tree.regionTree(List(frontier), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(path(t, Bucket.Key("asia")).map(_.count), Some(1), "one source, counted once at the top")
    assertEquals(path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).map(_.count), Some(1))
    assertEquals(path(t, Bucket.Key("asia"), Bucket.Key("near_east"), Bucket.Key("mitanni")).map(_.count), Some(1))
    assertEquals(
      path(t, Bucket.Key("asia")).map(_.overlap),
      Some(1),
      "1 of Asia's sources is in more than one of its subregions"
    )
    assertEquals(path(t, Bucket.Key("asia"), Bucket.Key("anatolia")).map(_.overlap), Some(0))

  test("counts are distinct units in the whole subtree, never the sum of the children"):
    val t = Tree.regionTree(List(dig, frontier), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(path(t, Bucket.Key("asia")).map(_.count), Some(2))
    assertEquals(
      path(t, Bucket.Key("asia")).map(_.children.map(_.count.value).sum),
      Some(3),
      "2 sources, 3 in children"
    )

  test("units with no place — or none valid as of the query — land in the trailing unlocated leaf"):
    val t = Tree.regionTree(List(dig, floating), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(t.last.key, Tree.KeyUnlocated)
    assertEquals(t.last.count, 1)
    assertEquals(t.last.placeId, None)
    assertEquals(t.last.kind, Tree.KindRegion)
    // the frontier find is only ever inside two polities; at -700 neither exists any more, so it
    // joins the source that never had a place at all
    val gone = Tree.regionTree(List(frontier, floating), nodes, relations, Some(Year(-700)), Tree.RegionTreeDepth(3))
    assertEquals(gone.map(_.key), List(Tree.KeyUnlocated))
    assertEquals(gone.head.count, 2)

  test("everything is unlocated when the gazetteer is empty (a store with no places still answers)"):
    val t = Tree.regionTree(List(dig), Nil, Nil, Some(Year(-1400)), Tree.RegionTreeDepth(2))
    assertEquals(t.map(b => (b.key, b.count)), List((Tree.KeyUnlocated, 1)))

  test("a node no unit reaches is dropped, and children sort by count then label"):
    val t = Tree.regionTree(List(dig, frontier), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(2))
    assertEquals(path(t, Bucket.Key("asia")).map(_.children.map(_.key)), Some(List("anatolia", "near_east")))

  test("truncating at depth does not change what overlap means"):
    val deep = Tree.regionTree(List(frontier), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    val shallow = Tree.regionTree(List(frontier), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(1))
    assertEquals(shallow.head.children, Nil)
    assertEquals(shallow.head.overlap, deep.head.overlap)

  test("a place's own validity is reported as the bucket's period; contested is carried through"):
    val t = Tree.regionTree(List(frontier), nodes, relations, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("near_east"), Bucket.Key("mitanni")).map(b => (b.start, b.end)),
      Some((Some(-1500L), Some(-1300L)))
    )
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("near_east"), Bucket.Key("mitanni")).map(_.contested.contested),
      Some(true)
    )
    assertEquals(path(t, Bucket.Key("asia")).map(b => (b.start, b.end)), Some((None, None)))

  test("a cyclic hierarchy terminates instead of overflowing the stack"):
    val cyc = relations :+ part(ChildId("anatolia"), ParentId("hatti"), Some(Period(Year(-1650), Year(-1180))))
    val t = Tree.regionTree(List(dig), nodes, cyc, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).map(_.count), Some(1))
    assertEquals(
      path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti"), Bucket.Key("anatolia")),
      None,
      "the cycle is cut, not walked"
    )

  test("an explicit root returns THAT node's subtree, which is how a client expands lazily"):
    val t = Tree.regionTree(
      List(dig, frontier),
      nodes,
      relations,
      Some(Year(-1400)),
      Tree.RegionTreeDepth(2),
      Tree.RegionTreeSamples(0),
      Some(RegionNode.Id("anatolia"))
    )
    assertEquals(t.map(_.key), List("anatolia"), "rooted at the node, not at the continent")
    assertEquals(t.head.count, 2)
    assertEquals(t.head.children.map(_.key), List("hatti"))
    // a root that is not valid as of the query falls back to the ordinary forest
    assertEquals(
      Tree
        .regionTree(
          List(dig),
          nodes,
          relations,
          Some(Year(-700)),
          Tree.RegionTreeDepth(2),
          Tree.RegionTreeSamples(0),
          Some(RegionNode.Id("hatti"))
        )
        .map(_.key),
      List("asia")
    )

  test("a parentless SITE is not a top level (Pleiades has ~16 000 of them, all parentless)"):
    // as of -1700 neither polity exists, so Hattusa has no valid parent — it must NOT become a root
    val t = Tree.regionTree(List(dig), nodes, relations, Some(Year(-1700)), Tree.RegionTreeDepth(3))
    assertEquals(t.map(_.key), List(Tree.KeyUnlocated))
    // a parentless REGION or POLITY still shows: it is a gazetteer top level, not a gap
    val loose = RegionNode(RegionNode.Id("loose"), RegionNode.Label("Loose Region"), RegionNode.Kind(Place.Kind.Region))
    val t2 = Tree.regionTree(
      List(row(TreeRow.Key("x"), Year(-1400), Year(-1400), RegionNode.Id("loose"))),
      List(loose),
      Nil,
      Some(Year(-1400)),
      Tree.RegionTreeDepth(2)
    )
    assertEquals(t2.map(b => (b.key, b.count)), List(("loose", 1)))

  test("a gazetteer with NO root at all still reports its units instead of losing them"):
    // every node has a parent, so nothing is reachable from a root — the units must not vanish
    val cyc = relations ++ List(part(ChildId("asia"), ParentId("hatti"), Some(Period(Year(-1650), Year(-1180)))))
    val t = Tree.regionTree(List(dig), nodes, cyc, Some(Year(-1400)), Tree.RegionTreeDepth(3))
    assertEquals(t.map(b => (b.key, b.count)), List((Tree.KeyUnlocated, 1)))

  test("asOf = None reads every edge, whatever its validity"):
    val t = Tree.regionTree(List(dig), nodes, relations, None, Tree.RegionTreeDepth(3))
    assert(path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).isDefined)
    assert(path(t, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("phrygia")).isDefined)

  // ── the two axes together ────────────────────────────────────────────────────────────────────

  test("timeRegionTree reads the gazetteer at EACH bucket's own midpoint"):
    // one source in the LBA, one in the Iron Age, both at Hattusa
    val lba = row(TreeRow.Key("lba"), Year(-1400), Year(-1400), RegionNode.Id("hattusa"))
    val iron = row(TreeRow.Key("iron"), Year(-800), Year(-800), RegionNode.Id("hattusa"))
    val t = Tree.timeRegionTree(List(lba, iron), Year(-1600), Year(-600), nodes, relations, Tree.TimeRegionTreeDepth(3))
    val early = t.find(_.start.contains(Year(-1400))).get
    val late = t.find(_.start.contains(Year(-800))).get
    assertEquals(Tree.midpoint(Year(-1400), Year(-1301)), Year(-1351))
    assertEquals(early.kind, Tree.KindTime)
    assert(
      path(early.children, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("hatti")).isDefined,
      "LBA bucket → Hittite Empire"
    )
    assert(
      path(late.children, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("phrygia")).isDefined,
      "Iron-Age bucket → Phrygia"
    )
    assert(path(early.children, Bucket.Key("asia"), Bucket.Key("anatolia"), Bucket.Key("phrygia")).isEmpty)

  test("timeRegionTree keeps the time level flat: exactly one level of buckets, then regions"):
    val t = Tree.timeRegionTree(List(dig), Year(-1600), Year(-1200), nodes, relations, Tree.TimeRegionTreeDepth(2))
    assertEquals(t.size, 1)
    assert(t.head.children.forall(_.kind.value == Tree.KindRegion))
    assertEquals(t.head.children.head.key, "asia")

  test("regionTimeTree hangs time buckets under each region LEAF"):
    val t = Tree.regionTimeTree(
      List(dig),
      Year(-1600),
      Year(-1200),
      nodes,
      relations,
      Some(Year(-1400)),
      Tree.RegionTimeTreeDepth(2),
      Tree.RegionTimeTreeSamples(2)
    )
    val leaf = path(t, Bucket.Key("asia"), Bucket.Key("anatolia")).get
    assertEquals(leaf.children.map(_.kind).distinct, List(Tree.KindTime))
    assertEquals(leaf.children.head.samples.map(_.citekey), List("dig"))

  test("the unlocated leaf survives both combined axes"):
    val t = Tree.timeRegionTree(List(floating), Year(-1600), Year(-1200), nodes, relations, Tree.TimeRegionTreeDepth(2))
    assertEquals(t.head.children.map(_.key), List(Tree.KeyUnlocated))

  // ── the `by` vocabulary + the wire shape ─────────────────────────────────────────────────────

  test("parseBy accepts exactly the four axes the plan names"):
    assertEquals(Tree.parseBy("time"), Some(List("time")))
    assertEquals(Tree.parseBy("region"), Some(List("region")))
    assertEquals(Tree.parseBy(" Time , Region "), Some(List("time", "region")))
    assertEquals(Tree.parseBy("region,time"), Some(List("region", "time")))
    assertEquals(Tree.parseBy("time,time"), None, "an axis cannot repeat")
    assertEquals(Tree.parseBy("author"), None)
    assertEquals(Tree.parseBy(""), None)

  test("coversAsOf: a missing period means always, a missing asOf means whenever"):
    assert(Tree.coversAsOf(None, Some(Year(-1400))))
    assert(Tree.coversAsOf(Some(Period(Year(-1650), Year(-1180))), None))
    assert(Tree.coversAsOf(Some(Period(Year(-1650), Year(-1180))), Some(Year(-1650))), "bounds are inclusive")
    assert(Tree.coversAsOf(Some(Period(Year(-1650), Year(-1180))), Some(Year(-1180))))
    assert(!Tree.coversAsOf(Some(Period(Year(-1650), Year(-1180))), Some(Year(-1179))))

  test("midpoint rounds down, so a bucket always reads the gazetteer at the same year"):
    assertEquals(Tree.midpoint(Year(-1600), Year(-1501)), Year(-1551))
    assertEquals(Tree.midpoint(Year(-1600), Year(-1200)), Year(-1400))
    assertEquals(Tree.midpoint(Year(-10), Year(-10)), Year(-10))

  test("the Bucket tree round-trips through JSON and has a tapir schema (recursion included)"):
    val b = Tree
      .regionTree(
        List(dig, floating),
        nodes,
        relations,
        Some(Year(-1400)),
        Tree.RegionTreeDepth(3),
        Tree.RegionTreeSamples(2)
      )
      .head
    val js = (b).asJson.noSpaces
    assert(js.contains("Hittite Empire"), js)
    assertEquals(RawJson.decode[Bucket](js), Right(b))
    assert(Bucket.schema.toString.nonEmpty)

  test("SearchMarkdown.tree renders the indented tree with the non-additive caveat"):
    val t = Tree.regionTree(
      List(dig, frontier, floating),
      nodes,
      relations,
      Some(Year(-1400)),
      Tree.RegionTreeDepth(3),
      Tree.RegionTreeSamples(1)
    )
    val md =
      SearchMarkdown.tree(
        TimeHistogram(
          TimeHistogram.From(-1600),
          TimeHistogram.To(-1200),
          TimeHistogram.Bucket(100),
          Nil,
          by = TimeHistogram.By("region"),
          total = TimeHistogram.Total(3),
          depth = TimeHistogram.Depth(3),
          tree = t
        ),
        SearchMarkdown.TreeUnit("sources")
      )
    assert(md.contains("### sources by region"), md)
    assert(md.contains("- **Asia** `asia` — 2 sources · 1 in more than one subregion"), md)
    assert(md.contains("    - **Hittite Empire** `hatti` — 2 sources"), md)
    assert(md.contains("(contested)"), md)
    assert(md.contains("- **unlocated** — 1 sources"), md)
    assert(md.contains("non-additive"), md)

  test("SearchMarkdown.tree says so when there is nothing to group"):
    assert(
      SearchMarkdown
        .tree(
          TimeHistogram(
            TimeHistogram.From(-1600),
            TimeHistogram.To(-1200),
            TimeHistogram.Bucket(100),
            Nil,
            by = TimeHistogram.By("region")
          ),
          SearchMarkdown.TreeUnit("sources")
        )
        .contains("No sources to group")
    )

/** Root eligibility and the "is this split worth drawing" rule (§1.6, UX items 7 and 8). Pure. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class TreeRootsSuite extends munit.FunSuite:

  private def node(
      id: RegionNode.Id,
      label: RegionNode.Label,
      kind: RegionNode.Kind,
      withdrawn: RegionNode.Withdrawn = RegionNode.Withdrawn.Current
  ) =
    RegionNode(id, label, kind, None, RegionNode.Contested.Settled, withdrawn)

  test("only a region, polity or continent may head the tree"):
    assert(
      RegionNode.rootEligible(node(RegionNode.Id("a"), RegionNode.Label("Asia"), RegionNode.Kind(Place.Kind.Region)))
    )
    assert(
      RegionNode.rootEligible(node(RegionNode.Id("b"), RegionNode.Label("Akkad"), RegionNode.Kind(Place.Kind.Polity)))
    )
    assert(RegionNode.rootEligible(node(RegionNode.Id("c"), RegionNode.Label("Europe"), RegionNode.Kind("continent"))))
    assert(
      !RegionNode.rootEligible(
        node(RegionNode.Id("d"), RegionNode.Label("Asopos (river)"), RegionNode.Kind(Place.Kind.Feature))
      )
    )
    assert(
      !RegionNode.rootEligible(node(RegionNode.Id("e"), RegionNode.Label("Hattusa"), RegionNode.Kind(Place.Kind.Site)))
    )

  test("a withdrawn Pleiades record is never a root, however it is marked"):
    assert(
      !RegionNode.rootEligible(
        node(
          RegionNode.Id("f"),
          RegionNode.Label("Mount Juktas"),
          RegionNode.Kind(Place.Kind.Region),
          withdrawn = RegionNode.Withdrawn.Withdrawn
        )
      )
    )
    assert(
      !RegionNode.rootEligible(
        node(
          RegionNode.Id("g"),
          RegionNode.Label("Withdrawn (duplicate): Creta et Cyrene"),
          RegionNode.Kind(Place.Kind.Region)
        )
      )
    )

  test("an uncertain or reconstructed name is never a root"):
    assert(RegionNode.uncertainLabel(RegionNode.Label("Theren? (river)")))
    assert(RegionNode.uncertainLabel(RegionNode.Label("*Granouas (river)")))
    assert(!RegionNode.uncertainLabel(RegionNode.Label("Anatolia")))
    assert(
      !RegionNode.rootEligible(
        node(RegionNode.Id("h"), RegionNode.Label("Kisamon? Pr."), RegionNode.Kind(Place.Kind.Region))
      )
    )

  test("ineligible nodes are dropped from the ROOTS, not from the tree"):
    val rows = List(
      Tree.TreeRow(
        Tree.TreeRow.Key("s1"),
        Citekey("s1"),
        Title("S1"),
        Some(Year(-1500)),
        Some(Year(-1400)),
        List(RegionNode.Id("river"))
      )
    )
    val nodes = List(
      node(RegionNode.Id("asia"), RegionNode.Label("Asia"), RegionNode.Kind(Place.Kind.Region)),
      node(RegionNode.Id("river"), RegionNode.Label("Asopos (river)"), RegionNode.Kind(Place.Kind.Feature))
    )
    val rels = List(
      Place.Relation(ChildId("river"), ParentId("asia"), Place.Relation.Kind(Place.Relation.PartOf), None)
    )
    val tree = Tree.regionTree(rows, nodes, rels, None, depth = Tree.RegionTreeDepth(2))
    assertEquals(tree.map(_.label), List("Asia"))
    assertEquals(tree.head.children.map(_.label), List("Asopos (river)"))

  test("a place with two parents is drawn once, under the bigger, and says where else it sits"):
    val rows = List(
      Tree.TreeRow(
        Tree.TreeRow.Key("s1"),
        Citekey("s1"),
        Title("S1"),
        Some(Year(-1500)),
        Some(Year(-1400)),
        List(RegionNode.Id("anatolia"))
      ),
      Tree.TreeRow(
        Tree.TreeRow.Key("s2"),
        Citekey("s2"),
        Title("S2"),
        Some(Year(-1500)),
        Some(Year(-1400)),
        List(RegionNode.Id("asia"))
      )
    )
    val nodes =
      List(
        node(RegionNode.Id("asia"), RegionNode.Label("Asia"), RegionNode.Kind(Place.Kind.Region)),
        node(RegionNode.Id("europe"), RegionNode.Label("Europe"), RegionNode.Kind(Place.Kind.Region)),
        node(RegionNode.Id("anatolia"), RegionNode.Label("Anatolia"), RegionNode.Kind(Place.Kind.Region))
      )
    val rels = List(
      Place.Relation(ChildId("anatolia"), ParentId("asia"), Place.Relation.Kind(Place.Relation.PartOf), None),
      Place.Relation(ChildId("anatolia"), ParentId("europe"), Place.Relation.Kind(Place.Relation.PartOf), None)
    )
    val tree = Tree.regionTree(rows, nodes, rels, None, depth = Tree.RegionTreeDepth(2))
    val under = tree.flatMap(b => b.children.map(c => (b.label, c.label, c.alsoUnder)))
    assertEquals(under.size, 1, s"Anatolia drawn ${under.size} times: $under")
    assertEquals(under.head._1, "Asia") // the parent with the larger count
    assertEquals(under.head._3, List("Europe"))

  // ── item 7: finer time levels that say nothing are not drawn ────────────────────────────────
  test("a split is kept only when a child is materially smaller than the parent"):
    def b(n: Int) = Bucket(
      label = Bucket.Label("x"),
      key = Bucket.Key("k" + n),
      kind = Bucket.Kind(Tree.KindTime),
      count = Bucket.Count(n)
    )
    assertEquals(Tree.splitWorthShowing(Bucket.Count(100), List(b(99), b(98))), Nil, "all children ≈ the parent")
    assertEquals(Tree.splitWorthShowing(Bucket.Count(100), List(b(99), b(20))).size, 2, "one child is much smaller")
    assertEquals(Tree.splitWorthShowing(Bucket.Count(100), List(b(10))), Nil, "a single child adds no information")

  test("a window of long-range sources does not nest into near-identical children"):
    // three sources each spanning the whole window: every finer bucket would carry all three
    val rows = (1 to 3)
      .map(i =>
        Tree.TreeRow(Tree.TreeRow.Key(s"s$i"), Citekey(s"s$i"), Title(s"S$i"), Some(Year(-3000)), Some(Year(-1000)))
      )
      .toList
    val tree = Tree.timeTree(
      rows,
      Tree.TimeTreeFrom(-3000),
      Tree.TimeTreeTo(-1000),
      depth = Tree.TimeTreeDepth(3),
      samples = Tree.TimeTreeSamples(2)
    )
    assert(tree.forall(_.children.isEmpty), tree.map(b => (b.label, b.children.size)).toString)
    assert(tree.forall(_.samples.nonEmpty), "a leaf shows its samples instead")

  test("a window whose sources really are in different centuries still nests"):
    // span 5 000 → 1 000-yr top buckets, 100-yr children; five sources spread across one millennium,
    // so every child holds one fifth of its parent — a split that genuinely separates the evidence
    val rows = (0 until 5).toList.map { i =>
      val s = -3000L + i * 200
      Tree.TreeRow(Tree.TreeRow.Key(s"s$i"), Citekey(s"s$i"), Title(s"S$i"), Some(Year(s)), Some(Year(s + 50)))
    }
    val tree = Tree.timeTree(
      rows,
      Tree.TimeTreeFrom(-6000),
      Tree.TimeTreeTo(-1000),
      depth = Tree.TimeTreeDepth(3),
      samples = Tree.TimeTreeSamples(0)
    )
    assert(tree.exists(_.children.nonEmpty), tree.map(b => (b.label, b.count, b.children.size)).toString)

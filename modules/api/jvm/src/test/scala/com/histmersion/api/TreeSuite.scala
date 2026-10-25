package com.histmersion.api


import com.histmersion.api.Tree.{RegionNode, TreeRow}
import com.histmersion.values.PlaceKind

/** The §1.6 / §1.6a nesting, purely: adaptive time levels, the as-of hierarchy walk, non-additive
  * counts with their `overlap`, the `unlocated` leaf, samples and the depth clamp. No database. */
class TreeSuite extends munit.FunSuite:

  // ── a tiny gazetteer, exactly the §1.6a acceptance shape ────────────────────────────────────
  //   asia ─┬─ anatolia ─┬─ hatti    (-1650…-1180) ─┬─ hattusa   (edge -1650…-1180)
  //         │            └─ phrygia  (-1200… -700) ─┘           (edge -1200… -700)
  //         └─ near_east ── mitanni  (-1500…-1300)
  private val asia     = RegionNode("asia", "Asia", PlaceKind.Region)
  private val anatolia = RegionNode("anatolia", "Anatolia", PlaceKind.Region)
  private val nearEast = RegionNode("near_east", "Near East", PlaceKind.Region)
  private val hatti    = RegionNode("hatti", "Hittite Empire", PlaceKind.Polity, Some(Period(-1650, -1180)))
  private val phrygia  = RegionNode("phrygia", "Phrygia", PlaceKind.Polity, Some(Period(-1200, -700)))
  private val mitanni  = RegionNode("mitanni", "Mitanni", PlaceKind.Polity, Some(Period(-1500, -1300)), contested = true)
  private val hattusa  = RegionNode("hattusa", "Hattusa", PlaceKind.Site)

  private val nodes = List(asia, anatolia, nearEast, hatti, phrygia, mitanni, hattusa)

  private def part(child: String, parent: String, p: Option[Period] = None) =
    PlaceRelation(child, parent, PlaceRelation.PartOf, p)

  private val relations = List(
    part("anatolia", "asia"),
    part("near_east", "asia"),
    part("hatti", "anatolia", Some(Period(-1650, -1180))),
    part("phrygia", "anatolia", Some(Period(-1200, -700))),
    part("mitanni", "near_east", Some(Period(-1500, -1300))),
    // the SAME site, under a different polity depending on when you look
    part("hattusa", "hatti", Some(Period(-1650, -1180))),
    part("hattusa", "phrygia", Some(Period(-1200, -700))))

  private def row(key: String, start: Long, end: Long, places: String*) =
    TreeRow(key, key, s"Title of $key", Some(start), Some(end), places.toList)

  /** A Hattusa dig dated −1400; materialised into the site AND the polity that covered it then. */
  private val dig      = row("dig", -1400, -1400, "hattusa", "hatti")
  /** A find on the Hittite/Mitanni frontier: inside two polities under two different macro-regions. */
  private val frontier = row("frontier", -1400, -1350, "hatti", "mitanni")
  /** No gazetteer place at all. */
  private val floating = row("floating", -1400, -1300)

  private def find(bs: List[Bucket], key: String): Option[Bucket] =
    bs.iterator.map(b => if b.key == key then Some(b) else find(b.children, key)).collectFirst {
      case Some(b) => b
    }
  private def path(bs: List[Bucket], keys: String*): Option[Bucket] =
    keys.toList match
      case Nil     => None
      case k :: ks => bs.find(_.key == k).flatMap(b => if ks.isEmpty then Some(b) else path(b.children, ks*))

  // ── the time axis ────────────────────────────────────────────────────────────────────────────

  test("timeTree buckets at the adaptive size and nests at the next finer one"):
    // a 3000-year window buckets at 100 (Aggregate.bucketSize), and nests at 10.
    // Two sources, because a finer level is only drawn when it SEPARATES them (Tree.splitWorthShowing).
    val t = Tree.timeTree(List(row("a", -1550, -1450), row("b", -1520, -1511)), -3500, -500, 2)
    assertEquals(t.map(_.key), List("time:-1600:-1501", "time:-1500:-1401"))
    assertEquals(t.head.start, Some(-1600L))
    assertEquals(t.head.end, Some(-1501L))
    assertEquals(t.head.kind, Tree.KindTime)
    assertEquals(t.head.label, "1600 BCE–1501 BCE")
    // 400-year span → 100-year buckets, children at 10
    assertEquals(t.head.children.map(_.key).take(2), List("time:-1550:-1541", "time:-1540:-1531"))

  test("a unit is counted in EVERY bucket it overlaps, so counts do not sum"):
    val rows = List(row("long", -1600, -1200), row("short", -1250, -1240))
    val t    = Tree.timeTree(rows, -3500, -500, 1)
    assertEquals(t.map(_.count), List(1, 1, 1, 2, 1))
    assertEquals(t.map(_.count).sum, 6, "6 > the 2 distinct sources — non-additive by design")

  test("empty buckets are dropped from the tree (the strip keeps its gaps, a tree row of 0 is noise)"):
    val t = Tree.timeTree(List(row("a", -1550, -1540)), -3500, -500, 1)
    assertEquals(t.map(_.key), List("time:-1600:-1501"))

  test("undated units never appear in a time level"):
    assertEquals(Tree.timeTree(List(TreeRow("u", "u", "U")), -3500, -500, 2), Nil)

  test("depth is clamped to 1..MaxDepth"):
    def levels(b: Bucket): Int = 1 + b.children.map(levels).maxOption.getOrElse(0)
    // an 11 500-year window buckets at 1000, so there are three sizes to nest through: 1000/100/10.
    // Sources spread so that each level genuinely splits its parent — otherwise the level is pruned
    // as uninformative (item 7), which the next test covers.
    val rows = List(row("a", -1550, -1541), row("b", -1520, -1511), row("c", -1050, -1041))
    assertEquals(levels(Tree.timeTree(rows, -12000, -500, 0).head), 1, "depth 0 → one level")
    assertEquals(levels(Tree.timeTree(rows, -12000, -500, 99).head), Tree.MaxDepth)
    // …and when the finest size is already reached there is simply nothing to nest
    assertEquals(levels(Tree.timeTree(List(row("a", -1550, -1540)), -1600, -1200, 99).head), 1,
      "a 400-yr window IS 10-yr buckets")

  test("samples land on the LEAVES only, deduplicated by citekey, in row order"):
    // the two sources sit in different 10-yr buckets, so the finer level is worth drawing (item 7)
    val t = Tree.timeTree(List(row("a", -1550, -1541), row("b", -1530, -1521)), -3500, -500, 2, 5)
    assertEquals(t.head.samples, Nil, "an inner node lists no samples")
    val leaf = t.head.children.head
    assertEquals(leaf.samples.map(_.citekey), List("a"))
    assertEquals(leaf.samples.head.title, "Title of a")
    assertEquals(Tree.timeTree(List(row("a", -1550, -1540)), -3500, -500, 1, 1).head.samples.size, 1)
    assertEquals(Tree.timeTree(List(row("a", -1550, -1540)), -3500, -500, 1, 0).head.samples, Nil)

  test("finer TIME children never report an overlap (spanning two buckets is the normal case)"):
    val t = Tree.timeTree(List(row("a", -1550, -1450)), -12000, -500, 3)
    assert(t.forall(_.overlap == 0))

  // ── the region axis ──────────────────────────────────────────────────────────────────────────

  test("§1.6a: Hattusa at -1400 sits under Hittite Empire → Anatolia → Asia"):
    val t = Tree.regionTree(List(dig), nodes, relations, Some(-1400), 3)
    assertEquals(t.map(_.key), List("asia"))
    assertEquals(path(t, "asia", "anatolia", "hatti").map(_.label), Some("Hittite Empire"))
    assertEquals(path(t, "asia", "anatolia", "hatti").map(_.count), Some(1))
    assertEquals(path(t, "asia").map(_.count), Some(1))
    assertEquals(path(t, "asia", "anatolia", "hatti").flatMap(_.placeId), Some("hatti"))
    assertEquals(path(t, "asia", "anatolia", "phrygia"), None, "Phrygia does not exist yet at -1400")

  test("§1.6a: the SAME source sits under Phrygia → Anatolia → Asia at -700"):
    val t = Tree.regionTree(List(dig), nodes, relations, Some(-700), 3)
    assertEquals(path(t, "asia", "anatolia", "phrygia").map(_.label), Some("Phrygia"))
    assertEquals(path(t, "asia", "anatolia", "hatti"), None,
      "the Hittite Empire is not valid at -700, so its direct assignment is dropped too")

  test("§1.6a: a frontier find counts once at the macro level and once under EACH polity"):
    val t = Tree.regionTree(List(frontier), nodes, relations, Some(-1400), 3)
    assertEquals(path(t, "asia").map(_.count), Some(1), "one source, counted once at the top")
    assertEquals(path(t, "asia", "anatolia", "hatti").map(_.count), Some(1))
    assertEquals(path(t, "asia", "near_east", "mitanni").map(_.count), Some(1))
    assertEquals(path(t, "asia").map(_.overlap), Some(1),
      "1 of Asia's sources is in more than one of its subregions")
    assertEquals(path(t, "asia", "anatolia").map(_.overlap), Some(0))

  test("counts are distinct units in the whole subtree, never the sum of the children"):
    val t = Tree.regionTree(List(dig, frontier), nodes, relations, Some(-1400), 3)
    assertEquals(path(t, "asia").map(_.count), Some(2))
    assertEquals(path(t, "asia").map(_.children.map(_.count).sum), Some(3), "2 sources, 3 in children")

  test("units with no place — or none valid as of the query — land in the trailing unlocated leaf"):
    val t = Tree.regionTree(List(dig, floating), nodes, relations, Some(-1400), 3)
    assertEquals(t.last.key, Tree.KeyUnlocated)
    assertEquals(t.last.count, 1)
    assertEquals(t.last.placeId, None)
    assertEquals(t.last.kind, Tree.KindRegion)
    // the frontier find is only ever inside two polities; at -700 neither exists any more, so it
    // joins the source that never had a place at all
    val gone = Tree.regionTree(List(frontier, floating), nodes, relations, Some(-700), 3)
    assertEquals(gone.map(_.key), List(Tree.KeyUnlocated))
    assertEquals(gone.head.count, 2)

  test("everything is unlocated when the gazetteer is empty (a store with no places still answers)"):
    val t = Tree.regionTree(List(dig), Nil, Nil, Some(-1400), 2)
    assertEquals(t.map(b => (b.key, b.count)), List((Tree.KeyUnlocated, 1)))

  test("a node no unit reaches is dropped, and children sort by count then label"):
    val t = Tree.regionTree(List(dig, frontier), nodes, relations, Some(-1400), 2)
    assertEquals(path(t, "asia").map(_.children.map(_.key)), Some(List("anatolia", "near_east")))

  test("truncating at depth does not change what overlap means"):
    val deep    = Tree.regionTree(List(frontier), nodes, relations, Some(-1400), 3)
    val shallow = Tree.regionTree(List(frontier), nodes, relations, Some(-1400), 1)
    assertEquals(shallow.head.children, Nil)
    assertEquals(shallow.head.overlap, deep.head.overlap)

  test("a place's own validity is reported as the bucket's period; contested is carried through"):
    val t = Tree.regionTree(List(frontier), nodes, relations, Some(-1400), 3)
    assertEquals(path(t, "asia", "near_east", "mitanni").map(b => (b.start, b.end)),
      Some((Some(-1500L), Some(-1300L))))
    assertEquals(path(t, "asia", "near_east", "mitanni").map(_.contested), Some(true))
    assertEquals(path(t, "asia").map(b => (b.start, b.end)), Some((None, None)))

  test("a cyclic hierarchy terminates instead of overflowing the stack"):
    val cyc = relations :+ part("anatolia", "hatti", Some(Period(-1650, -1180)))
    val t   = Tree.regionTree(List(dig), nodes, cyc, Some(-1400), 3)
    assertEquals(path(t, "asia", "anatolia", "hatti").map(_.count), Some(1))
    assertEquals(path(t, "asia", "anatolia", "hatti", "anatolia"), None, "the cycle is cut, not walked")

  test("an explicit root returns THAT node's subtree, which is how a client expands lazily"):
    val t = Tree.regionTree(List(dig, frontier), nodes, relations, Some(-1400), 2, 0, Some("anatolia"))
    assertEquals(t.map(_.key), List("anatolia"), "rooted at the node, not at the continent")
    assertEquals(t.head.count, 2)
    assertEquals(t.head.children.map(_.key), List("hatti"))
    // a root that is not valid as of the query falls back to the ordinary forest
    assertEquals(Tree.regionTree(List(dig), nodes, relations, Some(-700), 2, 0, Some("hatti"))
      .map(_.key), List("asia"))

  test("a parentless SITE is not a top level (Pleiades has ~16 000 of them, all parentless)"):
    // as of -1700 neither polity exists, so Hattusa has no valid parent — it must NOT become a root
    val t = Tree.regionTree(List(dig), nodes, relations, Some(-1700), 3)
    assertEquals(t.map(_.key), List(Tree.KeyUnlocated))
    // a parentless REGION or POLITY still shows: it is a gazetteer top level, not a gap
    val loose = RegionNode("loose", "Loose Region", PlaceKind.Region)
    val t2 = Tree.regionTree(List(row("x", -1400, -1400, "loose")), List(loose), Nil, Some(-1400), 2)
    assertEquals(t2.map(b => (b.key, b.count)), List(("loose", 1)))

  test("a gazetteer with NO root at all still reports its units instead of losing them"):
    // every node has a parent, so nothing is reachable from a root — the units must not vanish
    val cyc = relations ++ List(part("asia", "hatti", Some(Period(-1650, -1180))))
    val t   = Tree.regionTree(List(dig), nodes, cyc, Some(-1400), 3)
    assertEquals(t.map(b => (b.key, b.count)), List((Tree.KeyUnlocated, 1)))

  test("asOf = None reads every edge, whatever its validity"):
    val t = Tree.regionTree(List(dig), nodes, relations, None, 3)
    assert(path(t, "asia", "anatolia", "hatti").isDefined)
    assert(path(t, "asia", "anatolia", "phrygia").isDefined)

  // ── the two axes together ────────────────────────────────────────────────────────────────────

  test("timeRegionTree reads the gazetteer at EACH bucket's own midpoint"):
    // one source in the LBA, one in the Iron Age, both at Hattusa
    val lba  = row("lba", -1400, -1400, "hattusa")
    val iron = row("iron", -800, -800, "hattusa")
    val t    = Tree.timeRegionTree(List(lba, iron), -1600, -600, nodes, relations, 3)
    val early = t.find(_.start.contains(-1400L)).get
    val late  = t.find(_.start.contains(-800L)).get
    assertEquals(Tree.midpoint(-1400, -1301), -1351L)
    assertEquals(early.kind, Tree.KindTime)
    assert(path(early.children, "asia", "anatolia", "hatti").isDefined, "LBA bucket → Hittite Empire")
    assert(path(late.children, "asia", "anatolia", "phrygia").isDefined, "Iron-Age bucket → Phrygia")
    assert(path(early.children, "asia", "anatolia", "phrygia").isEmpty)

  test("timeRegionTree keeps the time level flat: exactly one level of buckets, then regions"):
    val t = Tree.timeRegionTree(List(dig), -1600, -1200, nodes, relations, 2)
    assertEquals(t.size, 1)
    assert(t.head.children.forall(_.kind == Tree.KindRegion))
    assertEquals(t.head.children.head.key, "asia")

  test("regionTimeTree hangs time buckets under each region LEAF"):
    val t    = Tree.regionTimeTree(List(dig), -1600, -1200, nodes, relations, Some(-1400), 2, 2)
    val leaf = path(t, "asia", "anatolia").get
    assertEquals(leaf.children.map(_.kind).distinct, List(Tree.KindTime))
    assertEquals(leaf.children.head.samples.map(_.citekey), List("dig"))

  test("the unlocated leaf survives both combined axes"):
    val t = Tree.timeRegionTree(List(floating), -1600, -1200, nodes, relations, 2)
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
    assert(Tree.coversAsOf(None, Some(-1400)))
    assert(Tree.coversAsOf(Some(Period(-1650, -1180)), None))
    assert(Tree.coversAsOf(Some(Period(-1650, -1180)), Some(-1650)), "bounds are inclusive")
    assert(Tree.coversAsOf(Some(Period(-1650, -1180)), Some(-1180)))
    assert(!Tree.coversAsOf(Some(Period(-1650, -1180)), Some(-1179)))

  test("midpoint rounds down, so a bucket always reads the gazetteer at the same year"):
    assertEquals(Tree.midpoint(-1600, -1501), -1551L)
    assertEquals(Tree.midpoint(-1600, -1200), -1400L)
    assertEquals(Tree.midpoint(-10, -10), -10L)

  test("the Bucket tree round-trips through JSON and has a tapir schema (recursion included)"):
    val b  = Tree.regionTree(List(dig, floating), nodes, relations, Some(-1400), 3, 2).head
    val js = io.circe.syntax.EncoderOps(b).asJson.noSpaces
    assert(js.contains("Hittite Empire"), js)
    assertEquals(io.circe.parser.decode[Bucket](js), Right(b))
    assert(Bucket.schema.toString.nonEmpty)

  test("Markdown.tree renders the indented tree with the non-additive caveat"):
    val t  = Tree.regionTree(List(dig, frontier, floating), nodes, relations, Some(-1400), 3, 1)
    val md = Markdown.tree(
      TimeHistogram(-1600, -1200, 100, Nil, by = "region", total = 3, depth = 3, tree = t), "sources")
    assert(md.contains("### sources by region"), md)
    assert(md.contains("- **Asia** `asia` — 2 sources · 1 in more than one subregion"), md)
    assert(md.contains("    - **Hittite Empire** `hatti` — 2 sources"), md)
    assert(md.contains("(contested)"), md)
    assert(md.contains("- **unlocated** — 1 sources"), md)
    assert(md.contains("non-additive"), md)

  test("Markdown.tree says so when there is nothing to group"):
    assert(Markdown.tree(TimeHistogram(-1600, -1200, 100, Nil, by = "region"), "sources")
      .contains("No sources to group"))

/** Root eligibility and the "is this split worth drawing" rule (§1.6, UX items 7 and 8). Pure. */
class TreeRootsSuite extends munit.FunSuite:
  import Tree.RegionNode

  private def node(id: String, label: String, kind: String, withdrawn: Boolean = false) =
    RegionNode(id, label, kind, None, false, withdrawn)

  test("only a region, polity or continent may head the tree"):
    assert(Tree.rootEligible(node("a", "Asia", "region")))
    assert(Tree.rootEligible(node("b", "Akkad", "polity")))
    assert(Tree.rootEligible(node("c", "Europe", "continent")))
    assert(!Tree.rootEligible(node("d", "Asopos (river)", "feature")))
    assert(!Tree.rootEligible(node("e", "Hattusa", "site")))

  test("a withdrawn Pleiades record is never a root, however it is marked"):
    assert(!Tree.rootEligible(node("f", "Mount Juktas", "region", withdrawn = true)))
    assert(!Tree.rootEligible(node("g", "Withdrawn (duplicate): Creta et Cyrene", "region")))

  test("an uncertain or reconstructed name is never a root"):
    assert(Tree.uncertainLabel("Theren? (river)"))
    assert(Tree.uncertainLabel("*Granouas (river)"))
    assert(!Tree.uncertainLabel("Anatolia"))
    assert(!Tree.rootEligible(node("h", "Kisamon? Pr.", "region")))

  test("ineligible nodes are dropped from the ROOTS, not from the tree"):
    val rows  = List(Tree.TreeRow("s1", "s1", "S1", Some(-1500), Some(-1400), List("river")))
    val nodes = List(node("asia", "Asia", "region"), node("river", "Asopos (river)", "feature"))
    val rels  = List(PlaceRelation("river", "asia", PlaceRelation.PartOf, None))
    val tree  = Tree.regionTree(rows, nodes, rels, None, depth = 2)
    assertEquals(tree.map(_.label), List("Asia"))
    assertEquals(tree.head.children.map(_.label), List("Asopos (river)"))

  test("a place with two parents is drawn once, under the bigger, and says where else it sits"):
    val rows = List(
      Tree.TreeRow("s1", "s1", "S1", Some(-1500), Some(-1400), List("anatolia")),
      Tree.TreeRow("s2", "s2", "S2", Some(-1500), Some(-1400), List("asia")))
    val nodes = List(node("asia", "Asia", "region"), node("europe", "Europe", "region"),
      node("anatolia", "Anatolia", "region"))
    val rels = List(
      PlaceRelation("anatolia", "asia", PlaceRelation.PartOf, None),
      PlaceRelation("anatolia", "europe", PlaceRelation.PartOf, None))
    val tree = Tree.regionTree(rows, nodes, rels, None, depth = 2)
    val under = tree.flatMap(b => b.children.map(c => (b.label, c.label, c.alsoUnder)))
    assertEquals(under.size, 1, s"Anatolia drawn ${under.size} times: $under")
    assertEquals(under.head._1, "Asia")                 // the parent with the larger count
    assertEquals(under.head._3, List("Europe"))

  // ── item 7: finer time levels that say nothing are not drawn ────────────────────────────────
  test("a split is kept only when a child is materially smaller than the parent"):
    def b(n: Int) = Bucket(label = "x", key = "k" + n, kind = Tree.KindTime, count = n)
    assertEquals(Tree.splitWorthShowing(100, List(b(99), b(98))), Nil, "all children ≈ the parent")
    assertEquals(Tree.splitWorthShowing(100, List(b(99), b(20))).size, 2, "one child is much smaller")
    assertEquals(Tree.splitWorthShowing(100, List(b(10))), Nil, "a single child adds no information")

  test("a window of long-range sources does not nest into near-identical children"):
    // three sources each spanning the whole window: every finer bucket would carry all three
    val rows = (1 to 3).map(i => Tree.TreeRow(s"s$i", s"s$i", s"S$i", Some(-3000), Some(-1000))).toList
    val tree = Tree.timeTree(rows, -3000, -1000, depth = 3, samples = 2)
    assert(tree.forall(_.children.isEmpty), tree.map(b => (b.label, b.children.size)).toString)
    assert(tree.forall(_.samples.nonEmpty), "a leaf shows its samples instead")

  test("a window whose sources really are in different centuries still nests"):
    // span 5 000 → 1 000-yr top buckets, 100-yr children; five sources spread across one millennium,
    // so every child holds one fifth of its parent — a split that genuinely separates the evidence
    val rows = (0 until 5).toList.map { i =>
      val s = -3000L + i * 200
      Tree.TreeRow(s"s$i", s"s$i", s"S$i", Some(s), Some(s + 50))
    }
    val tree = Tree.timeTree(rows, -6000, -1000, depth = 3, samples = 0)
    assert(tree.exists(_.children.nonEmpty), tree.map(b => (b.label, b.count, b.children.size)).toString)

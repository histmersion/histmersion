package com.histmersion.api

import com.histmersion.api.research.ArticleLint

import com.histmersion.api.research.CritiquePairing.*
import com.histmersion.api.checks.Lint
import com.histmersion.api.research.{CoverageEdge, CritiquePair, Critiques, CritiqueSide, CritiqueState}

/** The pairing rule (docs/research-review-convergence.md §6.9), pure: the names a source is called by, the paragraphs
  * that name a disagreement partner they do not cite, which of them code may repair, the repair itself, the instruction
  * a writer gets for the rest, and the lint rule over articles.
  *
  * The text is an excerpt of the live `achilles_origins` article (2026-10-19), whose ledger held three missing-critique
  * MAJORs noted "the paragraph still cites only [[kelly2021_gods]]" (S6), "…only [[fantuzzi2015_greek]]" (S5) and
  * "Neither this paragraph nor Disagreement 4 cites [[latacz2004_troy]]" (S4) across runs, and a fourth (S3) whose
  * critic the article never names.
  */
class CritiquePairingSuite extends munit.FunSuite:

  private val excerpt = scala.io.Source.fromResource("review/achilles-excerpt.md")(using scala.io.Codec.UTF8).mkString

  /** The live edges around those sources. */
  private val edges = List(
    CoverageEdge("bachvarova2016_hittite", "challenges", "burkert1992_orientalizing"),
    CoverageEdge("bachvarova2016_hittite", "challenges", "latacz2004_troy"),
    CoverageEdge("bachvarova2016_hittite", "challenges", "west1997_helicon"),
    CoverageEdge("ballesteros2024_naming", "challenges", "lord1960_singer"),
    CoverageEdge("fantuzzi2015_greek", "challenges", "west2011_making"),
    CoverageEdge("kelly2021_gods", "challenges", "bachvarova2016_hittite"),
    CoverageEdge("latacz2004_troy", "challenges", "kullmann1999_memory"),
    CoverageEdge("latacz2004_troy", "challenges", "page1959_history"),
    CoverageEdge("kelly2021_gods", "references", "lord1960_singer")
  ) // not a disagreement kind

  /** The live authors (as stored) and years. */
  private val authors = Map(
    "bachvarova2016_hittite" -> (List("Mary R. Bachvarova"), 2016),
    "latacz2004_troy" -> (List("Joachim Latacz"), 2004),
    "west2011_making" -> (List("Martin Litchfield West"), 2011),
    "west1997_helicon" -> (List("M. L. West"), 1997),
    "kelly2021_gods" -> (List("Adrian Kelly", "Christopher Metcalf"), 2021),
    "fantuzzi2015_greek" -> (List("Marco Fantuzzi", "Christos Tsagalis"), 2015),
    "ballesteros2024_naming" -> (List("Bernardo Ballesteros"), 2024),
    "lord1960_singer" -> (List("Albert Bates Lord"), 1960),
    "burkert1992_orientalizing" -> (List("Walter Burkert"), 1992),
    "kullmann1999_memory" -> (List("Wolfgang Kullmann"), 1999),
    "page1959_history" -> (List("Denys Page"), 1959),
    "pache2020_homerguide" -> (List("Corinne Pache"), 2020)
  )
  private val work: String => Option[Work] = ck => authors.get(ck).map((as, y) => Work(ck, names(ck, as), Some(y)))
  private val resolve: String => Option[String] = t => Some(t).filter(authors.contains)

  test("names: the authors' word that is the citekey's leading name, else the first author's last word"):
    assertEquals(names("latacz2004_troy", List("Joachim Latacz")), List("Latacz"))
    assertEquals(names("drews1993_endbronze", List("Drews Robert")), List("Drews"))
    assertEquals(names("bjrn2024_twenty", List("Rasmus Bjørn")), List("Bjørn"))
    assertEquals(names("kelly2021_gods", List("Adrian Kelly", "Christopher Metcalf")), List("Kelly"))
    assertEquals(names("smith1987_dying", List("Smith, Jonathan Z.")), List("Smith"))
    assertEquals(names("iecor2025_dataset", List("Cormac Anderson", "Matthew Scarborough")), List("Anderson"))
    assertEquals(names("x2000_y", Nil), Nil)
    assertEquals(names("li2000_y", List("Li")), Nil, "a two-letter name matches too much prose")

  test("the live excerpt: two paragraphs name the work they answer without citing it; both are mechanical"):
    val hs = hits(excerpt, resolve, edges, work)
    assertEquals(
      hs.map(h => (h.cited, h.missing, h.named, h.mechanical)),
      List(
        ("bachvarova2016_hittite", "latacz2004_troy", "Latacz", true),
        ("kelly2021_gods", "bachvarova2016_hittite", "Bachvarova", true),
        ("fantuzzi2015_greek", "west2011_making", "West", true)
      )
    )
    assertEquals(hs.head.edges, List("[[bachvarova2016_hittite]] challenges [[latacz2004_troy]]"))
    assert(hs.head.where.startsWith("Bachvarova (2016) directly challenges"), hs.head.where)
    assert(hs.head.line.contains("cite [[latacz2004_troy]] there (fixable)"), hs.head.line)
    // the paragraph on Lord never names Ballesteros: no hit (that pair is the writer's, see `instruction`)
    assert(!hs.exists(_.missing == "ballesteros2024_naming"))

  test("repair: the missing citekey appended after the paragraph's last link; the rest of the body untouched"):
    val (fixed, done) = repair(excerpt, hits(excerpt, resolve, edges, work))
    assertEquals(done.map(_.missing), List("latacz2004_troy", "bachvarova2016_hittite", "west2011_making"))
    assert(fixed.contains("[[bachvarova2016_hittite]] [[latacz2004_troy]]"), fixed)
    assert(fixed.contains("[[kelly2021_gods]] [[bachvarova2016_hittite]]"), fixed)
    assert(fixed.contains("[[fantuzzi2015_greek]] [[west2011_making]]"), fixed)
    assertEquals(fixed.linesIterator.size, excerpt.linesIterator.size)
    // nothing left to repair, and the three pairs are now addressed
    assertEquals(hits(fixed, resolve, edges, work), Nil)
    val pairs = Critiques.build(fixed, resolve, edges, Nil)
    List(
      "bachvarova2016_hittite>latacz2004_troy",
      "kelly2021_gods>bachvarova2016_hittite",
      "fantuzzi2015_greek>west2011_making"
    )
      .foreach(w => assertEquals(pairs.find(_.ref.wire == w).map(_.state), Some(CritiqueState.Addressed), w))
    assertEquals(repair(excerpt, Nil), (excerpt, Nil))

  test("not mechanical: a name two partners share; a work already cited by that name; only another year beside it"):
    val es = List(
      CoverageEdge("a2000_x", "challenges", "west1997_helicon"),
      CoverageEdge("a2000_x", "challenges", "west2011_making")
    )
    val w: String => Option[Work] = Map(
      "a2000_x" -> Work("a2000_x", List("Alpha"), Some(2000)),
      "west1997_helicon" -> Work("west1997_helicon", List("West"), Some(1997)),
      "west2011_making" -> Work("west2011_making", List("West"), Some(2011))
    ).get
    val r: String => Option[String] = Some(_)
    val cited = "\n\nWest wrote twice [[west1997_helicon]] [[west2011_making]]."
    // both Wests are partners: without a year the name means neither for sure
    val shared = hits("Alpha answers West's reading [[a2000_x]]." + cited, r, es, w)
    assertEquals(
      shared.map(h => (h.missing, h.mechanical)),
      List(("west1997_helicon", false), ("west2011_making", false))
    )
    assertEquals(repair("Alpha answers West's reading [[a2000_x]]." + cited, shared)._2, Nil)
    // with the year beside the name it means that one
    val dated = hits("Alpha answers West (2011) on the date [[a2000_x]]." + cited, r, es, w)
    assertEquals(dated.filter(_.mechanical).map(_.missing), List("west2011_making"))
    // …but code never brings in a source the article cites nowhere: that is a writer's call
    val fresh = hits("Alpha answers West (2011) on the date [[a2000_x]].", r, es, w)
    assertEquals(fresh.map(h => (h.missing, h.mechanical)), List(("west2011_making", false)))
    assert(fresh.head.line.contains("the article cites it nowhere else"), fresh.head.line)
    // only another year beside every mention: another work of the author — no hit at all
    assertEquals(hits("Alpha answers West's 1988 article [[a2000_x]].", r, es.take(1), w), Nil)
    // the critic named by its own name is no mention of the partner
    val self = List(CoverageEdge("west1997_helicon", "updates", "west2011_making"))
    assertEquals(
      hits("West revised his view [[west1997_helicon]]." + cited, r, self, w).filter(_.unit == 0).map(_.mechanical),
      List(false)
    )

  test("instruction: where a critique the paragraph never names goes, and what to write there"):
    val pairs = Critiques.build(excerpt, resolve, edges, Nil)
    val lord = pairs.find(_.ref.wire == "ballesteros2024_naming>lord1960_singer").get
    val text = instruction(excerpt, lord, resolve).get
    assert(
      text.startsWith(
        "ballesteros2024_naming>lord1960_singer: in the paragraph beginning \"Lord’s *The Singer of Tales* (1960)"
      ),
      text
    )
    assert(
      text.contains(
        "(it cites [[lord1960_singer]]), add one sentence stating the challenges from [[ballesteros2024_naming]]'s record"
      ),
      text
    )
    // a pair whose criticised work no paragraph cites: nothing to point at
    val loose = CritiquePair(CritiqueSide("a2000_x"), List("refutes"), CritiqueSide("b2001_y"), CritiqueState.Open)
    assertEquals(instruction(excerpt, loose, resolve), None)

  test("resolver: the citekey, an old one renamed away, the unique short form — nothing else"):
    val r = resolver(
      Set("knapp2016_seven", "knapp2021_migration", "west2011_making", "latacz2004_troy"),
      Map("latacz2023_troy" -> "latacz2004_troy")
    )
    assertEquals(
      List("west2011_making", "latacz2023_troy", "knapp2016", "knapp", "nothing", "WEST2011_MAKING").map(r),
      List(
        Some("west2011_making"),
        Some("latacz2004_troy"),
        Some("knapp2016_seven"),
        None,
        None,
        Some("west2011_making")
      )
    )

  test("the lint rule: one item per hit, per article"):
    val items = ArticleLint.articleCritiquePairing(
      List("achilles_origins" -> excerpt, "clean" -> "Nothing here [[lord1960_singer]]."),
      resolve,
      edges,
      work
    )
    assertEquals(items.map(_.citekey).distinct, List("achilles_origins"))
    assertEquals(items.size, 3)
    assert(items.forall(_.detail.contains("(fixable)")), items)
    assert(Lint.rule(Lint.ArticleCritiquePairing).isDefined)

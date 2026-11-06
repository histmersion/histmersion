package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.research.CritiquePairing.*
import com.histmersion.api.checks.Lint
import com.histmersion.api.research.{CritiquePair, Critiques, CritiqueSide, CritiqueState}
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.api.library.{AuthorName, Citekey}
import com.histmersion.api.catalogue.Year

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

  private val excerpt = Article.Body(scala.io.Source.fromResource("review/achilles-excerpt.md")(using scala.io.Codec.UTF8).mkString)

  /** The live edges around those sources. */
  private val edges = List(
    Coverage.Edge(com.histmersion.api.library.Citekey("bachvarova2016_hittite"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("burkert1992_orientalizing")),
    Coverage.Edge(com.histmersion.api.library.Citekey("bachvarova2016_hittite"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("latacz2004_troy")),
    Coverage.Edge(com.histmersion.api.library.Citekey("bachvarova2016_hittite"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("west1997_helicon")),
    Coverage.Edge(com.histmersion.api.library.Citekey("ballesteros2024_naming"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("lord1960_singer")),
    Coverage.Edge(com.histmersion.api.library.Citekey("fantuzzi2015_greek"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("west2011_making")),
    Coverage.Edge(com.histmersion.api.library.Citekey("kelly2021_gods"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("bachvarova2016_hittite")),
    Coverage.Edge(com.histmersion.api.library.Citekey("latacz2004_troy"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("kullmann1999_memory")),
    Coverage.Edge(com.histmersion.api.library.Citekey("latacz2004_troy"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("page1959_history")),
    Coverage.Edge(com.histmersion.api.library.Citekey("kelly2021_gods"), com.histmersion.api.research.Coverage.Edge.Kind("references"), com.histmersion.api.library.Citekey("lord1960_singer"))
  ) // not a disagreement kind

  /** The live authors (as stored) and years. */
  private val authors = Map(
    Citekey("bachvarova2016_hittite") -> (List(AuthorName("Mary R. Bachvarova")), Year(2016)),
    Citekey("latacz2004_troy") -> (List(AuthorName("Joachim Latacz")), Year(2004)),
    Citekey("west2011_making") -> (List(AuthorName("Martin Litchfield West")), Year(2011)),
    Citekey("west1997_helicon") -> (List(AuthorName("M. L. West")), Year(1997)),
    Citekey("kelly2021_gods") -> (List(AuthorName("Adrian Kelly"), AuthorName("Christopher Metcalf")), Year(2021)),
    Citekey("fantuzzi2015_greek") -> (List(AuthorName("Marco Fantuzzi"), AuthorName("Christos Tsagalis")), Year(2015)),
    Citekey("ballesteros2024_naming") -> (List(AuthorName("Bernardo Ballesteros")), Year(2024)),
    Citekey("lord1960_singer") -> (List(AuthorName("Albert Bates Lord")), Year(1960)),
    Citekey("burkert1992_orientalizing") -> (List(AuthorName("Walter Burkert")), Year(1992)),
    Citekey("kullmann1999_memory") -> (List(AuthorName("Wolfgang Kullmann")), Year(1999)),
    Citekey("page1959_history") -> (List(AuthorName("Denys Page")), Year(1959)),
    Citekey("pache2020_homerguide") -> (List(AuthorName("Corinne Pache")), Year(2020))
  )
  private val work: Citekey => Option[Work] = ck => authors.get(ck).map((as, y) => Work(ck, names(ck, as), Some(y)))
  private val resolve: Article.Link.Target => Option[Citekey] = t => Some(Citekey(t.value)).filter(authors.contains)

  test("names: the authors' word that is the citekey's leading name, else the first author's last word"):
    assertEquals(names(com.histmersion.api.library.Citekey("latacz2004_troy"), List(com.histmersion.api.library.AuthorName("Joachim Latacz"))), List("Latacz"))
    assertEquals(names(com.histmersion.api.library.Citekey("drews1993_endbronze"), List(com.histmersion.api.library.AuthorName("Drews Robert"))), List("Drews"))
    assertEquals(names(com.histmersion.api.library.Citekey("bjrn2024_twenty"), List(com.histmersion.api.library.AuthorName("Rasmus Bjørn"))), List("Bjørn"))
    assertEquals(names(com.histmersion.api.library.Citekey("kelly2021_gods"), List(com.histmersion.api.library.AuthorName("Adrian Kelly"), com.histmersion.api.library.AuthorName("Christopher Metcalf"))), List("Kelly"))
    assertEquals(names(com.histmersion.api.library.Citekey("smith1987_dying"), List(com.histmersion.api.library.AuthorName("Smith, Jonathan Z."))), List("Smith"))
    assertEquals(names(com.histmersion.api.library.Citekey("iecor2025_dataset"), List(com.histmersion.api.library.AuthorName("Cormac Anderson"), com.histmersion.api.library.AuthorName("Matthew Scarborough"))), List("Anderson"))
    assertEquals(names(com.histmersion.api.library.Citekey("x2000_y"), Nil), Nil)
    assertEquals(names(com.histmersion.api.library.Citekey("li2000_y"), List(com.histmersion.api.library.AuthorName("Li"))), Nil, "a two-letter name matches too much prose")

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
    assert(hs.head.where.value.startsWith("Bachvarova (2016) directly challenges"), hs.head.where)
    assert(hs.head.line.contains("cite [[latacz2004_troy]] there (fixable)"), hs.head.line)
    // the paragraph on Lord never names Ballesteros: no hit (that pair is the writer's, see `instruction`)
    assert(!hs.exists(_.missing.value == "ballesteros2024_naming"))

  test("repair: the missing citekey appended after the paragraph's last link; the rest of the body untouched"):
    val (fixed, done) = repair(excerpt, hits(excerpt, resolve, edges, work))
    assertEquals(done.map(_.missing), List("latacz2004_troy", "bachvarova2016_hittite", "west2011_making"))
    assert(fixed.value.contains("[[bachvarova2016_hittite]] [[latacz2004_troy]]"), fixed)
    assert(fixed.value.contains("[[kelly2021_gods]] [[bachvarova2016_hittite]]"), fixed)
    assert(fixed.value.contains("[[fantuzzi2015_greek]] [[west2011_making]]"), fixed)
    assertEquals(fixed.value.linesIterator.size, excerpt.value.linesIterator.size)
    // nothing left to repair, and the three pairs are now addressed
    assertEquals(hits(fixed, resolve, edges, work), Nil)
    val pairs = Critiques.build(fixed, resolve, edges, Nil)
    List(
      "bachvarova2016_hittite>latacz2004_troy",
      "kelly2021_gods>bachvarova2016_hittite",
      "fantuzzi2015_greek>west2011_making"
    )
      .foreach(w => assertEquals(pairs.find(_.ref.wire.value == w).map(_.state), Some(CritiqueState.Addressed), w))
    assertEquals(repair(excerpt, Nil), (excerpt, Nil))

  test("not mechanical: a name two partners share; a work already cited by that name; only another year beside it"):
    val es = List(
      Coverage.Edge(com.histmersion.api.library.Citekey("a2000_x"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("west1997_helicon")),
      Coverage.Edge(com.histmersion.api.library.Citekey("a2000_x"), com.histmersion.api.research.Coverage.Edge.Kind("challenges"), com.histmersion.api.library.Citekey("west2011_making"))
    )
    val w: Citekey => Option[Work] = Map(
      Citekey("a2000_x") -> Work(com.histmersion.api.library.Citekey("a2000_x"), List(com.histmersion.api.research.CritiquePairing.Work.Names("Alpha")), Some(com.histmersion.api.catalogue.Year(2000))),
      Citekey("west1997_helicon") -> Work(com.histmersion.api.library.Citekey("west1997_helicon"), List(com.histmersion.api.research.CritiquePairing.Work.Names("West")), Some(com.histmersion.api.catalogue.Year(1997))),
      Citekey("west2011_making") -> Work(com.histmersion.api.library.Citekey("west2011_making"), List(com.histmersion.api.research.CritiquePairing.Work.Names("West")), Some(com.histmersion.api.catalogue.Year(2011)))
    ).get
    val r: Article.Link.Target => Option[Citekey] = t => Some(Citekey(t.value))
    val cited = "\n\nWest wrote twice [[west1997_helicon]] [[west2011_making]]."
    // both Wests are partners: without a year the name means neither for sure
    val shared = hits(Article.Body("Alpha answers West's reading [[a2000_x]]." + cited), r, es, w)
    assertEquals(
      shared.map(h => (h.missing, h.mechanical)),
      List(("west1997_helicon", false), ("west2011_making", false))
    )
    assertEquals(repair(Article.Body("Alpha answers West's reading [[a2000_x]]." + cited), shared)._2, Nil)
    // with the year beside the name it means that one
    val dated = hits(Article.Body("Alpha answers West (2011) on the date [[a2000_x]]." + cited), r, es, w)
    assertEquals(dated.filter(_.mechanical.value).map(_.missing), List("west2011_making"))
    // …but code never brings in a source the article cites nowhere: that is a writer's call
    val fresh = hits(com.histmersion.api.research.Article.Body("Alpha answers West (2011) on the date [[a2000_x]]."), r, es, w)
    assertEquals(fresh.map(h => (h.missing, h.mechanical)), List(("west2011_making", false)))
    assert(fresh.head.line.contains("the article cites it nowhere else"), fresh.head.line)
    // only another year beside every mention: another work of the author — no hit at all
    assertEquals(hits(Article.Body("Alpha answers West's 1988 article [[a2000_x]]."), r, es.take(1), w), Nil)
    // the critic named by its own name is no mention of the partner
    val self = List(Coverage.Edge(com.histmersion.api.library.Citekey("west1997_helicon"), com.histmersion.api.research.Coverage.Edge.Kind("updates"), com.histmersion.api.library.Citekey("west2011_making")))
    assertEquals(
      hits(Article.Body("West revised his view [[west1997_helicon]]." + cited), r, self, w).filter(_.unit.value == 0).map(_.mechanical),
      List(false)
    )

  test("instruction: where a critique the paragraph never names goes, and what to write there"):
    val pairs = Critiques.build(excerpt, resolve, edges, Nil)
    val lord = pairs.find(_.ref.wire.value == "ballesteros2024_naming>lord1960_singer").get
    val text = instruction(excerpt, lord, resolve).get
    assert(
      text.value.startsWith(
        "ballesteros2024_naming>lord1960_singer: in the paragraph beginning \"Lord’s *The Singer of Tales* (1960)"
      ),
      text
    )
    assert(
      text.value.contains(
        "(it cites [[lord1960_singer]]), add one sentence stating the challenges from [[ballesteros2024_naming]]'s record"
      ),
      text
    )
    // a pair whose criticised work no paragraph cites: nothing to point at
    val loose = CritiquePair(CritiqueSide(com.histmersion.api.library.Citekey("a2000_x")), List(com.histmersion.api.research.CritiquePair.Kinds("refutes")), CritiqueSide(com.histmersion.api.library.Citekey("b2001_y")), CritiqueState.Open)
    assertEquals(instruction(excerpt, loose, resolve), None)

  test("resolver: the citekey, an old one renamed away, the unique short form — nothing else"):
    val r = resolver(
      Set(com.histmersion.api.library.Citekey("knapp2016_seven"), com.histmersion.api.library.Citekey("knapp2021_migration"), com.histmersion.api.library.Citekey("west2011_making"), com.histmersion.api.library.Citekey("latacz2004_troy")),
      Map(Citekey("latacz2023_troy") -> Citekey("latacz2004_troy"))
    )
    assertEquals(
      List("west2011_making", "latacz2023_troy", "knapp2016", "knapp", "nothing", "WEST2011_MAKING").map(s => r(Article.Link.Target(s))),
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
    val items = Article.Lint.articleCritiquePairing(
      List(Article.Slug("achilles_origins") -> excerpt, Article.Slug("clean") -> Article.Body("Nothing here [[lord1960_singer]].")),
      resolve,
      edges,
      work
    )
    assertEquals(items.map(_.citekey).distinct, List("achilles_origins"))
    assertEquals(items.size, 3)
    assert(items.forall(_.detail.value.contains("(fixable)")), items)
    assert(Lint.rule(Lint.Rule.Name(Lint.ArticleCritiquePairing)).isDefined)

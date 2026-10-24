package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

/** The critique checklist (docs/research-review-convergence.md §6.8), pure: the paragraphs "the same
  * paragraph" is judged in, the pair states (alias resolution, both sides cited or one), the ranking
  * and the bound of the skeptic's brief, the generated Method part and the wire forms. */
class CritiquesSuite extends munit.FunSuite:

  /** `[[x]]` → its canonical citekey: two aliases, one article slug, the rest themselves. */
  private val resolve: String => Option[String] = {
    case "frazer"           => Some("frazer1922_golden")   // a short form
    case "frazer1890_bough" => Some("frazer1922_golden")   // an old citekey (renamed)
    case "dying_gods"       => None                        // an article
    case t                  => Some(t)
  }
  private def edge(a: String, k: String, b: String) = CoverageEdge(a, k, b)
  private val refs: String => Option[(String, Option[Int])] = Map(
    "smith1987_dying" -> ("Dying and rising gods", Some(1987)), "frazer1922_golden" -> ("The golden bough", Some(1922))).get

  test("paragraphs: blank-line blocks, each list item and table row its own; headings, code and a hand-written Not used left out"):
    val body =
      """# Title
        |
        |One [[a]] and
        |two [[b]].
        |
        |- item [[a]]
        |  more of it [[c]]
        |- item [[b]]
        |
        || x [[a]] | [[b]] |
        |
        |```
        |[[a]] [[b]] in code
        |```
        |
        |## Not used
        |
        |- [[a]], [[b]] — off topic
        |""".stripMargin
    assertEquals(Critiques.paragraphs(body),
      List("One [[a]] and two [[b]].", "- item [[a]] more of it [[c]]", "- item [[b]]", "| x [[a]] | [[b]] |"))

  test("states: addressed in one paragraph (aliases resolve), reasoned by a stored reason either way round, else open"):
    val body =
      """The bough [[frazer]] made the dying god a type; [[smith1987_dying]] refutes the type.
        |
        |The calendar [[frazer1890_bough]] is old.
        |
        |A later study [[jones2001_myth]] reads the rites again; see [[dying_gods]].
        |""".stripMargin
    val edges = List(
      edge("smith1987_dying", "refutes", "frazer1922_golden"),       // both cited, one paragraph (via a short form)
      edge("brown1999_rites", "challenges", "frazer1922_golden"),    // critic not cited
      edge("jones2001_myth", "updates", "lee1990_ritual"),           // cited critic, target not cited
      edge("lee1990_ritual", "challenges", "jones2001_myth"),        // the same two sources, other way round
      edge("brown1999_rites", "references", "frazer1922_golden"),    // not a disagreement kind
      edge("x2000_a", "refutes", "y2001_b"))                         // touches no cited source
    val reasons = List(CritiqueReason("frazer1922_golden", "brown1999_rites", "The article cites it for the calendar only.", Some("person")))
    val pairs = Critiques.build(body, resolve, edges, reasons, refs)
    assertEquals(pairs.map(p => (p.ref.wire, p.kinds, p.state, p.src.cited, p.dst.cited)), List(
      ("brown1999_rites>frazer1922_golden", List("challenges"), CritiqueState.Reasoned, false, true),
      ("jones2001_myth>lee1990_ritual", List("updates"), CritiqueState.Open, true, false),
      ("lee1990_ritual>jones2001_myth", List("challenges"), CritiqueState.Open, false, true),
      ("smith1987_dying>frazer1922_golden", List("refutes"), CritiqueState.Addressed, true, true)))
    assertEquals(pairs.head.reason, Some("The article cites it for the calendar only."))
    assertEquals(pairs.last.dst, CritiqueSide("frazer1922_golden", Some("The golden bough"), Some(1922), cited = true, mentions = 2))
    // a reason on an addressed pair: the text wins
    val both = Critiques.build(body, resolve, edges.take(1), List(CritiqueReason("smith1987_dying", "frazer1922_golden", "x")), refs)
    assertEquals(both.map(_.state), List(CritiqueState.Addressed))
    // an empty stored reason is no reason
    assertEquals(Critiques.build(body, resolve, edges.slice(1, 2), List(reasons.head.copy(reason = " ")), refs).map(_.state),
      List(CritiqueState.Open))

  test("two kinds between the same two sources are one pair; both sides cited but apart is open"):
    val body = "One [[a2000_x]].\n\nTwo [[b2001_y]]."
    val pairs = Critiques.build(body, Some(_), List(edge("b2001_y", "updates", "a2000_x"), edge("b2001_y", "refutes", "a2000_x")), Nil)
    assertEquals(pairs.map(p => (p.kinds, p.state)), List((List("refutes", "updates"), CritiqueState.Open)))
    assertEquals(Critiques.find(pairs, CritiqueRef("a2000_x", "b2001_y")).map(_.ref), Some(CritiqueRef("b2001_y", "a2000_x")))

  private def pair(src: String, kind: String, dst: String, srcCited: Boolean, dstCited: Boolean, mentions: Int = 1,
      state: CritiqueState = CritiqueState.Open, reason: Option[String] = None) =
    CritiquePair(CritiqueSide(src, cited = srcCited, mentions = if srcCited then mentions else 0), List(kind),
      CritiqueSide(dst, cited = dstCited, mentions = if dstCited then mentions else 0), state, reason)

  test("ranked: an uncited critic of a cited source first, refutes before challenges before updates, the more-cited first"):
    val ps = List(
      pair("c1", "updates", "d1", srcCited = false, dstCited = true, mentions = 9),
      pair("c2", "challenges", "d2", srcCited = true, dstCited = false),
      pair("c3", "challenges", "d3", srcCited = false, dstCited = true, mentions = 1),
      pair("c4", "challenges", "d4", srcCited = false, dstCited = true, mentions = 5),
      pair("c5", "refutes", "d5", srcCited = true, dstCited = true),
      pair("c6", "refutes", "d6", srcCited = false, dstCited = true),
      pair("c7", "refutes", "d7", srcCited = false, dstCited = true, state = CritiqueState.Addressed))
    assertEquals(Critiques.ranked(ps).map(_.src.citekey), List("c6", "c4", "c3", "c1", "c5", "c2"))

  test("the skeptic's brief: the counts, the first N open pairs in full, the rest by name, the reasoned ones with their reasons"):
    val open = (1 to 5).toList.map(i => pair(s"c$i", "challenges", "d", srcCited = false, dstCited = true))
    val why  = pair("r1", "refutes", "d", srcCited = false, dstCited = true, state = CritiqueState.Reasoned, reason = Some("off the point"))
    val (text, shown) = Critiques.promptText(open :+ why, maxOpen = 2, maxReasoned = 10)
    assertEquals(shown.map(_.src.citekey), List("c1", "c2"))
    assert(text.startsWith("6 pairs — 0 addressed, 1 reasoned, 5 open."), text)
    assert(text.contains("- c1>d: [[c1]] (not cited) challenges [[d]] (cited)"), text)
    assert(text.contains("(3 more open pairs not shown, for a later pass: c3>d, c4>d, c5>d)"), text)
    assert(text.contains("- r1>d: off the point"), text)
    // nothing open, nothing reasoned: nothing to say
    assertEquals(Critiques.promptText(List(pair("a", "refutes", "b", true, true, state = CritiqueState.Addressed)), 5, 5), ("", Nil))
    assert(Critiques.promptText(List(why), 5, 5)._1.contains("No pair is open."))

  test("the Method note gains the counts only when there is a pair; coverage shows the open and the reasoned ones"):
    val ps = List(pair("a", "refutes", "b", true, true, state = CritiqueState.Addressed),
      pair("c", "challenges", "b", false, true, state = CritiqueState.Reasoned, reason = Some("not the point")),
      pair("d", "updates", "b", false, true))
    assertEquals(Critiques.methodPart(ps), Some("disagreements checked: 3 pairs — 1 addressed, 1 reasoned, 1 open"))
    assertEquals(Critiques.methodPart(Nil), None)
    val c = Coverage("s", ArticleFilter(topics = List("t")), "search {topics: [\"t\"]}", 1, 1, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil,
      critiques = ps)
    assert(ArticleCoverage.method(c).endsWith("; disagreements checked: 3 pairs — 1 addressed, 1 reasoned, 1 open._"), ArticleCoverage.method(c))
    assert(!ArticleCoverage.method(c.copy(critiques = Nil)).contains("disagreements checked"))
    val md = ArticleCoverage.markdown(c)
    assert(md.contains("### Disagreements checked\n\n3 pairs — 1 addressed, 1 reasoned, 1 open"), md)
    assert(md.contains("- d>b: [[d]] (not cited) updates [[b]] (cited)") && md.contains("- [[c]] challenges [[b]] — not the point"), md)

  test("wire forms: a pair and a reason as the CLI and the models write them"):
    assertEquals(CritiqueRef.parse("[[Smith1987_dying]] > frazer1922_golden"), Right(CritiqueRef("smith1987_dying", "frazer1922_golden")))
    assertEquals(CritiqueRef.parse("a2000_x -> b2001_y").map(_.wire), Right("a2000_x>b2001_y"))
    assertEquals(CritiqueRef.parse("a2000_x → b2001_y").map(_.wire), Right("a2000_x>b2001_y"))
    assert(CritiqueRef.parse("a2000_x").isLeft && CritiqueRef.parse("a>a").isLeft && CritiqueRef.parse(">b").isLeft)
    assertEquals(CritiqueReason.parse("a2000_x>b2001_y=Not the point.  "), Right(CritiqueReason("a2000_x", "b2001_y", "Not the point.")))
    assertEquals(CritiqueReason.parse("a2000_x>b2001_y=").map(_.reason), Right(""))
    assert(CritiqueReason.parse("a2000_x>b2001_y").isLeft)
    assert(CritiqueReason.tidy(CritiqueReason("a", "b", "x" * 501)).isLeft)
    assertEquals(CritiqueReason.tidy(CritiqueReason(" A ", "b", " one\n line ")).map(r => (r.src, r.reason)), Right(("a", "one line")))
    assert(ArticlePatch(critiqueReasons = List(CritiqueReason("a", "b", "x")), ifUpdatedAt = Some("t")).onlyCritiques)
    assert(!ArticlePatch(body = Some("b"), critiqueReasons = List(CritiqueReason("a", "b", "x"))).onlyCritiques)
    assert(!ArticlePatch().onlyCritiques)

  // ── §6.9: blocks with their lines, the criticised side, bearing pairs ─────────────────────────────

  test("units: each paragraph with the body lines it was read from; a hand-written Not used section left out"):
    val body = "# T\n\nOne [[a]] and\ntwo [[b]].\n\n- item [[a]]\n  more [[c]]\n| x [[a]] |\n\n## Not used\n\n- [[a]] — off\n\n## After\n\nLast [[b]]."
    assertEquals(Critiques.units(body).map(u => (u.from, u.to, u.text)), List(
      (2, 3, "One [[a]] and two [[b]]."), (5, 6, "- item [[a]] more [[c]]"), (7, 7, "| x [[a]] |"), (15, 15, "Last [[b]].")))
    assert(Critiques.together(body, CritiqueRef("a", "b"), Some(_)))
    assert(!Critiques.together(body, CritiqueRef("b", "c"), Some(_)))

  test("criticised: the target of challenges/refutes/updates, the source of superseded_by; bearing = open on a cited work"):
    val ch = pair("c1", "challenges", "d1", srcCited = false, dstCited = true)
    assertEquals((ch.criticised.citekey, ch.critic.citekey, ch.bearing), ("d1", "c1", true))
    val sup = pair("old1", "superseded_by", "new1", srcCited = false, dstCited = true)
    assertEquals((sup.criticised.citekey, sup.critic.citekey, sup.bearing), ("old1", "new1", false))
    assert(!pair("c2", "challenges", "d2", srcCited = true, dstCited = false).bearing, "a cited critic of an uncited work: background")
    assert(!pair("c3", "refutes", "d3", true, true, state = CritiqueState.Addressed).bearing)

  test("the skeptic's brief rules only on bearing pairs, once per two sources; the background is named, owing nothing"):
    val ps = List(pair("a1", "refutes", "b1", srcCited = true, dstCited = true), pair("b1", "challenges", "a1", srcCited = true, dstCited = true),
      pair("c2", "challenges", "d2", srcCited = true, dstCited = false))
    assertEquals(Critiques.toRule(ps).map(_.ref.wire), List("a1>b1"))
    val (text, shown) = Critiques.promptText(ps, maxOpen = 10, maxReasoned = 10)
    assertEquals(shown.map(_.ref.wire), List("a1>b1"))
    assert(text.contains("(1 open pair on a work the article does not cite — it states none of that work's claims, so no ruling is owed: c2>d2)"), text)
    val (bg, none) = Critiques.promptText(ps.drop(2), 10, 10)
    assert(bg.contains("No open pair is on a work the article cites.") && none.isEmpty, bg)

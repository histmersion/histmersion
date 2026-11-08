package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

/** The critique checklist (docs/research-review-convergence.md §6.8), pure: the paragraphs "the same paragraph" is
  * judged in, the pair states (alias resolution, both sides cited or one), the ranking and the bound of the skeptic's
  * brief, the generated Method part and the wire forms.
  */

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.{Citekey, Title}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.research.{
  Coverage,
  CritiquePair,
  CritiqueReason,
  CritiqueRef,
  Critiques,
  CritiqueSide,
  CritiqueState
}
import com.histmersion.api.research.Article
import com.histmersion.api.automation.Activity
import com.histmersion.api.library.Filter
class CritiquesSuite extends munit.FunSuite:

  /** `[[x]]` → its canonical citekey: two aliases, one article slug, the rest themselves. */
  private val resolve: Article.Link.Target => Option[Citekey] = t =>
    t.value match
      case "frazer"           => Some(Citekey("frazer1922_golden")) // a short form
      case "frazer1890_bough" => Some(Citekey("frazer1922_golden")) // an old citekey (renamed)
      case "dying_gods"       => None // an article
      case x                  => Some(Citekey(x))

  /** Every link target is itself a citekey. */
  private val selfResolve: Article.Link.Target => Option[Citekey] = t => Some(Citekey(t.value))

  private def edge(a: Citekey, k: Coverage.Edge.Kind, b: Citekey) = Coverage.Edge(a, k, b)
  private val refs: Citekey => Option[(Title, Option[Year])] = Map(
    Citekey("smith1987_dying") -> (Title("Dying and rising gods"), Some(Year(1987))),
    Citekey("frazer1922_golden") -> (Title("The golden bough"), Some(Year(1922)))
  ).get

  test(
    "paragraphs: blank-line blocks, each list item and table row its own; headings, code and a hand-written Not used left out"
  ):
    val body = Article.Body(
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
    )
    assertEquals(
      Critiques.paragraphs(body),
      List("One [[a]] and two [[b]].", "- item [[a]] more of it [[c]]", "- item [[b]]", "| x [[a]] | [[b]] |")
    )

  test("states: addressed in one paragraph (aliases resolve), reasoned by a stored reason either way round, else open"):
    val body = Article.Body(
      """The bough [[frazer]] made the dying god a type; [[smith1987_dying]] refutes the type.
        |
        |The calendar [[frazer1890_bough]] is old.
        |
        |A later study [[jones2001_myth]] reads the rites again; see [[dying_gods]].
        |""".stripMargin
    )
    val edges = List(
      edge(Citekey("smith1987_dying"), Coverage.Edge.Kind("refutes"), Citekey("frazer1922_golden")), // both cited, one paragraph (via a short form)
      edge(Citekey("brown1999_rites"), Coverage.Edge.Kind("challenges"), Citekey("frazer1922_golden")), // critic not cited
      edge(Citekey("jones2001_myth"), Coverage.Edge.Kind("updates"), Citekey("lee1990_ritual")), // cited critic, target not cited
      edge(Citekey("lee1990_ritual"), Coverage.Edge.Kind("challenges"), Citekey("jones2001_myth")), // the same two sources, other way round
      edge(Citekey("brown1999_rites"), Coverage.Edge.Kind("references"), Citekey("frazer1922_golden")), // not a disagreement kind
      edge(Citekey("x2000_a"), Coverage.Edge.Kind("refutes"), Citekey("y2001_b"))
    ) // touches no cited source
    val reasons = List(
      CritiqueReason(
        Citekey("frazer1922_golden"),
        Citekey("brown1999_rites"),
        CritiqueReason.Reason("The article cites it for the calendar only."),
        Some(Activity.Actor("person"))
      )
    )
    val pairs = Critiques.build(body, resolve, edges, reasons, refs)
    assertEquals(
      pairs.map(p => (p.ref.wire, p.kinds, p.state, p.src.cited, p.dst.cited)),
      List(
        ("brown1999_rites>frazer1922_golden", List("challenges"), CritiqueState.Reasoned, false, true),
        ("jones2001_myth>lee1990_ritual", List("updates"), CritiqueState.Open, true, false),
        ("lee1990_ritual>jones2001_myth", List("challenges"), CritiqueState.Open, false, true),
        ("smith1987_dying>frazer1922_golden", List("refutes"), CritiqueState.Addressed, true, true)
      )
    )
    assertEquals(pairs.head.reason, Some("The article cites it for the calendar only."))
    assertEquals(
      pairs.last.dst,
      CritiqueSide(Citekey("frazer1922_golden"), Some(Title("The golden bough")), Some(Year(1922)), cited = CritiqueSide.Cited(true), mentions = CritiqueSide.Mentions(2))
    )
    // a reason on an addressed pair: the text wins
    val both = Critiques.build(
      body,
      resolve,
      edges.take(1),
      List(CritiqueReason(Citekey("smith1987_dying"), Citekey("frazer1922_golden"), CritiqueReason.Reason("x"))),
      refs
    )
    assertEquals(both.map(_.state), List(CritiqueState.Addressed))
    // an empty stored reason is no reason
    assertEquals(
      Critiques.build(body, resolve, edges.slice(1, 2), List(reasons.head.copy(reason = CritiqueReason.Reason(" "))), refs).map(_.state),
      List(CritiqueState.Open)
    )

  test("two kinds between the same two sources are one pair; both sides cited but apart is open"):
    val body = Article.Body("One [[a2000_x]].\n\nTwo [[b2001_y]].")
    val pairs = Critiques.build(
      body,
      selfResolve,
      List(edge(Citekey("b2001_y"), Coverage.Edge.Kind("updates"), Citekey("a2000_x")), edge(Citekey("b2001_y"), Coverage.Edge.Kind("refutes"), Citekey("a2000_x"))),
      Nil
    )
    assertEquals(pairs.map(p => (p.kinds, p.state)), List((List("refutes", "updates"), CritiqueState.Open)))
    assertEquals(
      Critiques.find(pairs, CritiqueRef(Citekey("a2000_x"), Citekey("b2001_y"))).map(_.ref),
      Some(CritiqueRef(Citekey("b2001_y"), Citekey("a2000_x")))
    )

  private def pair(
      src: Citekey,
      kind: CritiquePair.Kinds,
      dst: Citekey,
      srcCited: CritiqueSide.Cited,
      dstCited: CritiqueSide.Cited,
      mentions: CritiqueSide.Mentions = CritiqueSide.Mentions(1),
      state: CritiqueState = CritiqueState.Open,
      reason: Option[CritiquePair.Reason] = None
  ) =
    CritiquePair(
      CritiqueSide(src, cited = srcCited, mentions = if srcCited.value then mentions else CritiqueSide.Mentions(0)),
      List(kind),
      CritiqueSide(dst, cited = dstCited, mentions = if dstCited.value then mentions else CritiqueSide.Mentions(0)),
      state,
      reason
    )

  test(
    "ranked: an uncited critic of a cited source first, refutes before challenges before updates, the more-cited first"
  ):
    val ps = List(
      pair(Citekey("c1"), CritiquePair.Kinds("updates"), Citekey("d1"), CritiqueSide.Cited(false), CritiqueSide.Cited(true), CritiqueSide.Mentions(9)),
      pair(Citekey("c2"), CritiquePair.Kinds("challenges"), Citekey("d2"), CritiqueSide.Cited(true), CritiqueSide.Cited(false)),
      pair(Citekey("c3"), CritiquePair.Kinds("challenges"), Citekey("d3"), CritiqueSide.Cited(false), CritiqueSide.Cited(true), CritiqueSide.Mentions(1)),
      pair(Citekey("c4"), CritiquePair.Kinds("challenges"), Citekey("d4"), CritiqueSide.Cited(false), CritiqueSide.Cited(true), CritiqueSide.Mentions(5)),
      pair(Citekey("c5"), CritiquePair.Kinds("refutes"), Citekey("d5"), CritiqueSide.Cited(true), CritiqueSide.Cited(true)),
      pair(Citekey("c6"), CritiquePair.Kinds("refutes"), Citekey("d6"), CritiqueSide.Cited(false), CritiqueSide.Cited(true)),
      pair(Citekey("c7"), CritiquePair.Kinds("refutes"), Citekey("d7"), CritiqueSide.Cited(false), CritiqueSide.Cited(true), state = CritiqueState.Addressed)
    )
    assertEquals(Critiques.ranked(ps).map(_.src.citekey), List("c6", "c4", "c3", "c1", "c5", "c2"))

  test(
    "the skeptic's brief: the counts, the first N open pairs in full, the rest by name, the reasoned ones with their reasons"
  ):
    val open = (1 to 5).toList.map(i => pair(Citekey(s"c$i"), CritiquePair.Kinds("challenges"), Citekey("d"), CritiqueSide.Cited(false), CritiqueSide.Cited(true)))
    val why = pair(
      Citekey("r1"),
      CritiquePair.Kinds("refutes"),
      Citekey("d"),
      CritiqueSide.Cited(false),
      CritiqueSide.Cited(true),
      state = CritiqueState.Reasoned,
      reason = Some(CritiquePair.Reason("off the point"))
    )
    val (text, shown) = Critiques.promptText(open :+ why, maxOpen = Critiques.OpenLimit(2), maxReasoned = Critiques.ReasonedLimit(10))
    assertEquals(shown.map(_.src.citekey), List("c1", "c2"))
    assert(text.value.startsWith("6 pairs — 0 addressed, 1 reasoned, 5 open."), text)
    assert(text.value.contains("- c1>d: [[c1]] (not cited) challenges [[d]] (cited)"), text)
    assert(text.value.contains("(3 more open pairs not shown, for a later pass: c3>d, c4>d, c5>d)"), text)
    assert(text.value.contains("- r1>d: off the point"), text)
    // nothing open, nothing reasoned: nothing to say
    assertEquals(
      Critiques.promptText(List(pair(Citekey("a"), CritiquePair.Kinds("refutes"), Citekey("b"), CritiqueSide.Cited(true), CritiqueSide.Cited(true), state = CritiqueState.Addressed)), Critiques.OpenLimit(5), Critiques.ReasonedLimit(5)),
      ("", Nil)
    )
    assert(Critiques.promptText(List(why), Critiques.OpenLimit(5), Critiques.ReasonedLimit(5))._1.value.contains("No pair is open."))

  test("the Method note gains the counts only when there is a pair; coverage shows the open and the reasoned ones"):
    val ps = List(
      pair(Citekey("a"), CritiquePair.Kinds("refutes"), Citekey("b"), CritiqueSide.Cited(true), CritiqueSide.Cited(true), state = CritiqueState.Addressed),
      pair(Citekey("c"), CritiquePair.Kinds("challenges"), Citekey("b"), CritiqueSide.Cited(false), CritiqueSide.Cited(true), state = CritiqueState.Reasoned, reason = Some(CritiquePair.Reason("not the point"))),
      pair(Citekey("d"), CritiquePair.Kinds("updates"), Citekey("b"), CritiqueSide.Cited(false), CritiqueSide.Cited(true))
    )
    assertEquals(Critiques.methodPart(ps), Some("disagreements checked: 3 pairs — 1 addressed, 1 reasoned, 1 open"))
    assertEquals(Critiques.methodPart(Nil), None)
    val c = Coverage(
      Article.Slug("s"),
      Article.Filter(topics = List(Filter.Topics("t"))),
      Coverage.FilterLine("search {topics: [\"t\"]}"),
      Coverage.Total(1),
      Coverage.Checked(1),
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      critiques = ps
    )
    assert(
      Article.Coverage.method(c).value.endsWith("; disagreements checked: 3 pairs — 1 addressed, 1 reasoned, 1 open._"),
      Article.Coverage.method(c)
    )
    assert(!Article.Coverage.method(c.modify(_.critiques).setTo(Nil)).value.contains("disagreements checked"))
    val md = Article.Coverage.markdown(c)
    assert(md.value.contains("### Disagreements checked\n\n3 pairs — 1 addressed, 1 reasoned, 1 open"), md)
    assert(
      md.value.contains("- d>b: [[d]] (not cited) updates [[b]] (cited)") && md.value.contains(
        "- [[c]] challenges [[b]] — not the point"
      ),
      md
    )

  test("wire forms: a pair and a reason as the CLI and the models write them"):
    assertEquals(
      CritiqueRef.parse(CritiqueRef.Wire("[[Smith1987_dying]] > frazer1922_golden")),
      Right(CritiqueRef(Citekey("smith1987_dying"), Citekey("frazer1922_golden")))
    )
    assertEquals(CritiqueRef.parse(CritiqueRef.Wire("a2000_x -> b2001_y")).map(_.wire), Right("a2000_x>b2001_y"))
    assertEquals(CritiqueRef.parse(CritiqueRef.Wire("a2000_x → b2001_y")).map(_.wire), Right("a2000_x>b2001_y"))
    assert(CritiqueRef.parse(CritiqueRef.Wire("a2000_x")).isLeft && CritiqueRef.parse(CritiqueRef.Wire("a>a")).isLeft && CritiqueRef.parse(CritiqueRef.Wire(">b")).isLeft)
    assertEquals(
      CritiqueReason.parse(CritiqueReason.Reason("a2000_x>b2001_y=Not the point.  ")),
      Right(CritiqueReason(Citekey("a2000_x"), Citekey("b2001_y"), CritiqueReason.Reason("Not the point.")))
    )
    assertEquals(CritiqueReason.parse(CritiqueReason.Reason("a2000_x>b2001_y=")).map(_.reason), Right(""))
    assert(CritiqueReason.parse(CritiqueReason.Reason("a2000_x>b2001_y")).isLeft)
    assert(CritiqueReason.tidy(CritiqueReason(Citekey("a"), Citekey("b"), CritiqueReason.Reason("x" * 501))).isLeft)
    assertEquals(
      CritiqueReason.tidy(CritiqueReason(Citekey(" A "), Citekey("b"), CritiqueReason.Reason(" one\n line "))).map(r => (r.src, r.reason)),
      Right(("a", "one line"))
    )
    assert(Article.Patch(critiqueReasons = List(CritiqueReason(Citekey("a"), Citekey("b"), CritiqueReason.Reason("x"))), ifUpdatedAt = Some(Article.UpdatedAt("t"))).onlyCritiques)
    assert(!Article.Patch(body = Some(Article.Body("b")), critiqueReasons = List(CritiqueReason(Citekey("a"), Citekey("b"), CritiqueReason.Reason("x")))).onlyCritiques)
    assert(!Article.Patch().onlyCritiques)

  // ── §6.9: blocks with their lines, the criticised side, bearing pairs ─────────────────────────

  test("units: each paragraph with the body lines it was read from; a hand-written Not used section left out"):
    val body = Article.Body(
      "# T\n\nOne [[a]] and\ntwo [[b]].\n\n- item [[a]]\n  more [[c]]\n| x [[a]] |\n\n## Not used\n\n- [[a]] — off\n\n## After\n\nLast [[b]]."
    )
    assertEquals(
      Critiques.units(body).map(u => (u.from, u.to, u.text)),
      List(
        (2, 3, "One [[a]] and two [[b]]."),
        (5, 6, "- item [[a]] more [[c]]"),
        (7, 7, "| x [[a]] |"),
        (15, 15, "Last [[b]].")
      )
    )
    assert(Critiques.together(body, CritiqueRef(Citekey("a"), Citekey("b")), selfResolve))
    assert(!Critiques.together(body, CritiqueRef(Citekey("b"), Citekey("c")), selfResolve))

  test(
    "criticised: the target of challenges/refutes/updates, the source of superseded_by; bearing = open on a cited work"
  ):
    val ch = pair(Citekey("c1"), CritiquePair.Kinds("challenges"), Citekey("d1"), CritiqueSide.Cited(false), CritiqueSide.Cited(true))
    assertEquals((ch.criticised.citekey, ch.critic.citekey, ch.bearing), ("d1", "c1", true))
    val sup = pair(Citekey("old1"), CritiquePair.Kinds("superseded_by"), Citekey("new1"), CritiqueSide.Cited(false), CritiqueSide.Cited(true))
    assertEquals((sup.criticised.citekey, sup.critic.citekey, sup.bearing), ("old1", "new1", false))
    assert(
      !pair(Citekey("c2"), CritiquePair.Kinds("challenges"), Citekey("d2"), CritiqueSide.Cited(true), CritiqueSide.Cited(false)).bearing,
      "a cited critic of an uncited work: background"
    )
    assert(!pair(Citekey("c3"), CritiquePair.Kinds("refutes"), Citekey("d3"), CritiqueSide.Cited(true), CritiqueSide.Cited(true), state = CritiqueState.Addressed).bearing)

  test("the skeptic's brief rules only on bearing pairs, once per two sources; the background is named, owing nothing"):
    val ps = List(
      pair(Citekey("a1"), CritiquePair.Kinds("refutes"), Citekey("b1"), CritiqueSide.Cited(true), CritiqueSide.Cited(true)),
      pair(Citekey("b1"), CritiquePair.Kinds("challenges"), Citekey("a1"), CritiqueSide.Cited(true), CritiqueSide.Cited(true)),
      pair(Citekey("c2"), CritiquePair.Kinds("challenges"), Citekey("d2"), CritiqueSide.Cited(true), CritiqueSide.Cited(false))
    )
    assertEquals(Critiques.toRule(ps).map(_.ref.wire), List("a1>b1"))
    val (text, shown) = Critiques.promptText(ps, maxOpen = Critiques.OpenLimit(10), maxReasoned = Critiques.ReasonedLimit(10))
    assertEquals(shown.map(_.ref.wire), List("a1>b1"))
    assert(
      text.value.contains(
        "(1 open pair on a work the article does not cite — it states none of that work's claims, so no ruling is owed: c2>d2)"
      ),
      text
    )
    val (bg, none) = Critiques.promptText(ps.drop(2), Critiques.OpenLimit(10), Critiques.ReasonedLimit(10))
    assert(bg.value.contains("No open pair is on a work the article cites.") && none.isEmpty, bg)

package histdb.core

import histdb.core.Lint.*

/** The lint classifiers (plan §5.2 stream 2.8) — everything that decides whether a row offends and
  * what the report says about it, with no database in sight. */
class LintSuite extends munit.FunSuite:

  private val corpus = Set(
    "heggarty2023_language", "anthony2015_ringe", "kassian2025_critique",
    "knapp2016_collapse", "knapp2016_seapeoples")

  private def edge(src: String, dst: String, kind: String = "references") = Edge(src, dst, kind)

  // ── rule selection ───────────────────────────────────────────────────────────────────────────

  test("no rules requested = every rule"):
    assertEquals(Lint.select(Nil).map(_.map(_.name)), Right(Lint.names))

  test("a subset is honoured, deduplicated, and blanks are ignored"):
    assertEquals(
      Lint.select(List("no-findings", " ", "no-findings", "no-period")).map(_.map(_.name)),
      Right(List(NoFindings, NoPeriod)))

  test("an unknown rule is an error, never a silently clean report"):
    val e = Lint.select(List("no-findings", "no-vibes")).left.getOrElse("")
    assert(e.contains("no-vibes"), e)
    assert(e.contains("no-findings"), "the error lists the known rules")

  test("the catalog has a description for every rule and no duplicate names"):
    assertEquals(Lint.names.distinct.size, Lint.names.size)
    assert(Lint.all.forall(_.description.trim.nonEmpty))
    assertEquals(Lint.names.size, 13)

  // ── suggestions ──────────────────────────────────────────────────────────────────────────────

  test("a short citekey suggests its full form first"):
    assertEquals(Lint.suggest("heggarty2023", corpus, 3), List("heggarty2023_language"))

  test("an ambiguous short citekey suggests all of them, shortest first"):
    assertEquals(Lint.suggest("knapp2016", corpus, 3), List("knapp2016_collapse", "knapp2016_seapeoples"))

  test("a typo suggests the key it shares a prefix with"):
    assertEquals(Lint.suggest("anthony2015_ringes", corpus, 1), List("anthony2015_ringe"))

  test("nothing close suggests nothing, and the cap is honoured"):
    assertEquals(Lint.suggest("zzz1999_nothing", corpus, 3), Nil)
    assertEquals(Lint.suggest("knapp2016", corpus, 1), List("knapp2016_collapse"))
    assertEquals(Lint.suggest("knapp2016", corpus, 0), Nil)
    assertEquals(Lint.suggest("   ", corpus, 3), Nil)

  // ── dangling edges ───────────────────────────────────────────────────────────────────────────

  test("dangling-edges lists only targets that are not citekeys, with their closest matches"):
    val edges = List(
      edge("anthony2015_ringe", "heggarty2023_language"), // fine
      edge("anthony2015_ringe", "heggarty2023"),          // short form
      edge("kassian2025_critique", "gone2001_missing"))   // truly dead
    val items = Lint.danglingEdges(edges, corpus, 3)
    assertEquals(items.size, 2)
    assert(items.exists(i => i.citekey == "anthony2015_ringe" && i.detail.contains("heggarty2023_language")),
      items.toString)
    assert(items.exists(i => i.citekey == "kassian2025_critique" && !i.detail.contains("closest")),
      items.toString)

  test("a target with a four-digit year is a source reference; a slug is an article link"):
    assert(Lint.looksLikeCitekey("gray_atkinson_2003"))
    assert(Lint.looksLikeCitekey("heggarty2023_language"))
    assert(!Lint.looksLikeCitekey("bronze_age_collapse"))
    assert(!Lint.looksLikeCitekey("oral_tradition"))

  test("citekey-shaped dangling targets sort FIRST, so the item cap keeps the actionable ones"):
    // the seeded corpus has 1 050 article links and exactly ONE dead source reference; the report
    // must not bury it.
    val edges = (1 to 5).toList.map(i => edge("s", s"concept_$i")) :+ edge("s", "gray_atkinson_2003")
    val items = Lint.danglingEdges(edges, corpus, 3)
    assert(items.head.detail.contains("gray_atkinson_2003"), items.head.detail)
    assert(items.head.detail.endsWith("(no such source)"), items.head.detail)
    assert(items(1).detail.contains("looks like a concept/article link"), items(1).detail)

  test("dangling-edges deduplicates and orders by (src, kind, dst)"):
    val e = edge("b_src", "gone", "cites")
    val items = Lint.danglingEdges(List(e, e, edge("a_src", "gone", "cites")), corpus, 1)
    assertEquals(items.map(_.citekey), List("a_src", "b_src"))

  test("unresolved-short-citekeys covers only prefixes of real citekeys, and flags the fixable ones"):
    val edges = List(
      edge("a", "heggarty2023"),      // exactly one expansion → fixable
      edge("b", "knapp2016"),         // two expansions → needs a human
      edge("c", "gone2001_missing"))  // not a short form at all
    val items = Lint.unresolvedShortCitekeys(edges, corpus, 3)
    assertEquals(items.map(_.citekey), List("a", "b"))
    assert(items.head.detail.contains("resolves to heggarty2023_language"), items.head.detail)
    assert(items.head.detail.contains("lint --fix"))
    assert(items(1).detail.contains("ambiguous between knapp2016_collapse, knapp2016_seapeoples"),
      items(1).detail)

  // ── duplicates ───────────────────────────────────────────────────────────────────────────────

  test("duplicates groups case- and whitespace-insensitively, naming the partners"):
    val items = Lint.duplicates(
      List("a" -> "10.1/X", "b" -> " 10.1/x ", "c" -> "10.2/y"), "doi")
    assertEquals(items.map(_.citekey), List("a", "b"))
    assert(items.head.detail.contains("also on b"), items.head.detail)
    assert(items(1).detail.contains("also on a"), items(1).detail)

  test("a key repeated for the SAME citekey is not a duplicate"):
    assertEquals(Lint.duplicates(List("a" -> "10.1/x", "a" -> "10.1/x"), "doi"), Nil)

  test("blank keys are skipped (an unpopulated sha256 column reports nothing)"):
    assertEquals(Lint.duplicates(List("a" -> "", "b" -> "  "), "sha256"), Nil)

  test("blank() is true for absent and whitespace-only values"):
    assert(Lint.blank(None) && Lint.blank(Some("")) && Lint.blank(Some("  \n ")))
    assert(!Lint.blank(Some("x")))

  // ── report assembly and rendering ────────────────────────────────────────────────────────────

  private val rule = Lint.rule(NoFindings).get

  test("count is the true total; items are capped"):
    val items = (1 to 10).toList.map(i => LintItem(s"s$i", "no findings"))
    val r     = Lint.report(rule, items, 3)
    assertEquals(r.count, 10)
    assertEquals(r.items.size, 3)

  test("Markdown groups by rule, shows counts and folds the clean rules into one line"):
    val rep = LintReport(List(
      Lint.report(rule, List(LintItem("a", "x"), LintItem("b", "y")), 1),
      Lint.report(Lint.rule(NoPeriod).get, Nil, 50)))
    val md = Lint.markdown(rep)
    assert(md.contains("### Lint: 2 finding(s) across 1 of 2 rule(s)"), md)
    assert(md.contains("#### no-findings (2)"), md)
    assert(md.contains("- **a** — x"), md)
    assert(md.contains("… and 1 more"), md)
    assert(md.contains("_clean: no-period_"), md)

  test("a clean corpus says so"):
    val md = Lint.markdown(LintReport(List(Lint.report(rule, Nil, 50))))
    assert(md.startsWith("### Lint: clean (1 rule(s) checked)"), md)

  test("applied fixes are reported above the verdict"):
    val md = Lint.markdown(LintReport(List(Lint.report(rule, Nil, 50)), List("resolve-edges: 3")))
    assert(md.startsWith("- fixed: resolve-edges: 3"), md)

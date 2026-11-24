package com.histmersion
package server
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.pluginhost.FetchChain

import com.histmersion.plugin.*

import scala.collection.immutable.ListMap
import com.histmersion.config.CommaList
import com.histmersion.domain.library.ArxivId
import com.histmersion.lint.{LintRule, tolerated}

/** The host side of source fetching (docs/research-plugins.md §5.1): order, the legal gate, the PDF check, the memo and
  * how answers combine — over fake fetchers, no network.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class FetchChainSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Pdf = FetchedFile.Bytes(("%PDF-1.4\n" + "x" * 2048).getBytes("ISO-8859-1"))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Html = FetchedFile.Bytes(("<!DOCTYPE html><html>" + "x" * 2048 + "</html>").getBytes("UTF-8"))
  private val FetchedDoi = domain.library.Doi("10.1234/x")
  private val doi = WorkKey(doi = Some(Doi("10.1234/x")))

  /** A fake fetcher answering `answer`, counting its calls. */
  final private class Fake(val access: Access, answer: (WorkKey, Fetch.Context) => Fetch) extends SourceFetcher:
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
      calls.incrementAndGet(); answer(work, ctx)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def found(
      source: FetchedFile.Source,
      bytes: FetchedFile.Bytes = Pdf,
      ext: FetchedFile.Extension = FetchedFile.Extension("pdf")
  ) =
    Fake(
      Access.Open,
      (_, _) => Fetch.Found(FetchedFile(source, Some(FetchedFile.Url(s"https://$source.example/f")), bytes, ext))
    )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def miss(access: Access = Access.Open) =
    Fake(access, (_, _) => Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("none"))))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────

  test("order: the configured ids first, as listed, then the other registered ones in registration order"):
    assertEquals(
      FetchChain.order(
        List(
          util.plugins.Plugin.Id("a"),
          util.plugins.Plugin.Id("b"),
          util.plugins.Plugin.Id("c"),
          util.plugins.Plugin.Id("d")
        ),
        List(util.plugins.Plugin.Id("c"), util.plugins.Plugin.Id("a"))
      ),
      (List("c", "a", "b", "d"), Nil)
    )
    assertEquals(
      FetchChain
        .order(List(util.plugins.Plugin.Id("a"), util.plugins.Plugin.Id("b")), Nil),
      (List("a", "b"), Nil)
    )

  test("order: a listed id nothing registered is skipped, with a note naming each such id"):
    val (ids, notes) = FetchChain.order(
      List(util.plugins.Plugin.Id("a")),
      CommaList("x, a , , y, x").entries.flatMap(e => util.plugins.Plugin.Id.option(e.value))
    )
    assertEquals(ids, List("a"))
    assertEquals(
      notes.map(_.sentence),
      List("x", "y").map(id => s"histmersion.acquire.chain names '$id', which no enabled plugin registered — skipped")
    )

  test("the legal gate: open and subscription always run; unauthorised only when allowed, never on the legal path"):
    for
      legal <- List(true, false).map(FetchChain.LegalOnly(_));
      allow <- List(true, false).map(FetchChain.AllowUnauthorised(_))
    do
      assert(FetchChain.runs(Access.Open, legal, allow))
      assert(FetchChain.runs(Access.Subscription, legal, allow))
    assert(
      FetchChain.runs(
        Access.Unauthorised,
        legalOnly = FetchChain.LegalOnly(false),
        allowUnauthorised = FetchChain.AllowUnauthorised(true)
      )
    )
    assert(
      !FetchChain.runs(
        Access.Unauthorised,
        legalOnly = FetchChain.LegalOnly(false),
        allowUnauthorised = FetchChain.AllowUnauthorised(false)
      )
    )
    assert(
      !FetchChain.runs(
        Access.Unauthorised,
        legalOnly = FetchChain.LegalOnly(true),
        allowUnauthorised = FetchChain.AllowUnauthorised(true)
      )
    )
    assert(
      !FetchChain.runs(
        Access.Unauthorised,
        legalOnly = FetchChain.LegalOnly(true),
        allowUnauthorised = FetchChain.AllowUnauthorised(false)
      )
    )

  test("the PDF check: the magic bytes within the first KiB of a plausible size"):
    assert(FetchChain.looksLikePdf(Pdf))
    assert(!FetchChain.looksLikePdf(Html))
    assert(!FetchChain.looksLikePdf(FetchedFile.Bytes("%PDF-1.4".getBytes("ISO-8859-1"))), "too small to be a PDF")
    assert(
      !FetchChain.looksLikePdf(FetchedFile.Bytes(("x" * 2000 + "%PDF").getBytes("ISO-8859-1"))),
      "not within the first KiB"
    )

  test("describe names the identifiers, else the title"):
    assertEquals(
      FetchChain.describe(WorkKey(doi = Some(Doi("10.1/x")), arxiv = Some(WorkKey.Arxiv("2101.1")))),
      "doi:10.1/x arxiv:2101.1"
    )
    assertEquals(FetchChain.describe(WorkKey(title = Some(WorkKey.Title("T")))), "\"T\"")
    assertEquals(FetchChain.describe(WorkKey()), "a work with no identifier")

  // ── the chain ───────────────────────────────────────────────────────────────────────────────

  test("within one fetcher, firstFound takes the first copy and tries nothing after it"):
    val asked = java.util.concurrent.atomic.AtomicInteger(0)
    val copy = Fetch.Found(FetchedFile(FetchedFile.Source("b"), None, Pdf, FetchedFile.Extension("pdf")))
    val none = Fetch.Miss(Fetch.Absence.NotHeld, Problem.NotFound.Entity(Problem.TypeName("Copy"), Problem.Raw("x")))
    val tries = Iterator(() => none, () => copy, () => none).map { t =>
      val _ = asked.incrementAndGet(); t()
    }
    assertEquals(Fetch.firstFound(tries, none), copy)
    assertEquals(asked.get, 2)

  test("the first copy wins and the fetchers after it are not asked"):
    val (a, b, c) = (miss(), found(FetchedFile.Source("b")), found(FetchedFile.Source("c")))
    val got = FetchChain(List(FetchChain.Step.Id("a") -> a, FetchChain.Step.Id("b") -> b, FetchChain.Step.Id("c") -> c))
      .fetch(Some(FetchedDoi), None)
    assertEquals(got.map(_.source), Right("b"))
    assertEquals((a.calls.get, b.calls.get, c.calls.get), (1, 1, 0))

  /** Is it the chain's "no copy"? */
  private def copy(p: Problem): Boolean = p match
    case _: Problem.NotFound.Copy => true
    case _                        => false

  test("nothing found: no copy of the work"):
    assertEquals(
      FetchChain(List(FetchChain.Step.Id("a") -> miss())).fetch(Some(FetchedDoi), None).left.map(_.toVector.map(copy)),
      Left(Vector(true))
    )
    assertEquals(
      FetchChain(Nil).fetch(Some(domain.library.Doi("10.1234/x")), None).left.map(_.toVector),
      Left(Vector(Problem.NotFound.Copy(Problem.Raw("doi:10.1234/x"), Vector.empty)))
    )

  test("a 'PDF' that is not one is no copy: the chain moves on"):
    val got = FetchChain(
      List(
        FetchChain.Step.Id("html") -> found(FetchedFile.Source("html"), Html),
        FetchChain.Step.Id("real") -> found(FetchedFile.Source("real"))
      )
    ).fetch(Some(FetchedDoi), None)
    assertEquals(got.map(_.source), Right("real"))

  test("a copy in another format is not PDF-checked (a JATS text, an EPUB)"):
    val xml = FetchedFile.Bytes("<article/>".getBytes("UTF-8"))
    val got = FetchChain(
      List(FetchChain.Step.Id("text") -> found(FetchedFile.Source("text"), xml, FetchedFile.Extension("xml")))
    ).fetch(Some(FetchedDoi), None)
    assertEquals(got.map(f => (f.source, f.extension)), Right(("text", "xml")))

  test("a fetcher that throws is a miss; the chain goes on"):
    val boom = Fake(Access.Open, (_, _) => throw new IllegalStateException("bang"))
    assertEquals(
      FetchChain(List(FetchChain.Step.Id("boom") -> boom, FetchChain.Step.Id("ok") -> found(FetchedFile.Source("ok"))))
        .fetch(Some(FetchedDoi), None)
        .map(_.source),
      Right("ok")
    )

  test("a refusal is asked once, the chain goes on, and a failed fetch says who refused and why"):
    val no = Fake(
      Access.Open,
      (_, _) => Fetch.Refused(Fetch.Refusal.LendingOnly, Problem.Unclassified(Legacy.Text("lending-only")))
    )
    val r = FetchChain(List(FetchChain.Step.Id("no") -> no, FetchChain.Step.Id("a") -> miss()))
      .fetchLegal(Some(FetchedDoi), None)
    assertEquals(r.left.map(_.toVector.map(p => copy(p) -> p.sentence).drop(1)), Left(Vector(false -> "lending-only")))
    assert(r.left.exists(_.toVector.headOption.exists(copy)), r)
    assertEquals(no.calls.get, 1)

  test("no copy: each fetcher that ran gives its answer, in chain order; a copy needs none"):
    val no = Fake(
      Access.Open,
      (_, _) => Fetch.Refused(Fetch.Refusal.BotChallenge, Problem.Unclassified(Legacy.Text("bot challenge")))
    )
    val boom = Fake(Access.Open, (_, _) => throw new IllegalStateException("bang"))
    val off = Fake(
      Access.Unauthorised,
      (_, _) => Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("never asked")))
    )
    val got = FetchChain(
      List(
        FetchChain.Step.Id("a") -> Fake(
          Access.Open,
          (_, _) => Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("no record")))
        ),
        FetchChain.Step.Id("no") -> no,
        FetchChain.Step.Id("boom") -> boom,
        FetchChain.Step.Id("u") -> off
      )
    ).fetchWorkExplained(doi, legalOnly = FetchChain.LegalOnly(false))
    assertEquals(
      got.file.left.map(_.toVector.map(p => copy(p) -> p.sentence).drop(1)),
      Left(Vector(false -> "bot challenge"))
    )
    assertEquals(
      got.answers.map(_.line),
      List(
        "a: no copy — no record",
        "no: refused (scripts refused) — bot challenge",
        got.answers(2).line
      )
    )
    assertEquals(
      got.answers.map(_.said),
      List(
        Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NotHeld),
        Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.BotChallenge),
        Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unanswered)
      )
    )
    assert(
      s"${got.answers(2).line}"
        .startsWith("boom: no answer — fetcher boom failed unexpectedly (java.lang.IllegalStateException"),
      got.answers(2).line
    )
    val hit = FetchChain(
      List(FetchChain.Step.Id("a") -> miss(), FetchChain.Step.Id("b") -> found(FetchedFile.Source("b")))
    ).fetchWorkExplained(doi, legalOnly = FetchChain.LegalOnly(false))
    assertEquals((hit.file.map(_.source), hit.answers), (Right("b"), Nil))

  test("an unauthorised fetcher stays off unless allowed, and never runs on the legal path"):
    def unauthorised = Fake(
      Access.Unauthorised,
      (_, _) => Fetch.Found(FetchedFile(FetchedFile.Source("u"), None, Pdf, FetchedFile.Extension("pdf")))
    )
    val off = unauthorised
    assertEquals(
      FetchChain(List(FetchChain.Step.Id("u") -> off)).fetch(Some(FetchedDoi), None).left.map(_.toVector.map(copy)),
      Left(Vector(true))
    )
    assertEquals(off.calls.get, 0)
    val legal = unauthorised
    assertEquals(
      FetchChain(List(FetchChain.Step.Id("u") -> legal), allowUnauthorised = FetchChain.AllowUnauthorised(true))
        .fetchLegal(Some(FetchedDoi), None)
        .left
        .map(_.toVector.map(copy)),
      Left(Vector(true))
    )
    assertEquals(legal.calls.get, 0)
    val on = unauthorised
    assertEquals(
      FetchChain(
        List(FetchChain.Step.Id("a") -> miss(), FetchChain.Step.Id("u") -> on),
        allowUnauthorised = FetchChain.AllowUnauthorised(true)
      )
        .fetch(Some(FetchedDoi), None)
        .map(_.source),
      Right("u")
    )
    assertEquals(on.calls.get, 1)

  test("fetchWork hands every identifier to the fetchers; fetch/fetchLegal pass the DOI and the arXiv id"):
    val seen = scala.collection.mutable.ListBuffer.empty[WorkKey] // a test double's record of calls
    val spy = Fake(
      Access.Open,
      (w, _) => { seen += w; Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("none"))) }
    )
    val chain = FetchChain(List(FetchChain.Step.Id("spy") -> spy))
    val _ = chain.fetch(Some(domain.library.Doi("10.1/x")), Some(ArxivId("2101.00001")))
    val _ = chain.fetchWork(
      WorkKey(archive = Some(WorkKey.Archive("racesofeurope")), format = Some(WorkKey.Format("pdf"))),
      legalOnly = FetchChain.LegalOnly(true)
    )
    assertEquals(
      seen.toList,
      List(
        WorkKey(doi = Some(Doi("10.1/x")), arxiv = Some(WorkKey.Arxiv("2101.00001"))),
        WorkKey(archive = Some(WorkKey.Archive("racesofeurope")), format = Some(WorkKey.Format("pdf")))
      )
    )

  test("the memo is shared by the fetchers of ONE fetch and computed once; the next fetch starts afresh"):
    val computed = java.util.concurrent.atomic.AtomicInteger(0)
    def record(ctx: Fetch.Context) = ctx.memo(Fetch.Context.MemoKey("test:record:10.1234/x")) {
      computed.incrementAndGet(); Problem.Unclassified(Legacy.Text("rec"))
    }
    val a = Fake(
      Access.Open,
      (_, ctx) => { record(ctx); Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("a"))) }
    )
    val b = Fake(Access.Open, (_, ctx) => Fetch.Miss(Fetch.Absence.NotHeld, record(ctx)))
    val chain = FetchChain(List(FetchChain.Step.Id("a") -> a, FetchChain.Step.Id("b") -> b))
    val _ = chain.fetch(Some(FetchedDoi), None)
    assertEquals(computed.get, 1)
    val _ = chain.fetch(Some(FetchedDoi), None)
    assertEquals(computed.get, 2)

  test("a memo value computed while another is being computed is kept too"):
    val m = FetchChain.Memo()
    val outer = m.get(Fetch.Context.MemoKey("outer"))(m.get(Fetch.Context.MemoKey("inner"))(1) + 1)
    assertEquals(
      (outer, m.get(Fetch.Context.MemoKey("inner"))(99), m.get(Fetch.Context.MemoKey("outer"))(99)),
      (2, 1, 2)
    )

  test(
    "of: the registry in chain order, a note for what is listed and missing and for an unauthorised fetcher left off"
  ):
    val regs = ListMap[util.plugins.Plugin.Id, SourceFetcher](
      util.plugins.Plugin.Id("b") -> miss(),
      util.plugins.Plugin.Id("u") -> miss(Access.Unauthorised),
      util.plugins.Plugin.Id("a") -> found(FetchedFile.Source("a"))
    )
    val (chain, notes) = FetchChain.of(
      regs,
      List(
        util.plugins.Plugin.Id("a"),
        util.plugins.Plugin.Id("gone"),
        util.plugins.Plugin.Id("b")
      ),
      allowUnauthorised = FetchChain.AllowUnauthorised(false)
    )
    assertEquals(chain.plan.map(_._1), List("a", "b", "u"))
    assertEquals(notes.size, 2, notes.map(_.sentence).mkString("\n"))
    assert(notes.exists(_.sentence.contains("'gone'")))
    assert(
      notes.exists(
        _.sentence == "u obtains copies without the rights holder's authorisation and stays off " +
          "(histmersion.acquire.allow-unauthorised = false)"
      ),
      notes
    )
    val (_, allowed) = FetchChain.of(regs, Nil, allowUnauthorised = FetchChain.AllowUnauthorised(true))
    assertEquals(allowed, Nil)

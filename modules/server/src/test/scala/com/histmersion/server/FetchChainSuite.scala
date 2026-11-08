package com.histmersion
package server

import com.histmersion.plugin.Problem
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.pluginhost.FetchChain

import com.histmersion.plugin.*

import scala.collection.immutable.ListMap
import com.histmersion.config.CommaList
import com.histmersion.domain.library.ArxivId

/** The host side of source fetching (docs/research-plugins.md §5.1): order, the legal gate, the PDF check, the memo and
  * how answers combine — over fake fetchers, no network.
  */
class FetchChainSuite extends munit.FunSuite:

  private val Pdf = FetchedFile.Bytes(("%PDF-1.4\n" + "x" * 2048).getBytes("ISO-8859-1"))
  private val Html = FetchedFile.Bytes(("<!DOCTYPE html><html>" + "x" * 2048 + "</html>").getBytes("UTF-8"))
  private val FetchedDoi = com.histmersion.domain.library.Doi("10.1234/x")
  private val doi = WorkKey(doi = Some(Doi("10.1234/x")))

  /** A fake fetcher answering `answer`, counting its calls. */
  final private class Fake(val access: Access, answer: (WorkKey, Fetch.Context) => Fetch) extends SourceFetcher:
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
      calls.incrementAndGet(); answer(work, ctx)

  private def found(
      source: FetchedFile.Source,
      bytes: FetchedFile.Bytes = Pdf,
      ext: FetchedFile.Extension = FetchedFile.Extension("pdf")
  ) =
    Fake(
      Access.Open,
      (_, _) => Fetch.Found(FetchedFile(source, Some(FetchedFile.Url(s"https://$source.example/f")), bytes, ext))
    )
  private def miss(access: Access = Access.Open) = Fake(access, (_, _) => Fetch.Miss(plugin.Problem("none")))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────

  test("order: the configured ids first, as listed, then the other registered ones in registration order"):
    assertEquals(
      FetchChain.order(
        List(
          com.histmersion.util.plugins.Plugin.Id("a"),
          com.histmersion.util.plugins.Plugin.Id("b"),
          com.histmersion.util.plugins.Plugin.Id("c"),
          com.histmersion.util.plugins.Plugin.Id("d")
        ),
        List(com.histmersion.util.plugins.Plugin.Id("c"), com.histmersion.util.plugins.Plugin.Id("a"))
      ),
      (List("c", "a", "b", "d"), Nil)
    )
    assertEquals(
      FetchChain
        .order(List(com.histmersion.util.plugins.Plugin.Id("a"), com.histmersion.util.plugins.Plugin.Id("b")), Nil),
      (List("a", "b"), Nil)
    )

  test("order: a listed id nothing registered is skipped, with one note naming every such id"):
    val (ids, notes) = FetchChain.order(
      List(com.histmersion.util.plugins.Plugin.Id("a")),
      CommaList("x, a , , y, x").entries.flatMap(e => com.histmersion.util.plugins.Plugin.Id.option(e.value))
    )
    assertEquals(ids, List("a"))
    assertEquals(notes.size, 1)
    assert(notes.head.value.contains("'x', 'y'"), notes.head)

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

  test("the first copy wins and the fetchers after it are not asked"):
    val (a, b, c) = (miss(), found(FetchedFile.Source("b")), found(FetchedFile.Source("c")))
    val got = FetchChain(List(FetchChain.Step.Id("a") -> a, FetchChain.Step.Id("b") -> b, FetchChain.Step.Id("c") -> c))
      .fetch(Some(FetchedDoi), None)
    assertEquals(got.map(_.source), Right("b"))
    assertEquals((a.calls.get, b.calls.get, c.calls.get), (1, 1, 0))

  test("nothing found: the message ingest has always reported"):
    assertEquals(
      FetchChain(List(FetchChain.Step.Id("a") -> miss())).fetch(Some(FetchedDoi), None),
      Left(FetchChain.NoCopy)
    )
    assertEquals(
      FetchChain(Nil).fetch(Some(com.histmersion.domain.library.Doi("10.1234/x")), None),
      Left(FetchChain.NoCopy)
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
    val no = Fake(Access.Open, (_, _) => Fetch.Refused(plugin.Problem("lending-only")))
    val r = FetchChain(List(FetchChain.Step.Id("no") -> no, FetchChain.Step.Id("a") -> miss()))
      .fetchLegal(Some(FetchedDoi), None)
    assertEquals(r, Left(s"${FetchChain.NoCopy} (no: lending-only)"))
    assertEquals(no.calls.get, 1)

  test("no copy: each fetcher that ran gives its answer, in chain order; a copy needs none"):
    val no = Fake(Access.Open, (_, _) => Fetch.Refused(plugin.Problem("bot challenge")))
    val boom = Fake(Access.Open, (_, _) => throw new IllegalStateException("bang"))
    val off = Fake(Access.Unauthorised, (_, _) => Fetch.Miss(plugin.Problem("never asked")))
    val got = FetchChain(
      List(
        FetchChain.Step.Id("a") -> Fake(Access.Open, (_, _) => Fetch.Miss(plugin.Problem("no record"))),
        FetchChain.Step.Id("no") -> no,
        FetchChain.Step.Id("boom") -> boom,
        FetchChain.Step.Id("u") -> off
      )
    ).fetchWorkExplained(doi, legalOnly = FetchChain.LegalOnly(false))
    assertEquals(got.file, Left(s"${FetchChain.NoCopy} (no: bot challenge)"))
    assertEquals(
      got.answers.map(_.line),
      List(
        "a: no copy — no record",
        "no: refused — bot challenge",
        "boom: no copy — boom failed: IllegalStateException: bang"
      )
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
      FetchChain(List(FetchChain.Step.Id("u") -> off)).fetch(Some(FetchedDoi), None),
      Left(FetchChain.NoCopy)
    )
    assertEquals(off.calls.get, 0)
    val legal = unauthorised
    assertEquals(
      FetchChain(List(FetchChain.Step.Id("u") -> legal), allowUnauthorised = FetchChain.AllowUnauthorised(true))
        .fetchLegal(Some(FetchedDoi), None),
      Left(FetchChain.NoCopy)
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
    val spy = Fake(Access.Open, (w, _) => { seen += w; Fetch.Miss(plugin.Problem("none")) })
    val chain = FetchChain(List(FetchChain.Step.Id("spy") -> spy))
    val _ = chain.fetch(Some(com.histmersion.domain.library.Doi("10.1/x")), Some(ArxivId("2101.00001")))
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
      computed.incrementAndGet(); Problem("rec")
    }
    val a = Fake(Access.Open, (_, ctx) => { record(ctx); Fetch.Miss(plugin.Problem("a")) })
    val b = Fake(Access.Open, (_, ctx) => Fetch.Miss(record(ctx)))
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
    val regs = ListMap[com.histmersion.util.plugins.Plugin.Id, SourceFetcher](
      com.histmersion.util.plugins.Plugin.Id("b") -> miss(),
      com.histmersion.util.plugins.Plugin.Id("u") -> miss(Access.Unauthorised),
      com.histmersion.util.plugins.Plugin.Id("a") -> found(FetchedFile.Source("a"))
    )
    val (chain, notes) = FetchChain.of(
      regs,
      List(
        com.histmersion.util.plugins.Plugin.Id("a"),
        com.histmersion.util.plugins.Plugin.Id("gone"),
        com.histmersion.util.plugins.Plugin.Id("b")
      ),
      allowUnauthorised = FetchChain.AllowUnauthorised(false)
    )
    assertEquals(chain.plan.map(_._1), List("a", "b", "u"))
    assertEquals(notes.size, 2, notes.mkString("\n"))
    assert(notes.exists(_.value.contains("'gone'")))
    assert(notes.exists(n => n.value.contains("u ") && n.value.contains("stays off")))
    val (_, allowed) = FetchChain.of(regs, Nil, allowUnauthorised = FetchChain.AllowUnauthorised(true))
    assertEquals(allowed, Nil)

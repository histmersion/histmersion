package com.histmersion.server

import com.histmersion.plugin.*
import com.histmersion.server.acquire.FetchChain

import scala.collection.immutable.ListMap

/** The host side of source fetching (docs/research-plugins.md §5.1): order, the legal gate, the PDF
  * check, the memo and how answers combine — over fake fetchers, no network. */
class FetchChainSuite extends munit.FunSuite:

  private val Pdf  = ("%PDF-1.4\n" + "x" * 2048).getBytes("ISO-8859-1")
  private val Html = ("<!DOCTYPE html><html>" + "x" * 2048 + "</html>").getBytes("UTF-8")
  private val doi  = WorkKey(doi = Some("10.1234/x"))

  /** A fake fetcher answering `answer`, counting its calls. */
  private final class Fake(val access: Access, answer: (WorkKey, FetchContext) => Fetch) extends SourceFetcher:
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    def fetch(work: WorkKey, ctx: FetchContext): Fetch =
      calls.incrementAndGet(); answer(work, ctx)

  private def found(source: String, bytes: Array[Byte] = Pdf, ext: String = "pdf") =
    Fake(Access.Open, (_, _) => Fetch.Found(FetchedFile(source, Some(s"https://$source.example/f"), bytes, ext)))
  private def miss(access: Access = Access.Open) = Fake(access, (_, _) => Fetch.Miss("none"))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────

  test("order: the configured ids first, as listed, then the other registered ones in registration order"):
    assertEquals(FetchChain.order(List("a", "b", "c", "d"), List("c", "a")), (List("c", "a", "b", "d"), Nil))
    assertEquals(FetchChain.order(List("a", "b"), Nil), (List("a", "b"), Nil))

  test("order: a listed id nothing registered is skipped, with one note naming every such id"):
    val (ids, notes) = FetchChain.order(List("a"), List("x", " a ", "", "y", "x"))
    assertEquals(ids, List("a"))
    assertEquals(notes.size, 1)
    assert(notes.head.contains("'x', 'y'"), notes.head)

  test("the legal gate: open and subscription always run; unauthorised only when allowed, never on the legal path"):
    for legal <- List(true, false); allow <- List(true, false) do
      assert(FetchChain.runs(Access.Open, legal, allow))
      assert(FetchChain.runs(Access.Subscription, legal, allow))
    assert(FetchChain.runs(Access.Unauthorised, legalOnly = false, allowUnauthorised = true))
    assert(!FetchChain.runs(Access.Unauthorised, legalOnly = false, allowUnauthorised = false))
    assert(!FetchChain.runs(Access.Unauthorised, legalOnly = true, allowUnauthorised = true))
    assert(!FetchChain.runs(Access.Unauthorised, legalOnly = true, allowUnauthorised = false))

  test("the PDF check: the magic bytes within the first KiB of a plausible size"):
    assert(FetchChain.looksLikePdf(Pdf))
    assert(!FetchChain.looksLikePdf(Html))
    assert(!FetchChain.looksLikePdf("%PDF-1.4".getBytes("ISO-8859-1")), "too small to be a PDF")
    assert(!FetchChain.looksLikePdf(("x" * 2000 + "%PDF").getBytes("ISO-8859-1")), "not within the first KiB")

  test("describe names the identifiers, else the title"):
    assertEquals(FetchChain.describe(WorkKey(doi = Some("10.1/x"), arxiv = Some("2101.1"))), "doi:10.1/x arxiv:2101.1")
    assertEquals(FetchChain.describe(WorkKey(title = Some("T"))), "\"T\"")
    assertEquals(FetchChain.describe(WorkKey()), "a work with no identifier")

  // ── the chain ───────────────────────────────────────────────────────────────────────────────

  test("the first copy wins and the fetchers after it are not asked"):
    val (a, b, c) = (miss(), found("b"), found("c"))
    val got = FetchChain(List("a" -> a, "b" -> b, "c" -> c)).fetch(Some("10.1234/x"), None)
    assertEquals(got.map(_.source), Right("b"))
    assertEquals((a.calls.get, b.calls.get, c.calls.get), (1, 1, 0))

  test("nothing found: the message ingest has always reported"):
    assertEquals(FetchChain(List("a" -> miss())).fetch(Some("10.1234/x"), None), Left(FetchChain.NoCopy))
    assertEquals(FetchChain(Nil).fetch(Some("10.1234/x"), None), Left(FetchChain.NoCopy))

  test("a 'PDF' that is not one is no copy: the chain moves on"):
    val got = FetchChain(List("html" -> found("html", Html), "real" -> found("real"))).fetch(Some("10.1234/x"), None)
    assertEquals(got.map(_.source), Right("real"))

  test("a copy in another format is not PDF-checked (a JATS text, an EPUB)"):
    val xml = "<article/>".getBytes("UTF-8")
    val got = FetchChain(List("text" -> found("text", xml, "xml"))).fetch(Some("10.1234/x"), None)
    assertEquals(got.map(f => (f.source, f.extension)), Right(("text", "xml")))

  test("a fetcher that throws is a miss; the chain goes on"):
    val boom = Fake(Access.Open, (_, _) => throw new IllegalStateException("bang"))
    assertEquals(FetchChain(List("boom" -> boom, "ok" -> found("ok"))).fetch(Some("10.1234/x"), None).map(_.source),
      Right("ok"))

  test("a refusal is asked once, the chain goes on, and a failed fetch says who refused and why"):
    val no = Fake(Access.Open, (_, _) => Fetch.Refused("lending-only"))
    val r  = FetchChain(List("no" -> no, "a" -> miss())).fetchLegal(Some("10.1234/x"), None)
    assertEquals(r, Left(s"${FetchChain.NoCopy} (no: lending-only)"))
    assertEquals(no.calls.get, 1)

  test("no copy: each fetcher that ran gives its answer, in chain order; a copy needs none"):
    val no   = Fake(Access.Open, (_, _) => Fetch.Refused("bot challenge"))
    val boom = Fake(Access.Open, (_, _) => throw new IllegalStateException("bang"))
    val off  = Fake(Access.Unauthorised, (_, _) => Fetch.Miss("never asked"))
    val got  = FetchChain(List("a" -> Fake(Access.Open, (_, _) => Fetch.Miss("no record")), "no" -> no, "boom" -> boom,
      "u" -> off)).fetchWorkExplained(doi, legalOnly = false)
    assertEquals(got.file, Left(s"${FetchChain.NoCopy} (no: bot challenge)"))
    assertEquals(got.answers.map(_.line), List("a: no copy — no record", "no: refused — bot challenge",
      "boom: no copy — boom failed: IllegalStateException: bang"))
    val hit = FetchChain(List("a" -> miss(), "b" -> found("b"))).fetchWorkExplained(doi, legalOnly = false)
    assertEquals((hit.file.map(_.source), hit.answers), (Right("b"), Nil))

  test("an unauthorised fetcher stays off unless allowed, and never runs on the legal path"):
    def unauthorised = Fake(Access.Unauthorised, (_, _) => Fetch.Found(FetchedFile("u", None, Pdf, "pdf")))
    val off = unauthorised
    assertEquals(FetchChain(List("u" -> off)).fetch(Some("10.1234/x"), None), Left(FetchChain.NoCopy))
    assertEquals(off.calls.get, 0)
    val legal = unauthorised
    assertEquals(FetchChain(List("u" -> legal), allowUnauthorised = true).fetchLegal(Some("10.1234/x"), None),
      Left(FetchChain.NoCopy))
    assertEquals(legal.calls.get, 0)
    val on = unauthorised
    assertEquals(FetchChain(List("a" -> miss(), "u" -> on), allowUnauthorised = true).fetch(Some("10.1234/x"), None)
      .map(_.source), Right("u"))
    assertEquals(on.calls.get, 1)

  test("fetchWork hands every identifier to the fetchers; fetch/fetchLegal pass the DOI and the arXiv id"):
    val seen = scala.collection.mutable.ListBuffer.empty[WorkKey] // a test double's record of calls
    val spy  = Fake(Access.Open, (w, _) => { seen += w; Fetch.Miss("none") })
    val chain = FetchChain(List("spy" -> spy))
    val _ = chain.fetch(Some("10.1/x"), Some("2101.00001"))
    val _ = chain.fetchWork(WorkKey(archive = Some("racesofeurope"), format = Some("pdf")), legalOnly = true)
    assertEquals(seen.toList, List(WorkKey(doi = Some("10.1/x"), arxiv = Some("2101.00001")),
      WorkKey(archive = Some("racesofeurope"), format = Some("pdf"))))

  test("the memo is shared by the fetchers of ONE fetch and computed once; the next fetch starts afresh"):
    val computed = java.util.concurrent.atomic.AtomicInteger(0)
    def record(ctx: FetchContext) = ctx.memo("test:record:10.1234/x") { computed.incrementAndGet(); "rec" }
    val a = Fake(Access.Open, (_, ctx) => { record(ctx); Fetch.Miss("a") })
    val b = Fake(Access.Open, (_, ctx) => Fetch.Miss(record(ctx)))
    val chain = FetchChain(List("a" -> a, "b" -> b))
    val _ = chain.fetch(Some("10.1234/x"), None)
    assertEquals(computed.get, 1)
    val _ = chain.fetch(Some("10.1234/x"), None)
    assertEquals(computed.get, 2)

  test("a memo value computed while another is being computed is kept too"):
    val m = FetchChain.Memo()
    val outer = m.get("outer") { m.get("inner")(1) + 1 }
    assertEquals((outer, m.get("inner")(99), m.get("outer")(99)), (2, 1, 2))

  test("of: the registry in chain order, a note for what is listed and missing and for an unauthorised fetcher left off"):
    val regs = ListMap[String, SourceFetcher]("b" -> miss(), "u" -> miss(Access.Unauthorised), "a" -> found("a"))
    val (chain, notes) = FetchChain.of(regs, List("a", "gone", "b"), allowUnauthorised = false)
    assertEquals(chain.plan.map(_._1), List("a", "b", "u"))
    assertEquals(notes.size, 2, notes.mkString("\n"))
    assert(notes.exists(_.contains("'gone'")))
    assert(notes.exists(n => n.contains("u ") && n.contains("stays off")))
    val (_, allowed) = FetchChain.of(regs, Nil, allowUnauthorised = true)
    assertEquals(allowed, Nil)

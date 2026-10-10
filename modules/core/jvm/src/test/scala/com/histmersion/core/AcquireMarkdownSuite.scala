package com.histmersion.core

/** The Markdown renderings of the acquisition results — what MCP returns and the CLI prints. Pure,
  * so every branch is cheap to pin: empty results, the `--apply` hints, and the safety notices that
  * must never silently disappear. */
class AcquireMarkdownSuite extends munit.FunSuite:

  private val work = WorkRef(
    doi = Some("10.1038/nature14317"), title = Some("Massive migration from the steppe"),
    year = Some(2015), authors = List("Wolfgang Haak", "Iosif Lazaridis", "Nick Patterson"),
    venue = Some("Nature"), citedByCount = Some(2065), isOa = true, oaStatus = Some("green"),
    citekey = Some("haak2015_massive"))

  test("discover renders a table and flags what is already stored"):
    val md = AcquireMarkdown.discover(DiscoverResult("steppe", 2, List(work, work.copy(inDb = true))))
    assert(md.contains("| citekey | title | year | authors | cited | doi | notes |"))
    assert(md.contains("haak2015_massive"))
    assert(md.contains("in db"))
    assert(md.contains("Wolfgang Haak, Iosif Lazaridis et al."))
    assert(md.contains("[10.1038/nature14317](https://doi.org/10.1038/nature14317)"))

  test("empty results say so instead of rendering an empty table"):
    assert(AcquireMarkdown.discover(DiscoverResult("nothing", 0, Nil)).contains("_No works found"))
    assert(AcquireMarkdown.followups(FollowupsResult("x", None, "s2", 0, 0, Nil)).contains("_Nothing cites"))
    assert(AcquireMarkdown.refs(RefsResult("x", None, "crossref", 0, 0, 0, Nil)).contains("_No reference list"))
    assert(AcquireMarkdown.calibreGaps(CalibreGaps(0, Nil)).contains("_Every stored source is in Calibre._"))

  test("calibre gaps show whether the server stores each original, only when the server says"):
    def gap(ck: String, stored: Option[Boolean]) =
      CalibreGap(ck, "T", List("A"), None, Some(2000), None, None, Nil, Some("unpaywall"), storedOriginal = stored)
    val md = AcquireMarkdown.calibreGaps(CalibreGaps(2, List(gap("a", Some(true)), gap("b", Some(false)))))
    assert(md.contains("| original |") && md.contains("| stored |") && md.contains("| none |"), md)
    assert(md.contains("--file"), md)
    val old = AcquireMarkdown.calibreGaps(CalibreGaps(1, List(gap("a", None))))
    assert(!old.contains("| original |"), old)

  test("a retracted work is marked in the single-work view"):
    val md = AcquireMarkdown.work(work.copy(isRetracted = true))
    assert(md.contains("RETRACTED"))
    assert(md.contains("**Citekey (proposed):** `haak2015_massive`"))

  test("the single-work view tells 'no free copy' from 'nobody said' (#599)"):
    assert(AcquireMarkdown.work(work).contains("**Access:** open access (green)"))
    val closed = AcquireMarkdown.work(work.copy(isOa = false, oaStatus = Some("closed")))
    assert(closed.contains("**Access:** no OA copy known"), closed)
    // a Crossref-only record carries no access information at all
    val unknown = AcquireMarkdown.work(work.copy(isOa = false, oaStatus = None))
    assert(unknown.contains("OA status unknown"), unknown)
    assert(!unknown.contains("no OA copy known"), unknown)

  test("refs interpolates the edge note (not a function value) and counts resolutions"):
    val r = RefsResult("haak2015_massive", Some("10.1038/x"), "crossref", 2, 1, 0,
      List(RefRow(title = Some("A"), citekey = Some("drews1993_bronze")), RefRow(title = Some("B"))))
    val md = AcquireMarkdown.refs(r)
    assert(md.contains("1 of 2 resolve to stored sources; re-run with `--apply`"), md)
    assert(!md.contains("Lambda") && !md.contains("$"), md)
    assert(AcquireMarkdown.refs(r.copy(edgesAdded = 3)).contains("3 `references` edge(s) written"))

  test("an assessment shows the verdict, the notices and whether it was applied"):
    val a = Assessment("haak2015_massive", Some("10.1038/x"), Some("haak2015_massive"), Some(2015),
      Some(3), None, isRetracted = true,
      retractionWatch = List(RetractionNotice(Some("Retraction"), Some("+Fabrication"), Some("2021-12-15"))),
      crossrefUpdates = List(UpdateNotice(Some("retraction"), Some("Retraction"), Some("10.1038/y"))),
      flagged = true, verdict = Acquire.Verdicts.Retracted,
      recommendation = Acquire.recommendation(Acquire.Verdicts.Retracted, Some(3)))
    val md = AcquireMarkdown.assessment(a)
    assert(md.contains("⛔ RETRACTED"))
    assert(md.contains("+Fabrication") && md.contains("2021-12-15"))
    assert(md.contains("Crossref update notices"))
    assert(md.contains("Re-run with `--apply`"))
    assert(AcquireMarkdown.assessment(a.copy(applied = Some("retracted")))
      .contains("Stored `status` set to **retracted**"))

  test("followups show the proposed edge kind, the intents and the citation contexts"):
    val f = FollowupRow(title = Some("A critique"), year = Some(2021), doi = Some("10.1/x"),
      isInfluential = true, intents = List("result"), contexts = List("these data contradict it"),
      proposedKind = Acquire.EdgeKinds.Refutes, citekey = Some("critic2021_x"))
    val md = AcquireMarkdown.followups(FollowupsResult("haak2015_massive", None, "semantic-scholar", 1, 0, List(f)))
    assert(md.contains("**refutes**"))
    assert(md.contains("★"))
    assert(md.contains("> these data contradict it"))
    assert(md.contains("`critic2021_x`"))

  test("a lending-only archive item renders the refusal prominently"):
    val md = AcquireMarkdown.archiveItem(ArchiveItem("x", title = Some("Black Athena"), lendingOnly = true))
    assert(md.contains("Lending-only item"))
    assert(md.contains("borrowed") || md.contains("BORROWED"))

  test("an ingest report lists every step and the manual drop-in when there was no PDF"):
    val r = IngestReport("haak2015_massive", Some("10.1038/x"), Some("T"), created = true, pdf = None,
      edgesAdded = 0, assessment = None,
      steps = List(IngestStep("metadata", true, "ok"), IngestStep("fetch", false, "no OA copy")),
      dropIn = Some("Drop it at …"))
    val md = AcquireMarkdown.ingest(r)
    assert(md.contains("✓ **metadata**"))
    assert(md.contains("✗ **fetch**"))
    assert(md.contains("none found through legal open access"))
    assert(md.contains("> Drop it at …"))

  test("every rendered table keeps its separator row (stripMargin eats a leading '|')"):
    val item = ArchiveItem("x", title = Some("A Book"), creator = List("C"), year = Some(1939),
      files = List(ArchiveFile("a.pdf", Some("Text PDF"), 12.0)))
    val lines = AcquireMarkdown.archiveItem(item).linesIterator.toList
    val sep   = lines.filter(_.contains("---"))
    assert(sep.nonEmpty && sep.forall(_.startsWith("|")), lines.mkString("\n"))
    val ing = AcquireMarkdown.ingest(IngestReport("k", None, Some("T"), true, None, 0,
      Some(Assessment("k", None, None, None, Some(9), None, false, Nil, Nil, false,
        Acquire.Verdicts.Current, "KEEP")),
      List(IngestStep("metadata", true, "ok"))))
    assert(ing.contains("✓ **metadata**"))
    assert(ing.contains("CURRENT"))

  test("pipes in a title cannot break the table"):
    val md = AcquireMarkdown.discover(DiscoverResult("q", 1, List(work.copy(title = Some("A | B")))))
    assert(md.contains("A \\| B"), md)

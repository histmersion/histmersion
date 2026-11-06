package com.histmersion.api

/** The Markdown renderings of the acquisition results — what MCP returns and the CLI prints. Pure, so every branch is
  * cheap to pin: empty results, the `--apply` hints, and the safety notices that must never silently disappear.
  */

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.{
  Acquire,
  AcquireMarkdown,
  ArchiveFile,
  ArchiveItem,
  Assessment,
  CalibreGap,
  CalibreGaps,
  DiscoverResult,
  FollowupRow,
  FollowupsResult,
  RefRow,
  RefsResult,
  RetractionNotice,
  UpdateNotice,
  WorkRef
}
import com.histmersion.api.library.Ingest
class AcquireMarkdownSuite extends munit.FunSuite:

  private val work = WorkRef(
    doi = (Some("10.1038/nature14317")).map(com.histmersion.api.library.Doi.apply),
    title = (Some("Massive migration from the steppe")).map(com.histmersion.api.library.Title.apply),
    year = (Some(2015)).map(com.histmersion.api.catalogue.Year.apply),
    authors = (List("Wolfgang Haak", "Iosif Lazaridis", "Nick Patterson")).map(com.histmersion.api.library.AuthorName.apply),
    venue = (Some("Nature")).map(com.histmersion.api.library.Source.Venue.apply),
    citedByCount = Some(com.histmersion.api.library.Source.CitedByCount(2065)),
    isOa = com.histmersion.api.library.WorkRef.IsOa(true),
    oaStatus = Some(com.histmersion.api.library.WorkRef.OaStatus("green")),
    citekey = (Some("haak2015_massive")).map(com.histmersion.api.library.Citekey.apply)
  )

  test("discover renders a table and flags what is already stored"):
    val md = AcquireMarkdown.discover(DiscoverResult(com.histmersion.api.library.DiscoverResult.Query("steppe"), com.histmersion.api.library.DiscoverResult.Count(2), List(work, work.modify(_.inDb).setTo(true))))
    assert(md.contains("| citekey | title | year | authors | cited | doi | notes |"))
    assert(md.contains("haak2015_massive"))
    assert(md.contains("in db"))
    assert(md.contains("Wolfgang Haak, Iosif Lazaridis et al."))
    assert(md.contains("[10.1038/nature14317](https://doi.org/10.1038/nature14317)"))

  test("empty results say so instead of rendering an empty table"):
    assert(AcquireMarkdown.discover(DiscoverResult(com.histmersion.api.library.DiscoverResult.Query("nothing"), com.histmersion.api.library.DiscoverResult.Count(0), Nil)).contains("_No works found"))
    assert(AcquireMarkdown.followups(FollowupsResult(com.histmersion.api.library.FollowupsResult.Target("x"), None, com.histmersion.api.library.FollowupsResult.Backend("s2"), com.histmersion.api.library.FollowupsResult.Count(0), com.histmersion.api.library.FollowupsResult.EdgesAdded(0), Nil)).contains("_Nothing cites"))
    assert(AcquireMarkdown.refs(RefsResult(com.histmersion.api.library.RefsResult.Target("x"), None, com.histmersion.api.library.RefsResult.Backend("crossref"), com.histmersion.api.library.RefsResult.Count(0), com.histmersion.api.library.RefsResult.Resolved(0), com.histmersion.api.library.RefsResult.EdgesAdded(0), Nil)).contains("_No reference list"))
    assert(AcquireMarkdown.calibreGaps(CalibreGaps(com.histmersion.api.library.CalibreGaps.Count(0), Nil)).contains("_Every stored source is in Calibre._"))

  test("calibre gaps show whether the server stores each original, only when the server says"):
    def gap(ck: String, stored: Option[Boolean]) =
      CalibreGap(ck, com.histmersion.api.library.CalibreGap.Title("T"), List(com.histmersion.api.library.CalibreGap.Authors("A")), None, Some(com.histmersion.api.catalogue.Year(2000)), None, None, Nil, Some(com.histmersion.api.library.CalibreGap.Provenance("unpaywall")), storedOriginal = stored)
    val md = AcquireMarkdown.calibreGaps(CalibreGaps(com.histmersion.api.library.CalibreGaps.Count(2), List(gap("a", Some(true)), gap("b", Some(false)))))
    assert(md.contains("| original |") && md.contains("| stored |") && md.contains("| none |"), md)
    assert(md.contains("--file"), md)
    val old = AcquireMarkdown.calibreGaps(CalibreGaps(com.histmersion.api.library.CalibreGaps.Count(1), List(gap("a", None))))
    assert(!old.contains("| original |"), old)

  test("a retracted work is marked in the single-work view"):
    val md = AcquireMarkdown.work(work.modify(_.isRetracted).setTo(true))
    assert(md.contains("RETRACTED"))
    assert(md.contains("**Citekey (proposed):** `haak2015_massive`"))

  test("the single-work view tells 'no free copy' from 'nobody said' (#599)"):
    assert(AcquireMarkdown.work(work).contains("**Access:** open access (green)"))
    val closed = AcquireMarkdown.work(work.modify(_.isOa).setTo(false).modify(_.oaStatus).setTo(Some("closed")))
    assert(closed.contains("**Access:** no OA copy known"), closed)
    // a Crossref-only record carries no access information at all
    val unknown = AcquireMarkdown.work(work.modify(_.isOa).setTo(false).modify(_.oaStatus).setTo(None))
    assert(unknown.contains("OA status unknown"), unknown)
    assert(!unknown.contains("no OA copy known"), unknown)

  test("refs interpolates the edge note (not a function value) and counts resolutions"):
    val r = RefsResult(
      com.histmersion.api.library.RefsResult.Target("haak2015_massive"),
      Some(com.histmersion.api.library.Doi("10.1038/x")),
      com.histmersion.api.library.RefsResult.Backend("crossref"),
      com.histmersion.api.library.RefsResult.Count(2),
      com.histmersion.api.library.RefsResult.Resolved(1),
      com.histmersion.api.library.RefsResult.EdgesAdded(0),
      List(RefRow(title = (Some("A")).map(com.histmersion.api.library.Title.apply), citekey = (Some("drews1993_bronze")).map(com.histmersion.api.library.Citekey.apply)), RefRow(title = (Some("B")).map(com.histmersion.api.library.Title.apply)))
    )
    val md = AcquireMarkdown.refs(r)
    assert(md.contains("1 of 2 resolve to stored sources; re-run with `--apply`"), md)
    assert(!md.contains("Lambda") && !md.contains("$"), md)
    assert(AcquireMarkdown.refs(r.modify(_.edgesAdded).setTo(3)).contains("3 `references` edge(s) written"))

  test("an assessment shows the verdict, the notices and whether it was applied"):
    val a = Assessment(
      com.histmersion.api.library.Assessment.Target("haak2015_massive"),
      Some(com.histmersion.api.library.Doi("10.1038/x")),
      Some(com.histmersion.api.library.Citekey("haak2015_massive")),
      Some(com.histmersion.api.catalogue.Year(2015)),
      Some(com.histmersion.api.library.Acquire.CitedByCount(3)),
      None,
      isRetracted = com.histmersion.api.library.Assessment.IsRetracted(true),
      retractionWatch = List(RetractionNotice(Some(com.histmersion.api.library.RetractionNotice.Nature("Retraction")), Some(com.histmersion.api.library.RetractionNotice.Reason("+Fabrication")), Some(com.histmersion.api.library.RetractionNotice.Date("2021-12-15")))),
      crossrefUpdates = List(UpdateNotice(Some(com.histmersion.api.library.UpdateNotice.Kind("retraction")), Some(com.histmersion.api.library.UpdateNotice.Label("Retraction")), Some(com.histmersion.api.library.Doi("10.1038/y")))),
      flagged = com.histmersion.api.library.Assessment.Flagged(true),
      verdict = Acquire.Verdicts.Retracted,
      recommendation = Acquire.recommendation(Acquire.Verdicts.Retracted, Some(com.histmersion.api.library.Acquire.CitedByCount(3)))
    )
    val md = AcquireMarkdown.assessment(a)
    assert(md.contains("⛔ RETRACTED"))
    assert(md.contains("+Fabrication") && md.contains("2021-12-15"))
    assert(md.contains("Crossref update notices"))
    assert(md.contains("Re-run with `--apply`"))
    assert(
      AcquireMarkdown
        .assessment(a.modify(_.applied).setTo(Some("retracted")))
        .contains("Stored `status` set to **retracted**")
    )

  test("followups show the proposed edge kind, the intents and the citation contexts"):
    val f = FollowupRow(
      title = Some(com.histmersion.api.library.Title("A critique")),
      year = Some(com.histmersion.api.catalogue.Year(2021)),
      doi = Some(com.histmersion.api.library.Doi("10.1/x")),
      isInfluential = true,
      intents = List("result"),
      contexts = List("these data contradict it"),
      proposedKind = Acquire.EdgeKinds.Refutes,
      citekey = Some("critic2021_x")
    )
    val md = AcquireMarkdown.followups(FollowupsResult(com.histmersion.api.library.FollowupsResult.Target("haak2015_massive"), None, com.histmersion.api.library.FollowupsResult.Backend("semantic-scholar"), com.histmersion.api.library.FollowupsResult.Count(1), com.histmersion.api.library.FollowupsResult.EdgesAdded(0), List(f)))
    assert(md.contains("**refutes**"))
    assert(md.contains("★"))
    assert(md.contains("> these data contradict it"))
    assert(md.contains("`critic2021_x`"))

  test("a lending-only archive item renders the refusal prominently"):
    val md = AcquireMarkdown.archiveItem(ArchiveItem(com.histmersion.api.library.ArchiveItem.Identifier("x"), title = Some(com.histmersion.api.library.ArchiveItem.Title("Black Athena")), lendingOnly = true))
    assert(md.contains("Lending-only item"))
    assert(md.contains("borrowed") || md.contains("BORROWED"))

  test("an ingest report lists every step and the manual drop-in when there was no PDF"):
    val r = Ingest.Report(
      com.histmersion.api.library.Citekey("haak2015_massive"),
      Some(com.histmersion.api.library.Doi("10.1038/x")),
      Some(com.histmersion.api.library.Title("T")),
      created = com.histmersion.api.library.Ingest.Report.Created(true),
      pdf = None,
      edgesAdded = com.histmersion.api.library.Ingest.Report.EdgesAdded(0),
      assessment = None,
      steps = List(Ingest.Step(com.histmersion.api.library.Ingest.Step.Value("metadata"), com.histmersion.api.library.Ingest.Step.Ok(true), com.histmersion.api.library.Ingest.Step.Detail("ok")), Ingest.Step(com.histmersion.api.library.Ingest.Step.Value("fetch"), com.histmersion.api.library.Ingest.Step.Ok(false), com.histmersion.api.library.Ingest.Step.Detail("no OA copy"))),
      dropIn = Some(com.histmersion.api.library.Ingest.DropInPath("Drop it at …"))
    )
    val md = AcquireMarkdown.ingest(r)
    assert(md.contains("✓ **metadata**"))
    assert(md.contains("✗ **fetch**"))
    assert(md.contains("none found through legal open access"))
    assert(md.contains("> Drop it at …"))

  test("every rendered table keeps its separator row (stripMargin eats a leading '|')"):
    val item = ArchiveItem(
      com.histmersion.api.library.ArchiveItem.Identifier("x"),
      title = Some(com.histmersion.api.library.ArchiveItem.Title("A Book")),
      creator = List(com.histmersion.api.library.ArchiveItem.Creator("C")),
      year = Some(com.histmersion.api.catalogue.Year(1939)),
      files = List(ArchiveFile("a.pdf", Some("Text PDF"), 12.0))
    )
    val lines = AcquireMarkdown.archiveItem(item).linesIterator.toList
    val sep = lines.filter(_.contains("---"))
    assert(sep.nonEmpty && sep.forall(_.startsWith("|")), lines.mkString("\n"))
    val ing = AcquireMarkdown.ingest(
      Ingest.Report(
        com.histmersion.api.library.Citekey("k"),
        None,
        Some(com.histmersion.api.library.Title("T")),
        com.histmersion.api.library.Ingest.Report.Created(true),
        None,
        com.histmersion.api.library.Ingest.Report.EdgesAdded(0),
        Some(
          Assessment(com.histmersion.api.library.Assessment.Target("k"), None, None, None, Some(com.histmersion.api.library.Acquire.CitedByCount(9)), None, com.histmersion.api.library.Assessment.IsRetracted(false), Nil, Nil, com.histmersion.api.library.Assessment.Flagged(false), Acquire.Verdicts.Current, com.histmersion.api.library.Assessment.Recommendation("KEEP"))
        ),
        List(Ingest.Step(com.histmersion.api.library.Ingest.Step.Value("metadata"), com.histmersion.api.library.Ingest.Step.Ok(true), com.histmersion.api.library.Ingest.Step.Detail("ok")))
      )
    )
    assert(ing.contains("✓ **metadata**"))
    assert(ing.contains("CURRENT"))

  test("pipes in a title cannot break the table"):
    val md = AcquireMarkdown.discover(DiscoverResult(com.histmersion.api.library.DiscoverResult.Query("q"), com.histmersion.api.library.DiscoverResult.Count(1), List(work.modify(_.title).setTo(Some(com.histmersion.api.library.Title("A | B"))))))
    assert(md.contains("A \\| B"), md)

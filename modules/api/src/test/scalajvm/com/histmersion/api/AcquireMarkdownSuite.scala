package com.histmersion
package api

/** The Markdown renderings of the acquisition results — what MCP returns and the CLI prints. Pure, so every branch is
  * cheap to pin: empty results, the `--apply` hints, and the safety notices that must never silently disappear.
  */

import com.histmersion.util.problem.Problem
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
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AcquireMarkdownSuite extends munit.FunSuite:

  private val work = WorkRef(
    doi = (Some("10.1038/nature14317")).map(Doi.apply),
    title = (Some("Massive migration from the steppe")).map(Title.apply),
    year = (Some(2015)).map(Year.apply),
    authors = (List("Wolfgang Haak", "Iosif Lazaridis", "Nick Patterson")).map(AuthorName.apply),
    venue = (Some("Nature")).map(Source.Venue.apply),
    citedByCount = Some(Source.CitedByCount(2065)),
    isOa = WorkRef.IsOa.OpenAccess,
    oaStatus = Some(WorkRef.OaStatus("green")),
    citekey = (Some("haak2015_massive")).map(Citekey.apply)
  )

  test("discover renders a table and flags what is already stored"):
    val md = AcquireMarkdown.discover(
      DiscoverResult(
        DiscoverResult.Query("steppe"),
        DiscoverResult.Count(2),
        List(work, work.modify(_.inDb).setTo(WorkRef.InDb.InDb))
      )
    )
    assert(md.contains("| citekey | title | year | authors | cited | doi | notes |"))
    assert(md.contains("haak2015_massive"))
    assert(md.contains("in db"))
    assert(md.contains("Wolfgang Haak, Iosif Lazaridis et al."))
    assert(md.contains("[10.1038/nature14317](https://doi.org/10.1038/nature14317)"))

  test("empty results say so instead of rendering an empty table"):
    assert(
      AcquireMarkdown
        .discover(DiscoverResult(DiscoverResult.Query("nothing"), DiscoverResult.Count(0), Nil))
        .contains("_No works found")
    )
    assert(
      AcquireMarkdown
        .followups(
          FollowupsResult(
            FollowupsResult.Target("x"),
            None,
            FollowupsResult.Backend("s2"),
            FollowupsResult.Count(0),
            FollowupsResult.EdgesAdded(0),
            Nil
          )
        )
        .contains("_Nothing cites")
    )
    assert(
      AcquireMarkdown
        .refs(
          RefsResult(
            RefsResult.Target("x"),
            None,
            RefsResult.Backend("crossref"),
            RefsResult.Count(0),
            RefsResult.Resolved(0),
            RefsResult.EdgesAdded(0),
            Nil
          )
        )
        .contains("_No reference list")
    )
    assert(
      AcquireMarkdown
        .calibreGaps(CalibreGaps(CalibreGaps.Count(0), Nil))
        .contains("_Every stored source is in Calibre._")
    )

  test("calibre gaps show whether the server stores each original, only when the server says"):
    def gap(ck: Citekey, stored: Option[CalibreGap.StoredOriginal]) =
      CalibreGap(
        ck,
        CalibreGap.Title("T"),
        List(CalibreGap.Authors("A")),
        None,
        Some(Year(2000)),
        None,
        None,
        Nil,
        Some(CalibreGap.Provenance("unpaywall")),
        storedOriginal = stored
      )
    val md = AcquireMarkdown.calibreGaps(
      CalibreGaps(
        CalibreGaps.Count(2),
        List(
          gap(Citekey("a"), Some(CalibreGap.StoredOriginal.Stored)),
          gap(Citekey("b"), Some(CalibreGap.StoredOriginal.Missing))
        )
      )
    )
    assert(md.contains("| original |") && md.contains("| stored |") && md.contains("| none |"), md)
    assert(md.contains("--file"), md)
    val old = AcquireMarkdown.calibreGaps(CalibreGaps(CalibreGaps.Count(1), List(gap(Citekey("a"), None))))
    assert(!old.contains("| original |"), old)

  test("a retracted work is marked in the single-work view"):
    val md = AcquireMarkdown.work(work.modify(_.isRetracted).setTo(WorkRef.IsRetracted.Retracted))
    assert(md.contains("RETRACTED"))
    assert(md.contains("**Citekey (proposed):** `haak2015_massive`"))

  test("the single-work view tells 'no free copy' from 'nobody said' (#599)"):
    assert(AcquireMarkdown.work(work).contains("**Access:** open access (green)"))
    val closed = AcquireMarkdown.work(
      work.modify(_.isOa).setTo(WorkRef.IsOa.Closed).modify(_.oaStatus).setTo(Some(WorkRef.OaStatus("closed")))
    )
    assert(closed.contains("**Access:** no OA copy known"), closed)
    // a Crossref-only record carries no access information at all
    val unknown = AcquireMarkdown.work(work.modify(_.isOa).setTo(WorkRef.IsOa.Closed).modify(_.oaStatus).setTo(None))
    assert(unknown.contains("OA status unknown"), unknown)
    assert(!unknown.contains("no OA copy known"), unknown)

  test("refs interpolates the edge note (not a function value) and counts resolutions"):
    val r = RefsResult(
      RefsResult.Target("haak2015_massive"),
      Some(Doi("10.1038/x")),
      RefsResult.Backend("crossref"),
      RefsResult.Count(2),
      RefsResult.Resolved(1),
      RefsResult.EdgesAdded(0),
      List(
        RefRow(title = (Some("A")).map(Title.apply), citekey = (Some("drews1993_bronze")).map(Citekey.apply)),
        RefRow(title = (Some("B")).map(Title.apply))
      )
    )
    val md = AcquireMarkdown.refs(r)
    assert(md.contains("1 of 2 resolve to stored sources; re-run with `--apply`"), md)
    assert(!md.contains("Lambda") && !md.contains("$"), md)
    assert(
      AcquireMarkdown
        .refs(r.modify(_.edgesAdded).setTo(RefsResult.EdgesAdded(3)))
        .contains("3 `references` edge(s) written")
    )

  test("an assessment shows the verdict, the notices and whether it was applied"):
    val a = Assessment(
      Assessment.Target("haak2015_massive"),
      Some(Doi("10.1038/x")),
      Some(Citekey("haak2015_massive")),
      Some(Year(2015)),
      Some(Acquire.CitedByCount(3)),
      None,
      isRetracted = Assessment.IsRetracted.Retracted,
      retractionWatch = List(
        RetractionNotice(
          Some(RetractionNotice.Nature("Retraction")),
          Some(RetractionNotice.Account("+Fabrication")),
          Some(RetractionNotice.Date("2021-12-15"))
        )
      ),
      crossrefUpdates = List(
        UpdateNotice(
          Some(UpdateNotice.Kind("retraction")),
          Some(UpdateNotice.Label("Retraction")),
          Some(Doi("10.1038/y"))
        )
      ),
      flagged = Assessment.Flagged.Flagged,
      verdict = Acquire.Verdicts.Retracted,
      recommendation = Assessment.Recommendation(
        Acquire.recommendation(Acquire.Verdicts.Retracted, Some(Acquire.CitedByCount(3))).value
      )
    )
    val md = AcquireMarkdown.assessment(a)
    assert(md.contains("⛔ RETRACTED"))
    assert(md.contains("+Fabrication") && md.contains("2021-12-15"))
    assert(md.contains("Crossref update notices"))
    assert(md.contains("Re-run with `--apply`"))
    assert(
      AcquireMarkdown
        .assessment(a.modify(_.applied).setTo(Some(Assessment.Applied("retracted"))))
        .contains("Stored `status` set to **retracted**")
    )

  test("followups show the proposed edge kind, the intents and the citation contexts"):
    val f = FollowupRow(
      title = Some(Title("A critique")),
      year = Some(Year(2021)),
      doi = Some(Doi("10.1/x")),
      isInfluential = FollowupRow.IsInfluential.Influential,
      intents = List(FollowupRow.Intents("result")),
      contexts = List(FollowupRow.Contexts("these data contradict it")),
      proposedKind = FollowupRow.ProposedKind(Acquire.EdgeKinds.Refutes),
      citekey = Some(Citekey("critic2021_x"))
    )
    val md = AcquireMarkdown.followups(
      FollowupsResult(
        FollowupsResult.Target("haak2015_massive"),
        None,
        FollowupsResult.Backend("semantic-scholar"),
        FollowupsResult.Count(1),
        FollowupsResult.EdgesAdded(0),
        List(f)
      )
    )
    assert(md.contains("**refutes**"))
    assert(md.contains("★"))
    assert(md.contains("> these data contradict it"))
    assert(md.contains("`critic2021_x`"))

  test("a lending-only archive item renders the refusal prominently"):
    val md = AcquireMarkdown.archiveItem(
      ArchiveItem(
        ArchiveItem.Identifier("x"),
        title = Some(ArchiveItem.Title("Black Athena")),
        lendingOnly = ArchiveItem.LendingOnly.LendingOnly
      )
    )
    assert(md.contains("Lending-only item"))
    assert(md.contains("borrowed") || md.contains("BORROWED"))

  test("an ingest report lists every step and the manual drop-in when there was no PDF"):
    val r = Ingest.Report(
      Citekey("haak2015_massive"),
      Some(Doi("10.1038/x")),
      Some(Title("T")),
      created = Ingest.Report.Created.Created,
      pdf = None,
      edgesAdded = Ingest.Report.EdgesAdded(0),
      assessment = None,
      steps = List(
        Ingest.Step(Ingest.Step.Value("metadata"), Ingest.Step.Ok.Ok, Ingest.Step.Detail("ok")),
        Ingest.Step(Ingest.Step.Value("fetch"), Ingest.Step.Ok.Failed, Ingest.Step.Detail("no OA copy"))
      ),
      dropIn = Some(Ingest.DropInPath("Drop it at …"))
    )
    val md = AcquireMarkdown.ingest(r)
    assert(md.contains("✓ **metadata**"))
    assert(md.contains("✗ **fetch**"))
    assert(md.contains("none found through legal open access"))
    assert(md.contains("> Drop it at …"))

  test("every rendered table keeps its separator row (stripMargin eats a leading '|')"):
    val item = ArchiveItem(
      ArchiveItem.Identifier("x"),
      title = Some(ArchiveItem.Title("A Book")),
      creator = List(ArchiveItem.Creator("C")),
      year = Some(Year(1939)),
      files =
        List(ArchiveFile(ArchiveFile.Name("a.pdf"), Some(ArchiveFile.Format("Text PDF")), ArchiveFile.Megabytes(12.0)))
    )
    val lines = AcquireMarkdown.archiveItem(item).linesIterator.toList
    val sep = lines.filter(_.contains("---"))
    assert(sep.nonEmpty && sep.forall(_.startsWith("|")), lines.mkString("\n"))
    val ing = AcquireMarkdown.ingest(
      Ingest.Report(
        Citekey("k"),
        None,
        Some(Title("T")),
        Ingest.Report.Created.Created,
        None,
        Ingest.Report.EdgesAdded(0),
        Some(
          Assessment(
            Assessment.Target("k"),
            None,
            None,
            None,
            Some(Acquire.CitedByCount(9)),
            None,
            Assessment.IsRetracted.Standing,
            Nil,
            Nil,
            Assessment.Flagged.Clear,
            Acquire.Verdicts.Current,
            Assessment.Recommendation("KEEP")
          )
        ),
        List(Ingest.Step(Ingest.Step.Value("metadata"), Ingest.Step.Ok.Ok, Ingest.Step.Detail("ok")))
      )
    )
    assert(ing.contains("✓ **metadata**"))
    assert(ing.contains("CURRENT"))

  test("pipes in a title cannot break the table"):
    val md = AcquireMarkdown.discover(
      DiscoverResult(
        DiscoverResult.Query("q"),
        DiscoverResult.Count(1),
        List(work.modify(_.title).setTo(Some(Title("A | B"))))
      )
    )
    assert(md.contains("A \\| B"), md)

package com.histmersion
package domain

import com.histmersion.domain.library.workflows.Classified
import com.histmersion.domain.library.workflows.StatedPeriod
import com.histmersion.domain.library.workflows.Findings
import com.histmersion.domain.library.workflows.FindingProposal
import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.domain.library.workflows.StatedYear
import com.histmersion.domain.library.workflows.VerifiedFindings
import com.histmersion.domain.library.workflows.VerifiedFinding
import com.histmersion.domain.library.workflows.Geocoded
import com.histmersion.domain.library.workflows.GeocodeChoice
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Severity
import com.histmersion.domain.research.workflows.Report
import com.histmersion.domain.research.workflows.ColdReaderAnswer
import com.histmersion.domain.research.workflows.Check
import com.histmersion.domain.research.workflows.SpotCheck
import com.histmersion.domain.research.workflows.Merged
import com.histmersion.domain.library.workflows.Loaded
import com.histmersion.domain.library.workflows.{AcquisitionSweepRules, Classification, Closed, Frame, MetaChecked}
import com.histmersion.domain.library.workflows.{Notes, Patched, StatusRouted, Upstreamed}
import com.histmersion.Year as StoredYear
import com.histmersion.domain.research.workflows.{Closure, Entry, Ledger, Lesson, LessonSource, Recheck, Status}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.{Legacy, Problem}
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.ListPatch
import com.histmersion.api.research.{CritiqueReason, CritiqueRef}
import com.histmersion.api.library.{EdgesPatch, FindingsPatch, MetadataField, MetadataPatch, WorkRef}
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Doi
import com.histmersion.api.Write
import com.histmersion.api.library.Citekey
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.automation.Activity
import com.histmersion.api.research.Coverage
import com.histmersion.lint.{tolerated, LintRule}

/** Representative values of every record an agent step stores as JSON (the run's checkpoints and the patches it
  * writes), every case and optional field among them. Their encodings are the golden files under `golden/checkpoints/`,
  * written once from the codecs stored runs were written with.
  */
object CheckpointGolden:

  final case class Golden[A](file: String, values: List[A])(using
      val encoder: JsonValueCodec[A],
      val decoder: JsonValueCodec[A]
  )

  private val findingInputs = List(
    Finding.Input(
      Finding.Claim("Tin reached the Aegean from Afghanistan."),
      Some(Finding.Region("Aegean")),
      Some(Longitude(25.1)),
      Some(Latitude(37.9)),
      Some(Year(-2500)),
      Some(Year(-2000)),
      Some(Finding.PeriodName("Early Bronze Age")),
      List(Finding.Tags("tin"), Finding.Tags("trade")),
      Some(Confidence.Wire("medium")),
      Some(Finding.Significance("a long-distance route"))
    ),
    Finding.Input(Finding.Claim("A claim with nothing else."))
  )

  private val metadataPatches = List(
    MetadataPatch(
      (Some("A title")).map(api.library.Title.apply),
      (Some(List("Smith, J.", "Doe, A."))).map(_.map(api.library.AuthorName.apply)),
      (Some(1987)).map(Year.apply),
      (Some("Antiquity")).map(Source.Venue.apply),
      (Some("10.1/x")).map(Doi.apply),
      Some(Source.Status.Contested),
      Some(Source.Provenance("oa")),
      Some(Source.CalibreId("42")),
      MetadataField.values.toList
    ),
    MetadataPatch(status = Some(Source.Status.Current)),
    MetadataPatch()
  )

  val sourcePatches: Golden[Source.Patch] = Golden(
    "source-patch.ndjson",
    List(
      Source.Patch(
        metadata = Some(metadataPatches.head),
        summary = Some(Source.Summary("What the source argues.")),
        body = Some(Source.OcrMarkdown("# Body")),
        notes = Some(Source.Notes("Notes on [[smith1987_tin]].")),
        topics = Some(List(Source.Topics("bronze_age"))),
        regions = Some(List(Source.Regions("Aegean"))),
        period = Some(Period.Input(Year(-3300), Year(-1200))),
        tags = Some(
          ListPatch(
            List(Write.ListItem("tin")),
            List(Write.ListItem("old")),
            Some(List(Write.ListItem("tin"), Write.ListItem("trade")))
          )
        ),
        findings = Some(
          FindingsPatch(findingInputs, List(FindingsPatch.Remove("smith1987_tin#3")), Some(findingInputs.take(1)))
        ),
        edges = Some(
          EdgesPatch(
            List(Edge.Input(Citekey("doe2001_tin"), Edge.Kind.Challenges, Some(Citekey("smith1987_tin")))),
            List(Edge.Input(Citekey("roe1990_tin"), Edge.Kind.Refutes))
          )
        ),
        ifUpdatedAt = Some(Source.UpdatedAt("2026-10-25T10:00:00Z"))
      ),
      Source.Patch(
        metadata = Some(metadataPatches(1)),
        tags = Some(ListPatch(add = List(Write.ListItem("tin")))),
        findings = Some(FindingsPatch(add = findingInputs))
      ),
      Source.Patch(metadata = Some(metadataPatches(2))),
      Source.Patch()
    ) ++
      EnrichSourceSamples.patches
  )

  val proposals: Golden[Proposal] = Golden(
    "proposal.ndjson",
    Proposal(
      Approval.Kind.StatusChange,
      library.Citekey("smith1987_tin"),
      status = Some(Source.Status.Superseded),
      evidence = Proposal.Evidence("Doe shows the route is later."),
      quote = Some(Proposal.Quote("the route is later"))
    ) ::
      Proposal.EdgeKinds.map(k =>
        Proposal(
          Approval.Kind.Edge,
          library.Citekey("doe2001_tin"),
          edgeKind = Some(k),
          target = Some(library.Citekey("smith1987_tin")),
          evidence = Proposal.Evidence("It argues against."),
          quote = Some(Proposal.Quote("we disagree"))
        )
      ) ++
      Approval.Kind.values.toList.map(k => Proposal(k, library.Citekey("smith1987_tin")))
  )

  /** What [[AgentProposals.Proposal.patch]] writes for each proposal. */
  val proposalPatches: Golden[Source.Patch] =
    Golden("proposal-patch.ndjson", proposals.values.map(_.patch))

  val classified: Golden[Classified] = Golden(
    "enrich-classified.ndjson",
    Classified(
      Classified.Summary("A summary."),
      List(EnrichSource.Topic("bronze_age")),
      List(EnrichSource.Region("Aegean")),
      List(EnrichSource.Tag("tin")),
      Some(Period(Year(-2500), Year(-2000))),
      Some(StatedPeriod.Name("Early Bronze Age")),
      Source.Status.Current,
      Classified.StatusEvidence("It is cited as current.")
    ) ::
      Source.Status.values.toList.map(s =>
        Classified(Classified.Summary("A summary."), Nil, Nil, Nil, None, None, s, Classified.StatusEvidence(""))
      )
  )

  val findings: Golden[Findings] = Golden(
    "enrich-findings.ndjson",
    List(
      Findings(
        List(
          FindingProposal(
            FindingProposal.Claim("Tin came from the east."),
            Proposal.Quote("tin came from the east"),
            Some(FindingProposal.Place("Troy")),
            Some(KindHint.Wire("site")),
            Some(StatedYear(AgentVerify.ToSignedYearValue(2500), AgentVerify.YearUnit.Wire("BCE"))),
            Some(StatedYear(AgentVerify.ToSignedYearValue(2000), AgentVerify.YearUnit.Wire("BCE"))),
            Some(FindingProposal.PeriodName("EBA")),
            List(EnrichSource.Tag("tin")),
            library.Confidence.Wire("high")
          ),
          FindingProposal(
            FindingProposal.Claim("A bare claim."),
            Proposal.Quote("a bare claim"),
            None,
            None,
            None,
            None,
            None,
            Nil,
            library.Confidence.Wire("low")
          )
        )
      ),
      Findings()
    )
  )

  val verifiedFindings: Golden[VerifiedFindings] = Golden(
    "enrich-verified-findings.ndjson",
    List(
      VerifiedFindings(
        VerifiedFinding(
          VerifiedFinding.Claim("A bare claim."),
          VerifiedFinding.Quote("a bare claim"),
          AgentVerify.Ratio(0.91),
          None,
          None,
          None,
          None,
          Nil,
          VerifiedFinding.Confidence("low")
        ) ::
          KindHint.values.toList.map(k =>
            VerifiedFinding(
              VerifiedFinding.Claim("Tin came from the east."),
              VerifiedFinding.Quote("tin came from the east"),
              AgentVerify.Ratio(1.0),
              Some(VerifiedFinding.Place("Troy")),
              Some(Year(-2500)),
              Some(Year(-2000)),
              Some(VerifiedFinding.PeriodName("EBA")),
              List(EnrichSource.Tag("tin"), EnrichSource.Tag("trade")),
              VerifiedFinding.Confidence("high"),
              Some(k)
            )
          ),
        Some(VerifiedFindings.Proposed(7)),
        Some(VerifiedFindings.QuoteFailed(1))
      ),
      VerifiedFindings(Nil)
    )
  )

  val geocoded: Golden[Geocoded] = Golden(
    "enrich-geocoded.ndjson",
    List(
      Geocoded(
        findingInputs,
        List(
          GeocodeChoice(
            GeocodeChoice.Claim("Tin came from the east."),
            GeocodeChoice.Asked("Troy"),
            Some(Place.Id.assume("troy")),
            Some(GeocodeChoice.Label("Troy")),
            Some(GeocodeChoice.Approximate("a qualifier was dropped"))
          ),
          GeocodeChoice(GeocodeChoice.Claim("Nowhere."), GeocodeChoice.Asked("Atlantis"), None, None, None)
        )
      ),
      Geocoded(Nil, Nil)
    )
  )

  private val frames = List(
    Frame(Frame.Index(0), Some(Frame.Heading("Introduction")), Frame.Text("Tin came from the east.")),
    Frame(Frame.Index(1), None, Frame.Text(""))
  )

  val loaded: Golden[Loaded] = Golden(
    "enrich-loaded.ndjson",
    List(
      Loaded(
        library.Citekey("smith1987_tin"),
        Loaded.Title("Tin in the Aegean"),
        List(AuthorName("Smith, J."), AuthorName("Doe, A.")),
        Some(StoredYear(1987)),
        Some(Loaded.Venue("Antiquity")),
        Source.Status.Wire("current"),
        Loaded.HasSummary(true),
        Loaded.HasNotes(false),
        Loaded.HasPeriod(true),
        Some(Period(Year(-2500), Year(-2000))),
        List(EnrichSource.Topic("bronze_age")),
        List(EnrichSource.Region("Aegean")),
        List(Loaded.ExistingClaims("Tin came from the east.")),
        Some(Source.UpdatedAt("2026-11-28T10:00:00Z")),
        Loaded.Head("Tin in the Aegean. Smith 1987."),
        frames,
        List(EnrichSource.Tag("tin")),
        Some(library.Doi("10.1/x"))
      ),
      Loaded(
        library.Citekey("doe2001_tin"),
        Loaded.Title(""),
        Nil,
        None,
        None,
        Source.Status.Wire("unassessed"),
        Loaded.HasSummary(false),
        Loaded.HasNotes(false),
        Loaded.HasPeriod(false),
        None,
        Nil,
        Nil,
        Nil,
        None,
        Loaded.Head(""),
        Nil
      )
    )
  )

  val metaChecked: Golden[MetaChecked] = Golden(
    "enrich-meta-checked.ndjson",
    List(
      MetaChecked(
        MetaChecked.Checked(true),
        List(MetaChecked.Differences("year: 1987 vs 1988")),
        MetaChecked.Note("n")
      ),
      MetaChecked(MetaChecked.Checked(false), Nil)
    )
  )

  val patched: Golden[Patched] = Golden(
    "enrich-patched.ndjson",
    List(
      Patched(
        Patched.FindingsAdded(3),
        List(Patched.FieldsSet("summary"), Patched.FieldsSet("topics")),
        Some(Patched.StatusProposal("contested"))
      ),
      Patched(Patched.FindingsAdded(0), Nil, None)
    )
  )

  val upstreamed: Golden[Upstreamed] = Golden(
    "enrich-upstreamed.ndjson",
    List(
      Upstreamed(Upstreamed.Ran(true), Upstreamed.Summary("12 references"), Upstreamed.Retracted(true)),
      Upstreamed(Upstreamed.Ran(false), Upstreamed.Summary(""))
    )
  )

  val statusRouted: Golden[StatusRouted] = Golden(
    "enrich-status-routed.ndjson",
    List(
      StatusRouted(
        Some(StatusRouted.Applied("contested")),
        Some(StatusRouted.Approval(42L)),
        StatusRouted.Note("routed")
      ),
      StatusRouted(None, None, StatusRouted.Note(""))
    )
  )

  val closed: Golden[Closed] = Golden(
    "enrich-closed.ndjson",
    List(
      Closed(Closed.Line("enriched"), List(Closed.Lint("no period")), Some(Closed.Acquire("doe2001_tin"))),
      Closed(Closed.Line(""), Nil, None)
    )
  )

  val classifications: Golden[Classification] = Golden(
    "enrich-classification.ndjson",
    List(
      Classification(
        Classification.Summary("A summary."),
        List(EnrichSource.Topic("bronze_age")),
        List(EnrichSource.Region("Aegean")),
        List(EnrichSource.Tag("tin")),
        Some(
          StatedPeriod(
            StatedYear(AgentVerify.ToSignedYearValue(2500), AgentVerify.YearUnit.Wire("BCE")),
            StatedYear(AgentVerify.ToSignedYearValue(2000), AgentVerify.YearUnit.Wire("BCE")),
            Some(StatedPeriod.Name("Early Bronze Age"))
          )
        ),
        library.Source.Status.Wire("current"),
        Classification.StatusEvidence("It is cited as current.")
      ),
      Classification(
        Classification.Summary(""),
        period = None,
        status = library.Source.Status.Wire("unassessed"),
        statusEvidence = Classification.StatusEvidence("")
      )
    )
  )

  val notes: Golden[Notes] = Golden("enrich-notes.ndjson", List(Notes(Notes.Value("See [[smith1987_tin]]."))))

  val candidates: Golden[AcquisitionSweepRules.Candidate] = Golden(
    "acquisition-candidate.ndjson",
    List(
      AcquisitionSweepRules.Candidate(
        library.Doi("10.1/x"),
        api.library.Title("Tin in the Aegean"),
        Some(Year(1987)),
        List(api.library.AuthorName("Smith, J.")),
        Some(Source.Venue("Antiquity")),
        WorkRef.IsOa(true),
        AcquisitionSweepRules.Candidate.Query("bronze age tin"),
        Some(WorkRef.AbstractText("An abstract."))
      ),
      AcquisitionSweepRules.Candidate(
        library.Doi("10.2/y"),
        api.library.Title(""),
        None,
        Nil,
        None,
        WorkRef.IsOa(false),
        AcquisitionSweepRules.Candidate.Query(""),
        None
      )
    )
  )

  val rankings: Golden[AcquisitionSweepRules.Ranking] = Golden(
    "acquisition-ranking.ndjson",
    List(
      AcquisitionSweepRules.Ranking(
        List(
          AcquisitionSweepRules.Pick(
            AcquisitionSweepRules.Pick.DoiText("10.1/x"),
            AcquisitionSweepRules.Rationale("on the goal")
          )
        )
      ),
      AcquisitionSweepRules.Ranking()
    )
  )

  private val evidence =
    List(
      domain.research.workflows.Quotation(
        library.Citekey("smith1987_tin"),
        Some(Offset(120)),
        domain.research.workflows.Quotation.Quote("the route is later"),
        domain.research.workflows.Quotation.Ratio(0.97)
      ),
      domain.research.workflows.Quotation(
        library.Citekey("doe2001_tin"),
        None,
        domain.research.workflows.Quotation.Quote("q"),
        domain.research.workflows.Quotation.Ratio(1.0)
      )
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val reviewFindings: Golden[domain.research.workflows.Finding] = Golden(
    "review-finding.ndjson",
    domain.research.workflows.Finding(
      domain.research.workflows.Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      domain.research.workflows.Finding.Kind("missing-critique"),
      domain.research.workflows.Finding.Where("## Origins"),
      domain.research.workflows.Finding.Quote("tin came"),
      domain.research.workflows.Finding.Problem("The critique is not cited."),
      domain.research.workflows.Finding.Fix("Cite it."),
      evidence,
      escalated = domain.research.workflows.Finding.Escalated(true),
      pair = Some(CritiqueRef(Citekey("doe2001_tin"), Citekey("smith1987_tin")))
    ) ::
      (for r <- Reviewer.values.toList; s <- Severity.values.toList
      yield domain.research.workflows.Finding(
        domain.research.workflows.Finding.Id(s"${r.prefix}1"),
        r,
        s,
        domain.research.workflows.Finding.Kind("style"),
        domain.research.workflows.Finding.Where(""),
        domain.research.workflows.Finding.Quote(""),
        domain.research.workflows.Finding.Problem("A problem."),
        domain.research.workflows.Finding.Fix("")
      ))
  )

  val reports: Golden[Report] = Golden(
    "review-report.ndjson",
    Report(
      Reviewer.Rules,
      domain.research.workflows.Verdict.Major,
      reviewFindings.values.take(2),
      Some(ColdReaderAnswer.ExplainBack("It says the route is later.")),
      List(ReviewArticle.ExplainSlotsValue("disagreement")),
      proposals.values.take(2),
      List(Check(Check.Id("R1"), fixed = Check.Fixed(true), Check.Quote("a quote"), Check.Note("fixed in ## Origins"))),
      List(
        SpotCheck(
          library.Citekey("smith1987_tin"),
          SpotCheck.Claim("tin came"),
          SpotCheck.Verdict("attributable"),
          SpotCheck.RecordQuote("the route is later"),
          SpotCheck.Ratio(0.98),
          SpotCheck.Tool("get_source")
        )
      ),
      List(
        CritiqueReason(
          Citekey("doe2001_tin"),
          Citekey("smith1987_tin"),
          Coverage.Rationale("Not about the route."),
          Some(Activity.Actor("skeptic (agent run 1234abcd)"))
        ),
        CritiqueReason(
          Citekey("roe1990_tin"),
          Citekey("smith1987_tin"),
          Coverage.Rationale("Not material.")
        )
      )
    ) ::
      domain.research.workflows.Verdict.values.toList.map(v => Report(Reviewer.ColdReader, v, Nil))
  )

  val merged: Golden[Merged] = Golden(
    "review-merged.ndjson",
    Merged(
      Merged.Round(2),
      domain.research.workflows.Verdict.Major,
      reviewFindings.values.take(3),
      Some(ColdReaderAnswer.ExplainBack("Explained back.")),
      List(ReviewArticle.ExplainSlotsValue("open")),
      proposals.values.take(1),
      List(domain.research.workflows.Finding.Id("S1"))
    ) ::
      domain.research.workflows.Verdict.values.toList.map(v => Merged(Merged.Round(1), v, Nil, None, Nil, Nil))
  )

  val workRefs: Golden[WorkRef] = Golden(
    "work-ref.ndjson",
    List(
      WorkRef(
        (Some("10.1/x")).map(Doi.apply),
        Some(WorkRef.OpenalexId("W123")),
        Some(Source.Candidate.ArxivId("2101.00001")),
        (Some("Tin and trade")).map(api.library.Title.apply),
        (Some(1987)).map(Year.apply),
        (List("J. Smith")).map(api.library.AuthorName.apply),
        (Some("Antiquity")).map(Source.Venue.apply),
        Some(Source.CitedByCount(12)),
        isOa = WorkRef.IsOa.OpenAccess,
        Some(WorkRef.OaStatus("gold")),
        Some(WorkRef.OaUrl("https://example.org/x.pdf")),
        isRetracted = WorkRef.IsRetracted.Retracted,
        Some(WorkRef.WorkType("article")),
        Some(Source.Candidate.AbstractText("An abstract.")),
        List(WorkRef.ReferencedWorks("W1"), WorkRef.ReferencedWorks("W2")),
        (Some("smith1987_tin")).map(Citekey.apply),
        inDb = WorkRef.InDb.InDb,
        List(WorkRef.OaLocations("https://example.org/x.pdf")),
        Some(WorkRef.OaLicence("cc-by"))
      ),
      WorkRef()
    )
  )

  private val runId = automation.Run.Id("0123abcd-0000-4000-8000-0000000000aa")

  /** The review ledger an article's review keeps in the database (`ReviewLedgerRepo`), every status among its entries.
    */
  val ledgers: Golden[Ledger] = Golden(
    "review-ledger.ndjson",
    List(
      Ledger(
        research.Slug("tin_trade"),
        Status.values.toList.zip(reviewFindings.values).map { (s, f) =>
          Entry(
            f,
            runId,
            Merged.Round(2),
            s,
            Entry.Claimed(true),
            Some(Entry.NotFixed("still there")),
            Entry.Reopened(1),
            Some(
              Closure(
                Reviewer.Wire("rules"),
                Merged.Round(2),
                runId,
                Closure.Note("fixed"),
                domain.research.workflows.Quotation.Quote("the route"),
                Some(library.Citekey("smith1987_tin"))
              )
            ),
            Entry.NotFixedCount(2),
            Some(runId)
          )
        } :+ Entry(reviewFindings.values.head, runId, Merged.Round(1)),
        LessonSource.values.toList.map(src =>
          Lesson(Recheck.Lessons("Quote the record."), src, runId, Merged.Round(1))
        ),
        Some(Ledger.Task(908))
      ),
      Ledger(research.Slug("empty"))
    )
  )

  val all: List[Golden[?]] = List(
    sourcePatches,
    proposals,
    proposalPatches,
    classified,
    findings,
    verifiedFindings,
    geocoded,
    reviewFindings,
    reports,
    merged,
    workRefs,
    loaded,
    metaChecked,
    patched,
    upstreamed,
    statusRouted,
    closed,
    classifications,
    notes,
    candidates,
    rankings,
    ledgers
  )

/** The patches `enrich-source` builds from its checkpoints: one that fills everything, one that fills nothing. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
private object EnrichSourceSamples:
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private val loaded = Loaded(
    library.Citekey("smith1987_tin"),
    Loaded.Title("Tin"),
    List(AuthorName("Smith, J.")),
    Some(com.histmersion.Year(1987)),
    None,
    Source.Status.Wire("unassessed"),
    hasSummary = Loaded.HasSummary(false),
    hasNotes = Loaded.HasNotes(false),
    hasPeriod = Loaded.HasPeriod(false),
    None,
    List(EnrichSource.Topic("trade")),
    Nil,
    Nil,
    Some(Source.UpdatedAt("2026-10-25T10:00:00Z")),
    Loaded.Head("head"),
    Nil,
    List(EnrichSource.Tag("tin"))
  )
  private val classified = Classified(
    Classified.Summary("A summary."),
    List(EnrichSource.Topic("bronze_age")),
    List(EnrichSource.Region("Aegean")),
    List(EnrichSource.Tag("tin"), EnrichSource.Tag("trade")),
    Some(Period(Year(-2500), Year(-2000))),
    None,
    Source.Status.Current,
    Classified.StatusEvidence("cited as current")
  )

  val patches: List[Source.Patch] = List(
    loaded
      .patch(
        classified,
        List(Finding.Input(Finding.Claim("A claim."), lon = Some(Longitude(1.0)), lat = Some(Latitude(2.0)))),
        Some(EnrichSource.PatchNotes("Notes."))
      )
      ._1,
    loaded
      .modify(_.status)
      .setTo(Source.Status.Wire("current"))
      .modify(_.hasSummary)
      .setTo(Loaded.HasSummary(true))
      .modify(_.hasNotes)
      .setTo(Loaded.HasNotes(true))
      .modify(_.hasPeriod)
      .setTo(Loaded.HasPeriod(true))
      .modify(_.topics)
      .setTo(List(EnrichSource.Topic("bronze_age")))
      .modify(_.regions)
      .setTo(List(EnrichSource.Region("Aegean")))
      .modify(_.tags)
      .setTo(List(EnrichSource.Tag("tin"), EnrichSource.Tag("trade")))
      .patch(classified.modify(_.status).setTo(Source.Status.Contested), Nil, None)
      ._1
  )

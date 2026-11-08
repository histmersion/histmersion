package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.ListPatch
import com.histmersion.api.research.{CritiqueReason, CritiqueRef}
import com.histmersion.api.library.{EdgesPatch, FindingsPatch, MetadataField, MetadataPatch, WorkRef}
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.EnrichSource.{
  Classified,
  FindingProposal,
  Findings,
  GeocodeChoice,
  Geocoded,
  StatedYear,
  VerifiedFinding,
  VerifiedFindings
}
import com.histmersion.domain.ReviewArticle.{Check, Evidence, Merged, Report, Reviewer, Severity, SpotCheck, Verdict}
import io.circe.{Decoder, Encoder}
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

/** Representative values of every record an agent step stores as JSON (the run's checkpoints and the patches it
  * writes), every case and optional field among them. Their encodings are the golden files under `golden/checkpoints/`,
  * written once from the codecs stored runs were written with.
  */
object CheckpointGolden:

  final case class Golden[A](file: String, values: List[A])(using val encoder: Encoder[A], val decoder: Decoder[A])

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
      (Some("A title")).map(com.histmersion.api.library.Title.apply),
      (Some(List("Smith, J.", "Doe, A."))).map(_.map(com.histmersion.api.library.AuthorName.apply)),
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
      com.histmersion.domain.library.Citekey("smith1987_tin"),
      status = Some(Source.Status.Superseded),
      evidence = Proposal.Evidence("Doe shows the route is later."),
      quote = Some(Proposal.Quote("the route is later"))
    ) ::
      AgentProposals.EdgeKinds.map(k =>
        Proposal(
          Approval.Kind.Edge,
          com.histmersion.domain.library.Citekey("doe2001_tin"),
          edgeKind = Some(k),
          target = Some(com.histmersion.domain.library.Citekey("smith1987_tin")),
          evidence = Proposal.Evidence("It argues against."),
          quote = Some(Proposal.Quote("we disagree"))
        )
      ) ++
      Approval.Kind.values.toList.map(k => Proposal(k, com.histmersion.domain.library.Citekey("smith1987_tin")))
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
      Some(EnrichSource.StatedPeriod.Name("Early Bronze Age")),
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
            Some(Geocode.KindHint.Wire("site")),
            Some(StatedYear(AgentVerify.ToSignedYearValue(2500), AgentVerify.YearUnit.Wire("BCE"))),
            Some(StatedYear(AgentVerify.ToSignedYearValue(2000), AgentVerify.YearUnit.Wire("BCE"))),
            Some(FindingProposal.PeriodName("EBA")),
            List(EnrichSource.Tag("tin")),
            com.histmersion.domain.library.Confidence.Wire("high")
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
            com.histmersion.domain.library.Confidence.Wire("low")
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
          Geocode.KindHint.values.toList.map(k =>
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

  private val evidence =
    List(
      Evidence(
        com.histmersion.domain.library.Citekey("smith1987_tin"),
        Some(Offset(120)),
        Evidence.Quote("the route is later"),
        Evidence.Ratio(0.97)
      ),
      Evidence(com.histmersion.domain.library.Citekey("doe2001_tin"), None, Evidence.Quote("q"), Evidence.Ratio(1.0))
    )

  val reviewFindings: Golden[ReviewArticle.Finding] = Golden(
    "review-finding.ndjson",
    ReviewArticle.Finding(
      ReviewArticle.Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      ReviewArticle.Finding.Kind("missing-critique"),
      ReviewArticle.Finding.Where("## Origins"),
      ReviewArticle.Finding.Quote("tin came"),
      ReviewArticle.Finding.Problem("The critique is not cited."),
      ReviewArticle.Finding.Fix("Cite it."),
      evidence,
      escalated = ReviewArticle.Finding.Escalated(true),
      pair = Some(CritiqueRef(Citekey("doe2001_tin"), Citekey("smith1987_tin")))
    ) ::
      (for r <- Reviewer.values.toList; s <- Severity.values.toList
      yield ReviewArticle.Finding(
        ReviewArticle.Finding.Id(s"${r.prefix}1"),
        r,
        s,
        ReviewArticle.Finding.Kind("style"),
        ReviewArticle.Finding.Where(""),
        ReviewArticle.Finding.Quote(""),
        ReviewArticle.Finding.Problem("A problem."),
        ReviewArticle.Finding.Fix("")
      ))
  )

  val reports: Golden[Report] = Golden(
    "review-report.ndjson",
    Report(
      Reviewer.Rules,
      Verdict.Major,
      reviewFindings.values.take(2),
      Some(ReviewArticle.ColdReaderAnswer.ExplainBack("It says the route is later.")),
      List(ReviewArticle.ExplainSlotsValue("disagreement")),
      proposals.values.take(2),
      List(Check(Check.Id("R1"), fixed = Check.Fixed(true), Check.Quote("a quote"), Check.Note("fixed in ## Origins"))),
      List(
        SpotCheck(
          com.histmersion.domain.library.Citekey("smith1987_tin"),
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
          Problem("Not about the route."),
          Some(Activity.Actor("skeptic (agent run 1234abcd)"))
        ),
        CritiqueReason(Citekey("roe1990_tin"), Citekey("smith1987_tin"), Problem("Not material."))
      )
    ) ::
      Verdict.values.toList.map(v => Report(Reviewer.ColdReader, v, Nil))
  )

  val merged: Golden[Merged] = Golden(
    "review-merged.ndjson",
    Merged(
      Merged.Round(2),
      Verdict.Major,
      reviewFindings.values.take(3),
      Some(ReviewArticle.ColdReaderAnswer.ExplainBack("Explained back.")),
      List(ReviewArticle.ExplainSlotsValue("open")),
      proposals.values.take(1),
      List(ReviewArticle.Finding.Id("S1"))
    ) ::
      Verdict.values.toList.map(v => Merged(Merged.Round(1), v, Nil, None, Nil, Nil))
  )

  val workRefs: Golden[WorkRef] = Golden(
    "work-ref.ndjson",
    List(
      WorkRef(
        (Some("10.1/x")).map(Doi.apply),
        Some(WorkRef.OpenalexId("W123")),
        Some(Source.Candidate.ArxivId("2101.00001")),
        (Some("Tin and trade")).map(com.histmersion.api.library.Title.apply),
        (Some(1987)).map(Year.apply),
        (List("J. Smith")).map(com.histmersion.api.library.AuthorName.apply),
        (Some("Antiquity")).map(Source.Venue.apply),
        Some(Source.CitedByCount(12)),
        isOa = WorkRef.IsOa(true),
        Some(WorkRef.OaStatus("gold")),
        Some(WorkRef.OaUrl("https://example.org/x.pdf")),
        isRetracted = WorkRef.IsRetracted(true),
        Some(WorkRef.WorkType("article")),
        Some(Source.Candidate.AbstractText("An abstract.")),
        List(WorkRef.ReferencedWorks("W1"), WorkRef.ReferencedWorks("W2")),
        (Some("smith1987_tin")).map(Citekey.apply),
        inDb = WorkRef.InDb(true),
        List(WorkRef.OaLocations("https://example.org/x.pdf")),
        Some(WorkRef.OaLicence("cc-by"))
      ),
      WorkRef()
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
    workRefs
  )

/** The patches `enrich-source` builds from its checkpoints: one that fills everything, one that fills nothing. */
private object EnrichSourceSamples:
  private val loaded = EnrichSource.Loaded(
    com.histmersion.domain.library.Citekey("smith1987_tin"),
    EnrichSource.Loaded.Title("Tin"),
    List(AuthorName("Smith, J.")),
    Some(com.histmersion.Year(1987)),
    None,
    Source.Status.Wire("unassessed"),
    hasSummary = EnrichSource.Loaded.HasSummary(false),
    hasNotes = EnrichSource.Loaded.HasNotes(false),
    hasPeriod = EnrichSource.Loaded.HasPeriod(false),
    None,
    List(EnrichSource.Topic("trade")),
    Nil,
    Nil,
    Some(Source.UpdatedAt("2026-10-25T10:00:00Z")),
    EnrichSource.Loaded.Head("head"),
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
    EnrichSource
      .patch(
        loaded,
        classified,
        List(Finding.Input(Finding.Claim("A claim."), lon = Some(Longitude(1.0)), lat = Some(Latitude(2.0)))),
        Some(EnrichSource.PatchNotes("Notes."))
      )
      ._1,
    EnrichSource
      .patch(
        loaded
          .modify(_.status)
          .setTo(Source.Status.Wire("current"))
          .modify(_.hasSummary)
          .setTo(EnrichSource.Loaded.HasSummary(true))
          .modify(_.hasNotes)
          .setTo(EnrichSource.Loaded.HasNotes(true))
          .modify(_.hasPeriod)
          .setTo(EnrichSource.Loaded.HasPeriod(true))
          .modify(_.topics)
          .setTo(List(EnrichSource.Topic("bronze_age")))
          .modify(_.regions)
          .setTo(List(EnrichSource.Region("Aegean")))
          .modify(_.tags)
          .setTo(List(EnrichSource.Tag("tin"), EnrichSource.Tag("trade"))),
        classified.modify(_.status).setTo(Source.Status.Contested),
        Nil,
        None
      )
      ._1
  )

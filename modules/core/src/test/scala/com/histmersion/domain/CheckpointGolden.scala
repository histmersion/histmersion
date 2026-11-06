package com.histmersion.domain

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

/** Representative values of every record an agent step stores as JSON (the run's checkpoints and the patches it
  * writes), every case and optional field among them. Their encodings are the golden files under `golden/checkpoints/`,
  * written once from the codecs stored runs were written with.
  */
object CheckpointGolden:

  final case class Golden[A](file: String, values: List[A])(using val encoder: Encoder[A], val decoder: Decoder[A])

  private val findingInputs = List(
    Finding.Input(
      com.histmersion.api.library.Finding.Claim("Tin reached the Aegean from Afghanistan."),
      Some(com.histmersion.api.library.Finding.Region("Aegean")),
      Some(25.1),
      Some(37.9),
      Some(com.histmersion.api.catalogue.Year(-2500)),
      Some(com.histmersion.api.catalogue.Year(-2000)),
      Some(com.histmersion.api.library.Finding.PeriodName("Early Bronze Age")),
      List(com.histmersion.api.library.Finding.Tags("tin"), com.histmersion.api.library.Finding.Tags("trade")),
      Some(com.histmersion.api.library.Confidence.Wire("medium")),
      Some(com.histmersion.api.library.Finding.Significance("a long-distance route"))
    ),
    Finding.Input(com.histmersion.api.library.Finding.Claim("A claim with nothing else."))
  )

  private val metadataPatches = List(
    MetadataPatch(
      (Some("A title")).map(com.histmersion.api.library.Title.apply),
      (Some(List("Smith, J.", "Doe, A."))).map(_.map(com.histmersion.api.library.AuthorName.apply)),
      (Some(1987)).map(com.histmersion.api.catalogue.Year.apply),
      (Some("Antiquity")).map(com.histmersion.api.library.Source.Venue.apply),
      (Some("10.1/x")).map(com.histmersion.api.library.Doi.apply),
      Some(Source.Status.Contested),
      Some(com.histmersion.api.library.Source.Provenance("oa")),
      Some(com.histmersion.api.library.Source.CalibreId("42")),
      MetadataField.values.toList
    ),
    MetadataPatch(status = Some(Source.Status.Current)),
    MetadataPatch()
  )

  val sourcePatches: Golden[com.histmersion.api.library.Source.Patch] = Golden(
    "source-patch.ndjson",
    List(
      com.histmersion.api.library.Source.Patch(
        metadata = Some(metadataPatches.head),
        summary = Some(com.histmersion.api.library.Source.Summary("What the source argues.")),
        body = Some(com.histmersion.api.library.Source.OcrMarkdown("# Body")),
        notes = Some(com.histmersion.api.library.Source.Notes("Notes on [[smith1987_tin]].")),
        topics = Some(List(com.histmersion.api.library.Source.Topics("bronze_age"))),
        regions = Some(List(com.histmersion.api.library.Source.Regions("Aegean"))),
        period = Some(Period.Input(com.histmersion.api.catalogue.Year(-3300), com.histmersion.api.catalogue.Year(-1200))),
        tags = Some(ListPatch(List(com.histmersion.api.Write.ListItem("tin")), List(com.histmersion.api.Write.ListItem("old")), Some(List(com.histmersion.api.Write.ListItem("tin"), com.histmersion.api.Write.ListItem("trade"))))),
        findings = Some(FindingsPatch(findingInputs, List(com.histmersion.api.library.FindingsPatch.Remove("smith1987_tin#3")), Some(findingInputs.take(1)))),
        edges = Some(
          EdgesPatch(
            List(com.histmersion.api.library.Edge.Input(com.histmersion.api.library.Citekey("doe2001_tin"), Edge.Kind.Challenges, Some(com.histmersion.api.library.Citekey("smith1987_tin")))),
            List(com.histmersion.api.library.Edge.Input(com.histmersion.api.library.Citekey("roe1990_tin"), Edge.Kind.Refutes))
          )
        ),
        ifUpdatedAt = Some(com.histmersion.api.library.Source.UpdatedAt("2026-10-25T10:00:00Z"))
      ),
      com.histmersion.api.library.Source.Patch(
        metadata = Some(metadataPatches(1)),
        tags = Some(ListPatch(add = List(com.histmersion.api.Write.ListItem("tin")))),
        findings = Some(FindingsPatch(add = findingInputs))
      ),
      com.histmersion.api.library.Source.Patch(metadata = Some(metadataPatches(2))),
      com.histmersion.api.library.Source.Patch()
    ) ++
      EnrichSourceSamples.patches
  )

  val proposals: Golden[Proposal] = Golden(
    "proposal.ndjson",
    Proposal(
      Approval.Kind.StatusChange,
      com.histmersion.domain.library.Citekey("smith1987_tin"),
      status = Some(Source.Status.Superseded),
      evidence = "Doe shows the route is later.",
      quote = Some("the route is later")
    ) ::
      AgentProposals.EdgeKinds.map(k =>
        Proposal(
          Approval.Kind.Edge,
          com.histmersion.domain.library.Citekey("doe2001_tin"),
          edgeKind = Some(k),
          target = Some("smith1987_tin"),
          evidence = "It argues against.",
          quote = Some("we disagree")
        )
      ) ++
      Approval.Kind.values.toList.map(k => Proposal(k, com.histmersion.domain.library.Citekey("smith1987_tin")))
  )

  /** What [[AgentProposals.Proposal.patch]] writes for each proposal. */
  val proposalPatches: Golden[com.histmersion.api.library.Source.Patch] =
    Golden("proposal-patch.ndjson", proposals.values.map(_.patch))

  val classified: Golden[Classified] = Golden(
    "enrich-classified.ndjson",
    Classified(
      com.histmersion.domain.EnrichSource.Classified.Summary("A summary."),
      List(com.histmersion.domain.EnrichSource.Topic("bronze_age")),
      List(com.histmersion.domain.EnrichSource.Region("Aegean")),
      List(com.histmersion.domain.EnrichSource.Tag("tin")),
      Some(Period(com.histmersion.api.catalogue.Year(-2500), com.histmersion.api.catalogue.Year(-2000))),
      Some(com.histmersion.domain.EnrichSource.StatedPeriod.Name("Early Bronze Age")),
      Source.Status.Current,
      com.histmersion.domain.EnrichSource.Classified.StatusEvidence("It is cited as current.")
    ) ::
      Source.Status.values.toList.map(s => Classified(com.histmersion.domain.EnrichSource.Classified.Summary("A summary."), Nil, Nil, Nil, None, None, s, com.histmersion.domain.EnrichSource.Classified.StatusEvidence("")))
  )

  val findings: Golden[Findings] = Golden(
    "enrich-findings.ndjson",
    List(
      Findings(
        List(
          FindingProposal(
            com.histmersion.domain.EnrichSource.FindingProposal.Claim("Tin came from the east."),
            com.histmersion.domain.AgentProposals.Proposal.Quote("tin came from the east"),
            Some(com.histmersion.domain.EnrichSource.FindingProposal.Place("Troy")),
            Some(com.histmersion.api.catalogue.Geocode.KindHint.Wire("site")),
            Some(StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(2500), com.histmersion.domain.AgentVerify.YearUnit.Wire("BCE"))),
            Some(StatedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(2000), com.histmersion.domain.AgentVerify.YearUnit.Wire("BCE"))),
            Some(com.histmersion.domain.EnrichSource.FindingProposal.PeriodName("EBA")),
            List(com.histmersion.domain.EnrichSource.Tag("tin")),
            com.histmersion.domain.library.Confidence.Wire("high")
          ),
          FindingProposal(com.histmersion.domain.EnrichSource.FindingProposal.Claim("A bare claim."), com.histmersion.domain.AgentProposals.Proposal.Quote("a bare claim"), None, None, None, None, None, Nil, com.histmersion.domain.library.Confidence.Wire("low"))
        )
      ),
      Findings()
    )
  )

  val verifiedFindings: Golden[VerifiedFindings] = Golden(
    "enrich-verified-findings.ndjson",
    List(
      VerifiedFindings(
        VerifiedFinding(com.histmersion.domain.EnrichSource.VerifiedFinding.Claim("A bare claim."), com.histmersion.domain.EnrichSource.VerifiedFinding.Quote("a bare claim"), 0.91, None, None, None, None, Nil, com.histmersion.domain.EnrichSource.VerifiedFinding.Confidence("low")) ::
          Geocode.KindHint.values.toList.map(k =>
            VerifiedFinding(
              com.histmersion.domain.EnrichSource.VerifiedFinding.Claim("Tin came from the east."),
              com.histmersion.domain.EnrichSource.VerifiedFinding.Quote("tin came from the east"),
              1.0,
              Some(com.histmersion.domain.EnrichSource.VerifiedFinding.Place("Troy")),
              Some(com.histmersion.api.catalogue.Year(-2500)),
              Some(com.histmersion.api.catalogue.Year(-2000)),
              Some(com.histmersion.domain.EnrichSource.VerifiedFinding.PeriodName("EBA")),
              List(com.histmersion.domain.EnrichSource.Tag("tin"), com.histmersion.domain.EnrichSource.Tag("trade")),
              com.histmersion.domain.EnrichSource.VerifiedFinding.Confidence("high"),
              Some(k)
            )
          ),
        Some(com.histmersion.domain.EnrichSource.VerifiedFindings.Proposed(7)),
        Some(com.histmersion.domain.EnrichSource.VerifiedFindings.QuoteFailed(1))
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
          GeocodeChoice(com.histmersion.domain.EnrichSource.GeocodeChoice.Claim("Tin came from the east."), com.histmersion.domain.EnrichSource.GeocodeChoice.Asked("Troy"), Some(com.histmersion.domain.catalogue.Place.Id("troy")), Some(com.histmersion.domain.EnrichSource.GeocodeChoice.Label("Troy")), Some(com.histmersion.domain.EnrichSource.GeocodeChoice.Approximate("a qualifier was dropped"))),
          GeocodeChoice(com.histmersion.domain.EnrichSource.GeocodeChoice.Claim("Nowhere."), com.histmersion.domain.EnrichSource.GeocodeChoice.Asked("Atlantis"), None, None, None)
        )
      ),
      Geocoded(Nil, Nil)
    )
  )

  private val evidence =
    List(Evidence(com.histmersion.domain.library.Citekey("smith1987_tin"), Some(com.histmersion.Offset(120)), com.histmersion.domain.ReviewArticle.Evidence.Quote("the route is later"), 0.97), Evidence(com.histmersion.domain.library.Citekey("doe2001_tin"), None, com.histmersion.domain.ReviewArticle.Evidence.Quote("q"), 1.0))

  val reviewFindings: Golden[ReviewArticle.Finding] = Golden(
    "review-finding.ndjson",
    ReviewArticle.Finding(
      com.histmersion.domain.ReviewArticle.Finding.Id("S1"),
      Reviewer.Skeptic,
      Severity.Major,
      com.histmersion.domain.ReviewArticle.Finding.Kind("missing-critique"),
      com.histmersion.domain.ReviewArticle.Finding.Where("## Origins"),
      com.histmersion.domain.ReviewArticle.Finding.Quote("tin came"),
      com.histmersion.domain.ReviewArticle.Finding.Problem("The critique is not cited."),
      com.histmersion.domain.ReviewArticle.Finding.Fix("Cite it."),
      evidence,
      escalated = com.histmersion.domain.ReviewArticle.Finding.Escalated(true),
      pair = Some(CritiqueRef(com.histmersion.api.library.Citekey("doe2001_tin"), com.histmersion.api.library.Citekey("smith1987_tin")))
    ) ::
      (for r <- Reviewer.values.toList; s <- Severity.values.toList
      yield ReviewArticle.Finding(s"${r.prefix}1", r, s, com.histmersion.domain.ReviewArticle.Finding.Kind("style"), com.histmersion.domain.ReviewArticle.Finding.Where(""), com.histmersion.domain.ReviewArticle.Finding.Quote(""), com.histmersion.domain.ReviewArticle.Finding.Problem("A problem."), com.histmersion.domain.ReviewArticle.Finding.Fix("")))
  )

  val reports: Golden[Report] = Golden(
    "review-report.ndjson",
    Report(
      Reviewer.Rules,
      Verdict.Major,
      reviewFindings.values.take(2),
      Some(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("It says the route is later.")),
      List(com.histmersion.domain.ReviewArticle.ExplainSlotsValue("disagreement")),
      proposals.values.take(2),
      List(Check(com.histmersion.domain.ReviewArticle.Check.Id("R1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("a quote"), com.histmersion.domain.ReviewArticle.Check.Note("fixed in ## Origins"))),
      List(SpotCheck(com.histmersion.domain.library.Citekey("smith1987_tin"), com.histmersion.domain.ReviewArticle.SpotCheck.Claim("tin came"), com.histmersion.domain.ReviewArticle.SpotCheck.Verdict("attributable"), com.histmersion.domain.ReviewArticle.SpotCheck.RecordQuote("the route is later"), 0.98, com.histmersion.domain.ReviewArticle.SpotCheck.Tool("get_source"))),
      List(
        CritiqueReason(com.histmersion.api.library.Citekey("doe2001_tin"), com.histmersion.api.library.Citekey("smith1987_tin"), com.histmersion.api.research.CritiqueReason.Reason("Not about the route."), Some(com.histmersion.api.automation.Activity.Actor("skeptic (agent run 1234abcd)"))),
        CritiqueReason(com.histmersion.api.library.Citekey("roe1990_tin"), com.histmersion.api.library.Citekey("smith1987_tin"), com.histmersion.api.research.CritiqueReason.Reason("Not material."))
      )
    ) ::
      Verdict.values.toList.map(v => Report(Reviewer.ColdReader, v, Nil))
  )

  val merged: Golden[Merged] = Golden(
    "review-merged.ndjson",
    Merged(
      com.histmersion.domain.ReviewArticle.Merged.Round(2),
      Verdict.Major,
      reviewFindings.values.take(3),
      Some(com.histmersion.domain.ReviewArticle.ColdReaderAnswer.ExplainBack("Explained back.")),
      List(com.histmersion.domain.ReviewArticle.ExplainSlotsValue("open")),
      proposals.values.take(1),
      List(com.histmersion.domain.ReviewArticle.Finding.Id("S1"))
    ) ::
      Verdict.values.toList.map(v => Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), v, Nil, None, Nil, Nil))
  )

  val workRefs: Golden[WorkRef] = Golden(
    "work-ref.ndjson",
    List(
      WorkRef(
        (Some("10.1/x")).map(com.histmersion.api.library.Doi.apply),
        Some(com.histmersion.api.library.WorkRef.OpenalexId("W123")),
        Some(com.histmersion.api.library.Source.Candidate.ArxivId("2101.00001")),
        (Some("Tin and trade")).map(com.histmersion.api.library.Title.apply),
        (Some(1987)).map(com.histmersion.api.catalogue.Year.apply),
        (List("J. Smith")).map(com.histmersion.api.library.AuthorName.apply),
        (Some("Antiquity")).map(com.histmersion.api.library.Source.Venue.apply),
        Some(com.histmersion.api.library.Source.CitedByCount(12)),
        isOa = com.histmersion.api.library.WorkRef.IsOa(true),
        Some(com.histmersion.api.library.WorkRef.OaStatus("gold")),
        Some(com.histmersion.api.library.WorkRef.OaUrl("https://example.org/x.pdf")),
        isRetracted = com.histmersion.api.library.WorkRef.IsRetracted(true),
        Some(com.histmersion.api.library.WorkRef.WorkType("article")),
        Some(com.histmersion.api.library.Source.Candidate.AbstractText("An abstract.")),
        List(com.histmersion.api.library.WorkRef.ReferencedWorks("W1"), com.histmersion.api.library.WorkRef.ReferencedWorks("W2")),
        (Some("smith1987_tin")).map(com.histmersion.api.library.Citekey.apply),
        inDb = com.histmersion.api.library.WorkRef.InDb(true),
        List(com.histmersion.api.library.WorkRef.OaLocations("https://example.org/x.pdf")),
        Some(com.histmersion.api.library.WorkRef.OaLicence("cc-by"))
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
    com.histmersion.domain.EnrichSource.Loaded.Title("Tin"),
    List(com.histmersion.domain.AuthorName("Smith, J.")),
    Some(com.histmersion.Year(1987)),
    None,
    com.histmersion.api.library.Source.Status.Wire("unassessed"),
    hasSummary = com.histmersion.domain.EnrichSource.Loaded.HasSummary(false),
    hasNotes = com.histmersion.domain.EnrichSource.Loaded.HasNotes(false),
    hasPeriod = com.histmersion.domain.EnrichSource.Loaded.HasPeriod(false),
    None,
    List(com.histmersion.domain.EnrichSource.Topic("trade")),
    Nil,
    Nil,
    Some(com.histmersion.api.library.Source.UpdatedAt("2026-10-25T10:00:00Z")),
    com.histmersion.domain.EnrichSource.Loaded.Head("head"),
    Nil,
    List(com.histmersion.domain.EnrichSource.Tag("tin"))
  )
  private val classified = Classified(
    com.histmersion.domain.EnrichSource.Classified.Summary("A summary."),
    List(com.histmersion.domain.EnrichSource.Topic("bronze_age")),
    List(com.histmersion.domain.EnrichSource.Region("Aegean")),
    List(com.histmersion.domain.EnrichSource.Tag("tin"), com.histmersion.domain.EnrichSource.Tag("trade")),
    Some(Period(com.histmersion.api.catalogue.Year(-2500), com.histmersion.api.catalogue.Year(-2000))),
    None,
    Source.Status.Current,
    com.histmersion.domain.EnrichSource.Classified.StatusEvidence("cited as current")
  )

  val patches: List[com.histmersion.api.library.Source.Patch] = List(
    EnrichSource
      .patch(loaded, classified, List(Finding.Input(com.histmersion.api.library.Finding.Claim("A claim."), lon = Some(1.0), lat = Some(2.0))), Some(com.histmersion.domain.EnrichSource.PatchNotes("Notes.")))
      ._1,
    EnrichSource
      .patch(
        loaded.modify(
          _.status).setTo("current").modify(_.hasSummary).setTo(true).modify(_.hasNotes).setTo(true).modify(_.hasPeriod).setTo(true).modify(_.topics).setTo(List("bronze_age")).modify(_.regions).setTo(List("Aegean")).modify(_.tags).setTo(List("tin", "trade")
        ),
        classified.modify(_.status).setTo(Source.Status.Contested),
        Nil,
        None
      )
      ._1
  )

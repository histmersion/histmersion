package com.histmersion.domain

import com.histmersion.api.ListPatch
import com.histmersion.api.research.{CritiqueReason, CritiqueRef}
import com.histmersion.api.library.{
  EdgeInput,
  EdgesPatch,
  FindingInput,
  FindingsPatch,
  MetadataField,
  MetadataPatch,
  SourcePatch,
  WorkRef
}
import com.histmersion.api.catalogue.{Geocode, Period, PeriodInput}
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
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source

/** Representative values of every record an agent step stores as JSON (the run's checkpoints and the patches it
  * writes), every case and optional field among them. Their encodings are the golden files under `golden/checkpoints/`,
  * written once from the codecs stored runs were written with.
  */
object CheckpointGolden:

  final case class Golden[A](file: String, values: List[A])(using val encoder: Encoder[A], val decoder: Decoder[A])

  private val findingInputs = List(
    FindingInput(
      "Tin reached the Aegean from Afghanistan.",
      Some("Aegean"),
      Some(25.1),
      Some(37.9),
      Some(-2500),
      Some(-2000),
      Some("Early Bronze Age"),
      List("tin", "trade"),
      Some("medium"),
      Some("a long-distance route")
    ),
    FindingInput("A claim with nothing else.")
  )

  private val metadataPatches = List(
    MetadataPatch(
      Some("A title"),
      Some(List("Smith, J.", "Doe, A.")),
      Some(1987),
      Some("Antiquity"),
      Some("10.1/x"),
      Some(Source.Status.Contested),
      Some("oa"),
      Some("42"),
      MetadataField.values.toList
    ),
    MetadataPatch(status = Some(Source.Status.Current)),
    MetadataPatch()
  )

  val sourcePatches: Golden[SourcePatch] = Golden(
    "source-patch.ndjson",
    List(
      SourcePatch(
        metadata = Some(metadataPatches.head),
        summary = Some("What the source argues."),
        body = Some("# Body"),
        notes = Some("Notes on [[smith1987_tin]]."),
        topics = Some(List("bronze_age")),
        regions = Some(List("Aegean")),
        period = Some(PeriodInput(-3300, -1200)),
        tags = Some(ListPatch(List("tin"), List("old"), Some(List("tin", "trade")))),
        findings = Some(FindingsPatch(findingInputs, List("smith1987_tin#3"), Some(findingInputs.take(1)))),
        edges = Some(
          EdgesPatch(
            List(EdgeInput("doe2001_tin", Edge.Kind.Challenges, Some("smith1987_tin"))),
            List(EdgeInput("roe1990_tin", Edge.Kind.Refutes))
          )
        ),
        ifUpdatedAt = Some("2026-10-25T10:00:00Z")
      ),
      SourcePatch(
        metadata = Some(metadataPatches(1)),
        tags = Some(ListPatch(add = List("tin"))),
        findings = Some(FindingsPatch(add = findingInputs))
      ),
      SourcePatch(metadata = Some(metadataPatches(2))),
      SourcePatch()
    ) ++
      EnrichSourceSamples.patches
  )

  val proposals: Golden[Proposal] = Golden(
    "proposal.ndjson",
    Proposal(
      Approval.Kind.StatusChange,
      "smith1987_tin",
      status = Some(Source.Status.Superseded),
      evidence = "Doe shows the route is later.",
      quote = Some("the route is later")
    ) ::
      AgentProposals.EdgeKinds.map(k =>
        Proposal(
          Approval.Kind.Edge,
          "doe2001_tin",
          edgeKind = Some(k),
          target = Some("smith1987_tin"),
          evidence = "It argues against.",
          quote = Some("we disagree")
        )
      ) ++
      Approval.Kind.values.toList.map(k => Proposal(k, "smith1987_tin"))
  )

  /** What [[AgentProposals.Proposal.patch]] writes for each proposal. */
  val proposalPatches: Golden[SourcePatch] = Golden("proposal-patch.ndjson", proposals.values.map(_.patch))

  val classified: Golden[Classified] = Golden(
    "enrich-classified.ndjson",
    Classified(
      "A summary.",
      List("bronze_age"),
      List("Aegean"),
      List("tin"),
      Some(Period(-2500, -2000)),
      Some("Early Bronze Age"),
      Source.Status.Current,
      "It is cited as current."
    ) ::
      Source.Status.values.toList.map(s => Classified("A summary.", Nil, Nil, Nil, None, None, s, ""))
  )

  val findings: Golden[Findings] = Golden(
    "enrich-findings.ndjson",
    List(
      Findings(
        List(
          FindingProposal(
            "Tin came from the east.",
            "tin came from the east",
            Some("Troy"),
            Some("site"),
            Some(StatedYear(2500, "BCE")),
            Some(StatedYear(2000, "BCE")),
            Some("EBA"),
            List("tin"),
            "high"
          ),
          FindingProposal("A bare claim.", "a bare claim", None, None, None, None, None, Nil, "low")
        )
      ),
      Findings()
    )
  )

  val verifiedFindings: Golden[VerifiedFindings] = Golden(
    "enrich-verified-findings.ndjson",
    List(
      VerifiedFindings(
        VerifiedFinding("A bare claim.", "a bare claim", 0.91, None, None, None, None, Nil, "low") ::
          Geocode.KindHint.values.toList.map(k =>
            VerifiedFinding(
              "Tin came from the east.",
              "tin came from the east",
              1.0,
              Some("Troy"),
              Some(-2500),
              Some(-2000),
              Some("EBA"),
              List("tin", "trade"),
              "high",
              Some(k)
            )
          ),
        Some(7),
        Some(1)
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
          GeocodeChoice("Tin came from the east.", "Troy", Some("troy"), Some("Troy"), Some("a qualifier was dropped")),
          GeocodeChoice("Nowhere.", "Atlantis", None, None, None)
        )
      ),
      Geocoded(Nil, Nil)
    )
  )

  private val evidence =
    List(Evidence("smith1987_tin", Some(120), "the route is later", 0.97), Evidence("doe2001_tin", None, "q", 1.0))

  val reviewFindings: Golden[ReviewArticle.Finding] = Golden(
    "review-finding.ndjson",
    ReviewArticle.Finding(
      "S1",
      Reviewer.Skeptic,
      Severity.Major,
      "missing-critique",
      "## Origins",
      "tin came",
      "The critique is not cited.",
      "Cite it.",
      evidence,
      escalated = true,
      pair = Some(CritiqueRef("doe2001_tin", "smith1987_tin"))
    ) ::
      (for r <- Reviewer.values.toList; s <- Severity.values.toList
      yield ReviewArticle.Finding(s"${r.prefix}1", r, s, "style", "", "", "A problem.", ""))
  )

  val reports: Golden[Report] = Golden(
    "review-report.ndjson",
    Report(
      Reviewer.Rules,
      Verdict.Major,
      reviewFindings.values.take(2),
      Some("It says the route is later."),
      List("disagreement"),
      proposals.values.take(2),
      List(Check("R1", fixed = true, "a quote", "fixed in ## Origins")),
      List(SpotCheck("smith1987_tin", "tin came", "attributable", "the route is later", 0.98, "get_source")),
      List(
        CritiqueReason("doe2001_tin", "smith1987_tin", "Not about the route.", Some("skeptic (agent run 1234abcd)")),
        CritiqueReason("roe1990_tin", "smith1987_tin", "Not material.")
      )
    ) ::
      Verdict.values.toList.map(v => Report(Reviewer.ColdReader, v, Nil))
  )

  val merged: Golden[Merged] = Golden(
    "review-merged.ndjson",
    Merged(
      2,
      Verdict.Major,
      reviewFindings.values.take(3),
      Some("Explained back."),
      List("open"),
      proposals.values.take(1),
      List("S1")
    ) ::
      Verdict.values.toList.map(v => Merged(1, v, Nil, None, Nil, Nil))
  )

  val workRefs: Golden[WorkRef] = Golden(
    "work-ref.ndjson",
    List(
      WorkRef(
        Some("10.1/x"),
        Some("W123"),
        Some("2101.00001"),
        Some("Tin and trade"),
        Some(1987),
        List("J. Smith"),
        Some("Antiquity"),
        Some(12),
        isOa = true,
        Some("gold"),
        Some("https://example.org/x.pdf"),
        isRetracted = true,
        Some("article"),
        Some("An abstract."),
        List("W1", "W2"),
        Some("smith1987_tin"),
        inDb = true,
        List("https://example.org/x.pdf"),
        Some("cc-by")
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
    "smith1987_tin",
    "Tin",
    List("Smith, J."),
    Some(1987),
    None,
    "unassessed",
    hasSummary = false,
    hasNotes = false,
    hasPeriod = false,
    None,
    List("trade"),
    Nil,
    Nil,
    Some("2026-10-25T10:00:00Z"),
    "head",
    Nil,
    List("tin")
  )
  private val classified = Classified(
    "A summary.",
    List("bronze_age"),
    List("Aegean"),
    List("tin", "trade"),
    Some(Period(-2500, -2000)),
    None,
    Source.Status.Current,
    "cited as current"
  )

  val patches: List[SourcePatch] = List(
    EnrichSource
      .patch(loaded, classified, List(FindingInput("A claim.", lon = Some(1.0), lat = Some(2.0))), Some("Notes."))
      ._1,
    EnrichSource
      .patch(
        loaded.copy(
          status = "current",
          hasSummary = true,
          hasNotes = true,
          hasPeriod = true,
          topics = List("bronze_age"),
          regions = List("Aegean"),
          tags = List("tin", "trade")
        ),
        classified.copy(status = Source.Status.Contested),
        Nil,
        None
      )
      ._1
  )

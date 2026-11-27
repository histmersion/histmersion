package com.histmersion
package domain.library.acquisition
import hearth.kindlings.jsoniterderivation.JsoniterConfig
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.problem.Problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.api.VocabularyCodecs.given

import chimp.server.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.{
  AcquireApi,
  AcquireMarkdown,
  ArchiveImportRequest,
  AssessRequest,
  CandidateMark,
  Candidates,
  CandidatesMarkdown,
  FollowupsRequest,
  RefsRequest,
  Title
}
import com.histmersion.api.Api
import com.histmersion.api.library.CandidateSweep
import com.histmersion.api.library.Ingest
import com.histmersion.domain.library.collections.CollectionsControllers
import com.histmersion.domain.library.ArchiveId
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.collections.CollectionsService
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.CandidateList
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Creation
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{tolerated, LintRule}

/** The acquisition's MCP tools. Every tool returns Markdown and delegates to the same [[AcquisitionControllers]] the
  * REST routes call, so the two surfaces cannot drift.
  *
  * The Calibre library is the `collection` tool's (the library's collections); `archive` runs archive.org, one of them.
  */
final class AcquisitionTools(
    h: AcquisitionControllers,
    collections: CollectionsControllers
):
  import AcquisitionTools.*

  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)

  private def render[A](r: Either[Api.Error, A])(f: A => String) = McpOut.of(r)(f)

  /** A call that gave none of the arguments `fields`, of which it needs one. */
  private def oneOf(first: Problem.Field, rest: Problem.Field*) =
    McpOut.refused(
      Problem.Invalid.Inconsistent.here(NonEmptyVector.of(first, rest*), Problem.Invalid.Exclusion.AtLeastOne)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private val discover =
    tool("discover")
      .description(
        "Find LITERATURE THAT IS NOT IN THE CORPUS YET — the trailhead into a new topic. Three modes, " +
          "pick one: `query` searches OpenAlex by relevance (or `sort: cited` for the most-cited work " +
          "in the field; start a topic with `<topic> review`, `since` and `sort: cited` for recent reviews); " +
          "`doi` fetches normalised metadata for one paper; `wikipediaUrl` harvests every DOI a Wikipedia " +
          "article cites — SUPPLEMENTARY only, to catch a gap after the database searches and the citation " +
          "graph (`refs`, `followups`), never the starting point or the main channel. Each result carries the citekey it WOULD get and whether the corpus " +
          "already has it, so `notInDb: true` gives you exactly the delta to ingest. Use the `search` " +
          "tool instead when you want what we already hold; this one goes out to the internet."
      )
      .input[DiscoverInput]
      .handle { in =>
        (
          in.query.map(q => AcquireApi.Query(q.value.trim)).filter(_.value.nonEmpty),
          in.doi.map(d => AcquireApi.Identifier(d.value.trim)).filter(_.value.nonEmpty),
          in.wikipediaUrl.map(u => Title(u.value.trim)).filter(_.value.nonEmpty)
        ) match
          case (_, _, Some(url)) => render(h.wikirefs(url))(AcquireMarkdown.wikirefs)
          case (_, Some(doi), _) => render(h.meta(doi))(AcquireMarkdown.work)
          case (Some(q), _, _)   =>
            render(h.discover(q, in.since, in.sort, in.limit, in.notInDb.value))(AcquireMarkdown.discover)
          case _ => oneOf(Problem.Field("query"), Problem.Field("doi"), Problem.Field("wikipediaUrl"))
      }

  private val refs =
    tool("refs")
      .description(
        "The reference list a paper DEPOSITED with Crossref, with each entry resolved to a stored " +
          "source where we have it. Use it to see what a paper builds on, and with `apply: true` to " +
          "record those as `references` edges so `neighbors` can walk them. Input is a citekey or a DOI."
      )
      .input[RefsInput]
      .handle(in => CreatorControllers.mcp(in)(render(h.refs(RefsRequest(in.id, in.apply)))(AcquireMarkdown.refs)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val assess =
    tool("assess")
      .description(
        "Is this paper safe to rely on? Checks the local Retraction Watch mirror, Crossref's " +
          "retraction/correction/expression-of-concern notices and the citation metrics, and returns a " +
          "verdict: retracted | corrected | current | low-impact | unassessed. ALWAYS run this before " +
          "citing a source you have not checked, and with `apply: true` to write the verdict into the " +
          "source's `status` (a retraction becomes status=retracted). `apply` only overwrites an " +
          "`unassessed` status — one already set (contested, foundational, …) is kept and reported — " +
          "unless `forceStatus: true`; a retraction is always written. Input is a citekey or a DOI. " +
          "`updateRetractions: true` instead starts a refresh of the whole Retraction Watch mirror " +
          "(it runs in the background — call again to see whether it has finished); do that " +
          "periodically, not per paper."
      )
      .input[AssessInput]
      .handle { in =>
        CreatorControllers.mcp(in) {
          if in.updateRetractions.value then render(h.updateRetractions())(AcquireMarkdown.retractionsUpdate)
          else if in.retractionsStatus.value then
            ToolResult.text(AcquireMarkdown.retractionsUpdate(h.retractionsStatus))
          else
            in.id.map(i => AssessRequest.Id(i.value.trim)).filter(_.value.nonEmpty) match
              case Some(id) => render(h.assess(AssessRequest(id, in.apply, in.forceStatus)))(AcquireMarkdown.assessment)
              case None     =>
                McpOut.error(
                  Api.Error(
                    Problem.Invalid.Inconsistent.here(
                      NonEmptyVector.of(
                        Problem.Field("id"),
                        Problem.Field("updateRetractions"),
                        Problem.Field("retractionsStatus")
                      ),
                      Problem.Invalid.Exclusion.AtLeastOne
                    )
                  ),
                  "`id` is a citekey or a DOI; `updateRetractions: true` or `retractionsStatus: true` stand alone."
                )
        }
      }

  private val followups =
    tool("followups")
      .description(
        "What came AFTER this paper — the citation graph forward. Returns the papers citing it with " +
          "Semantic Scholar's citation intents, the influential-citation flag and the actual citation " +
          "contexts, plus a proposed edge kind for each (confirms | challenges | refutes | supersedes | " +
          "cites) read from those contexts. This is how you answer \"is this still accepted?\" and " +
          "\"has it been debunked?\". `apply: true` records the proposed edges for the citing papers " +
          "that are already in the corpus. `since` limits to papers published from that year on."
      )
      .input[FollowupsInput]
      .handle(in =>
        CreatorControllers.mcp(in) {
          render(h.followups(FollowupsRequest(in.id, in.since, in.limit, in.apply)))(AcquireMarkdown.followups)
        }
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ingestDoi =
    tool("ingest_doi")
      .description(
        "Bring a paper INTO the corpus from its identifier: metadata from OpenAlex, then a LEGAL " +
          "open-access PDF (Unpaywall → CORE → OpenAlex OA locations → Europe PMC/PMC → arXiv → repository landing " +
          "pages resolved one step (Zenodo, an institutional repository's citation_pdf_url); else PMC's " +
          "JATS full text), then the ingest job, " +
          "then its reference edges, then an assessment. Idempotent: re-ingesting an existing citekey " +
          "updates its metadata and never wipes enrichment; one with no original yet (metadata only) is " +
          "fetched and converted, one that has its original keeps it unless `refetch: true`. When no open-access copy exists the source " +
          "is still created from metadata and the report tells you where to drop the file in by hand. " +
          "An agent's ingest asks the legal chain alone (never a fetcher the config marks unauthorised); " +
          "`legalOnly: false` is refused unless a person allowed agents more. " +
          "Input is a DOI or `arxiv:<id>`."
      )
      .input[IngestInput]
      .handle(in =>
        render(
          h.ingestByAgent(
            Ingest.Request(
              in.id,
              in.citekey,
              in.arxiv,
              in.refetch,
              Ingest.Request.LegalOnly(in.legalOnly.value),
              in.createdBy
            )
          )
        )(AcquireMarkdown.ingest)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val archive =
    tool("archive")
      .description(
        "Internet Archive — for the out-of-print scholarly books the open-access pipeline cannot " +
          "reach. `query` searches, `id` shows one item's files, and `id` with `import: true` fetches " +
          "it. PUBLIC items only: a lending-only scan is reported and refused, because borrowing it " +
          "on archive.org is the only legitimate way to read it."
      )
      .input[ArchiveInput]
      .handle { in =>
        CreatorControllers.mcp(in) {
          (
            in.query.map(q => AcquireApi.Query(q.value.trim)).filter(_.value.nonEmpty),
            in.id.map(i => ArchiveImportRequest.Id(i.value.trim)).filter(_.value.nonEmpty)
          ) match
            case (_, Some(id)) if in.`import`.value =>
              render(collections.archiveImport(Caller.Local, ArchiveImportRequest(id, in.citekey, in.format)))(
                AcquireMarkdown.ingest
              )
            case (_, Some(id)) =>
              render(collections.archiveInfo(Caller.Local, ArchiveId(id.value)))(AcquireMarkdown.archiveItem)
            case (Some(q), _) =>
              render(
                collections
                  .archiveSearch(
                    Caller.Local,
                    Search.Query(q.value),
                    in.rows.map(r => CollectionsService.Limit(r.value))
                  )
              )(AcquireMarkdown.archiveSearch)
            case _ => oneOf(Problem.Field("query"), Problem.Field("id"))
        }
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val candidates =
    tool("candidates")
      .description(
        "The NEW SOURCES found for the research goals by the deterministic search (no model): each goal's saved " +
          "queries on OpenAlex, Crossref, Europe PMC, arXiv (and CORE with a key), the works citing its sources and " +
          "its sources' authors' newer works, each read from the day it was last read, deduplicated by DOI against " +
          "the corpus and the acquisition list. Default: the `new` (unseen) candidates of every goal, or of `goal`; " +
          "`state` = new, seen, queued or dismissed. Filter them yourself: `mark` (candidate ids) with `as` = seen, " +
          "queued or dismissed records your decision; `sweep: true` (with `goal`) reads the goal's watches now " +
          "(`dryRun: true` stores nothing). Queue what is worth acquiring with the `queue` tool or `ingest_doi`."
      )
      .input[CandidatesInput]
      .handle { in =>
        CreatorControllers.mcp(in) {
          if in.sweep.value then
            in.goal.map(_.value.trim).filter(_.nonEmpty) match
              case Some(g) =>
                render(
                  h.sweepCandidates(
                    CandidateSweep
                      .Request(CandidateSweep.Request.Goal(g), CandidateSweep.Request.DryRun(in.dryRun.value))
                  )
                )(CandidatesMarkdown.sweep)
              case None => McpOut.missing(Problem.Field("goal"))
          else if in.mark.nonEmpty then
            render(
              h.markCandidates(
                CandidateMark(in.mark, in.as.getOrElse(Source.Candidate.State(Candidates.State.Seen.wire.value)))
              )
            )(
              CandidatesMarkdown.marked
            )
          else render(h.candidates(in.goal, in.state, in.limit, in.offset))(CandidatesMarkdown.list)
        }
      }

  val tools = List(discover, refs, assess, followups, ingestDoi, archive, candidates)

object AcquisitionTools:
  /** Refuse an argument a tool does not know, as `McpTools` does. */
  given JsonCodec.Unknown = JsonCodec.Unknown.Refused
  given JsoniterConfig = JsonCodec.schemas

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class DiscoverInput(
      query: Option[AcquireApi.Query] = None,
      doi: Option[AcquireApi.Identifier] = None,
      wikipediaUrl: Option[Title] = None,
      since: Option[Year] = None,
      sort: Option[AcquireApi.Filter] = None,
      limit: Option[AcquireApi.Limit] = None,
      notInDb: DiscoverInput.NotInDb = DiscoverInput.NotInDb(false)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object DiscoverInput:
    type NotInDb = NotInDb.T
    object NotInDb extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class CandidatesInput(
      goal: Option[Goal.Slug] = None,
      state: Option[Candidates.State.Wire] = None,
      limit: Option[AcquireApi.Limit] = None,
      offset: Option[CandidateList.Offset] = None,
      mark: List[Source.Candidate.Id] = Nil,
      as: Option[Source.Candidate.State] = None,
      sweep: CandidatesInput.Sweep = CandidatesInput.Sweep(false),
      dryRun: CandidatesInput.DryRun = CandidatesInput.DryRun(false),
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  object CandidatesInput:
    type Sweep = Sweep.T
    object Sweep extends Newtype[Boolean, Pure]
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class RefsInput(
      id: RefsRequest.Id,
      apply: RefsRequest.Apply = RefsRequest.Apply(false),
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class AssessInput(
      id: Option[AssessRequest.Id] = None,
      apply: AssessRequest.Apply = AssessRequest.Apply(false),
      forceStatus: AssessRequest.ForceStatus = AssessRequest.ForceStatus(false),
      updateRetractions: AssessInput.UpdateRetractions = AssessInput.UpdateRetractions(false),
      retractionsStatus: AssessInput.RetractionsStatus = AssessInput.RetractionsStatus(false),
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  object AssessInput:
    type UpdateRetractions = UpdateRetractions.T
    object UpdateRetractions extends Newtype[Boolean, Pure]
    type RetractionsStatus = RetractionsStatus.T
    object RetractionsStatus extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class FollowupsInput(
      id: FollowupsRequest.Id,
      since: Option[FollowupsRequest.Since] = None,
      limit: Option[FollowupsRequest.Limit] = None,
      apply: FollowupsRequest.Apply = FollowupsRequest.Apply(false),
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  final case class IngestInput(
      id: Ingest.Request.Id,
      citekey: Option[Citekey] = None,
      arxiv: Option[Ingest.Request.Arxiv] = None,
      refetch: Ingest.Request.Refetch = Ingest.Request.Refetch(false),
      legalOnly: IngestInput.LegalOnly = IngestInput.LegalOnly(true),
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec
  object IngestInput:
    type LegalOnly = LegalOnly.T
    object LegalOnly extends Newtype[Boolean, Pure]

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[IngestInput] = KindlingsSchema
      .derived[IngestInput]
      .schema
      .modify(_.legalOnly)(
        _.description(
          "The legal open-access chain only (the default) — never a fetcher the config marks unauthorised; " +
            "false is refused unless a person allowed agents more"
        )
      )

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class ArchiveInput(
      query: Option[AcquireApi.Query] = None,
      id: Option[ArchiveImportRequest.Id] = None,
      `import`: ArchiveInput.Import = ArchiveInput.Import(false),
      citekey: Option[Citekey] = None,
      format: Option[ArchiveImportRequest.Format] = None,
      rows: Option[AcquireApi.Limit] = None,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  object ArchiveInput:
    type Import = Import.T
    object Import extends Newtype[Boolean, Pure]

package com.histmersion.domain.library.acquisition

import com.histmersion.api.ApiError
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.mcp.McpOut
import com.histmersion.api.VocabularyCodecs.given

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.{
  AcquireMarkdown,
  ArchiveImportRequest,
  AssessRequest,
  CandidateMark,
  Candidates,
  CandidatesMarkdown,
  CandidateSweepRequest,
  FollowupsRequest,
  IngestRequest,
  RefsRequest
}

/** The acquisition's MCP tools. Every tool returns Markdown and delegates to the same [[AcquisitionControllers]] the
  * REST routes call, so the two surfaces cannot drift.
  *
  * The Calibre library is the `collection` tool's (the library's collections); `archive` runs archive.org, one of them.
  */
final class AcquisitionTools(
    h: AcquisitionControllers,
    collections: com.histmersion.domain.library.collections.CollectionsControllers
):
  import AcquisitionTools.*

  private def render[A](r: Either[ApiError, A])(f: A => String) =
    ToolResult.text(r.fold(McpOut.refusal, f))

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
          in.query.map(_.trim).filter(_.nonEmpty),
          in.doi.map(_.trim).filter(_.nonEmpty),
          in.wikipediaUrl.map(_.trim).filter(_.nonEmpty)
        ) match
          case (_, _, Some(url)) => render(h.wikirefs(url))(AcquireMarkdown.wikirefs)
          case (_, Some(doi), _) => render(h.meta(doi))(AcquireMarkdown.work)
          case (Some(q), _, _)   =>
            render(h.discover(q, in.since, in.sort, in.limit, in.notInDb))(AcquireMarkdown.discover)
          case _ => ToolResult.text("_give one of: query, doi, wikipediaUrl_")
      }

  private val refs =
    tool("refs")
      .description(
        "The reference list a paper DEPOSITED with Crossref, with each entry resolved to a stored " +
          "source where we have it. Use it to see what a paper builds on, and with `apply: true` to " +
          "record those as `references` edges so `neighbors` can walk them. Input is a citekey or a DOI."
      )
      .input[RefsInput]
      .handle(in => render(h.refs(RefsRequest(in.id, in.apply)))(AcquireMarkdown.refs))

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
        if in.updateRetractions then render(h.updateRetractions())(AcquireMarkdown.retractionsUpdate)
        else if in.retractionsStatus then ToolResult.text(AcquireMarkdown.retractionsUpdate(h.retractionsStatus))
        else
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) => render(h.assess(AssessRequest(id, in.apply, in.forceStatus)))(AcquireMarkdown.assessment)
            case None     =>
              ToolResult.text(
                "_give an `id` (citekey or DOI), or `updateRetractions: true` / `retractionsStatus: true`_"
              )
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
        render(h.followups(FollowupsRequest(in.id, in.since, in.limit, in.apply)))(AcquireMarkdown.followups)
      )

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
          "`legalOnly: true` asks the legal chain alone (never a fetcher the config marks unauthorised). " +
          "Input is a DOI or `arxiv:<id>`."
      )
      .input[IngestInput]
      .handle(in =>
        render(h.ingest(IngestRequest(in.id, in.citekey, in.arxiv, in.refetch, in.legalOnly)))(AcquireMarkdown.ingest)
      )

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
        (in.query.map(_.trim).filter(_.nonEmpty), in.id.map(_.trim).filter(_.nonEmpty)) match
          case (_, Some(id)) if in.`import` =>
            render(collections.archiveImport(Caller.Local, ArchiveImportRequest(id, in.citekey, in.format)))(
              AcquireMarkdown.ingest
            )
          case (_, Some(id)) => render(collections.archiveInfo(Caller.Local, id))(AcquireMarkdown.archiveItem)
          case (Some(q), _)  =>
            render(collections.archiveSearch(Caller.Local, q, in.rows))(AcquireMarkdown.archiveSearch)
          case _ => ToolResult.text("_give a `query` to search, or an `id`_")
      }

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
        if in.sweep then
          in.goal.map(_.trim).filter(_.nonEmpty) match
            case Some(g) => render(h.sweepCandidates(CandidateSweepRequest(g, in.dryRun)))(CandidatesMarkdown.sweep)
            case None    => ToolResult.text("_give `goal` to sweep_")
        else if in.mark.nonEmpty then
          render(h.markCandidates(CandidateMark(in.mark, in.as.getOrElse(Candidates.State.Seen.wire))))(
            CandidatesMarkdown.marked
          )
        else render(h.candidates(in.goal, in.state, in.limit, in.offset))(CandidatesMarkdown.list)
      }

  val tools = List(discover, refs, assess, followups, ingestDoi, archive, candidates)

object AcquisitionTools:
  /** Honour Scala defaults when decoding tool arguments, as `McpTools` does — otherwise every optional field would be
    * required in the JSON.
    */
  given hearth.kindlings.circederivation.Configuration =
    hearth.kindlings.circederivation.Configuration.default.withDefaults.withStrictDecoding

  final case class DiscoverInput(
      query: Option[String] = None,
      doi: Option[String] = None,
      wikipediaUrl: Option[String] = None,
      since: Option[Int] = None,
      sort: Option[String] = None,
      limit: Option[Int] = None,
      notInDb: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object DiscoverInput:
    given Schema[DiscoverInput] = summon[KindlingsSchema[DiscoverInput]].schema

  final case class CandidatesInput(
      goal: Option[String] = None,
      state: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None,
      mark: List[Long] = Nil,
      as: Option[String] = None,
      sweep: Boolean = false,
      dryRun: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object CandidatesInput:
    given Schema[CandidatesInput] = summon[KindlingsSchema[CandidatesInput]].schema

  final case class RefsInput(id: String, apply: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
  object RefsInput:
    given Schema[RefsInput] = summon[KindlingsSchema[RefsInput]].schema

  final case class AssessInput(
      id: Option[String] = None,
      apply: Boolean = false,
      forceStatus: Boolean = false,
      updateRetractions: Boolean = false,
      retractionsStatus: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object AssessInput:
    given Schema[AssessInput] = summon[KindlingsSchema[AssessInput]].schema

  final case class FollowupsInput(
      id: String,
      since: Option[Int] = None,
      limit: Option[Int] = None,
      apply: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object FollowupsInput:
    given Schema[FollowupsInput] = summon[KindlingsSchema[FollowupsInput]].schema

  final case class IngestInput(
      id: String,
      citekey: Option[String] = None,
      arxiv: Option[String] = None,
      refetch: Boolean = false,
      legalOnly: Boolean = false
  ) derives KindlingsCodecAsObject
  object IngestInput:
    given Schema[IngestInput] = KindlingsSchema
      .derived[IngestInput]
      .schema
      .modify(_.legalOnly)(
        _.description("Ask the legal open-access chain only — never a fetcher the config marks unauthorised")
      )

  final case class ArchiveInput(
      query: Option[String] = None,
      id: Option[String] = None,
      `import`: Boolean = false,
      citekey: Option[String] = None,
      format: Option[String] = None,
      rows: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ArchiveInput:
    given Schema[ArchiveInput] = summon[KindlingsSchema[ArchiveInput]].schema

package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.library.{
  AcquireApi,
  AcquireMarkdown,
  ArchiveId,
  ArchiveImportRequest,
  AssessRequest,
  Citekey,
  FollowupsRequest,
  RefsRequest,
  RetractionsUpdate,
  Title
}
import com.histmersion.api.library.Ingest
import com.histmersion.util.console.Console
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion discover|refs|assess|followups|ingest|archive` — the acquisition & assessment commands (plan §5.2
  * stream 2.6), each a thin wrapper over one endpoint that prints the SHARED Markdown rendering, or the raw JSON with
  * `--json`.
  */
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion acquire` arguments")
object AcquireCmd:

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  // ── refs / assess / followups ───────────────────────────────────────────────────────────────

  /** The server downloads and rewrites the mirror in the background — one HTTP request cannot hold a 30 MB download
    * plus 60 000 inserts — so the CLI drives the wait, exactly as `reindex` does.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  @annotation.tailrec
  private def pollRetractions(c: AcquireClient, waited: Int)(using
      console: Console
  ): Either[Cli.Error, RetractionsUpdate] =
    c.retractionsStatus match
      case Left(e)                        => Left(e)
      case Right(r) if !r.running.running =>
        r.error match
          case Some(msg) =>
            Left(Cli.Error.answered(Problem.Unclassified(Legacy.Text(s"the refresh failed: ${msg.sentence}"))))
          case None => Right(r)
      case Right(_) if waited > 600 =>
        Left(Cli.Error(c.unanswered("the refresh is still running after 10 minutes — check the server log")))
      case Right(_) =>
        Thread.sleep(2000)
        Cli.err.print(".")
        pollRetractions(c, waited + 2)

  // ── ingest ──────────────────────────────────────────────────────────────────────────────────

  /** ONE `ingest`, two ways in — because from the user's side it is one intention.
    *
    * A **file** is uploaded and converted here (stream 2.4, §1.8); a **DOI or arxiv:id** is fetched upstream by the
    * server, which then hands the PDF to the very same OCR job. The argument decides: anything that is a readable file
    * on this machine takes the upload path. The flags of both halves are accepted, and the ones that do not apply to
    * the branch taken are simply unused.
    */

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** Everything this stream adds to `histmersion`, in one value so `Cli` gains a single line. */

  /** Whether an identifier's ingest asks the legal chain alone: yes, unless the person says `--allow-unauthorised`;
    * `--legal-only` says the default aloud, and the two together are refused. Pure.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def legalOnly(legalOnly: Boolean, allowUnauthorised: Boolean): Either[Problem, Boolean] =
    if legalOnly && allowUnauthorised then
      Left(
        Problem.Invalid.Inconsistent.here(
          util.nonempty.NonEmptyVector.of(Problem.Field("legal-only"), Problem.Field("allow-unauthorised")),
          Problem.Invalid.Exclusion.AtMostOne
        )
      )
    else Right(!allowUnauthorised)

  /** What `ingest` brings in: a file on this machine (uploaded) or an identifier the server fetches. */
  enum IngestTarget:
    case File(path: Path)
    case Identifier(id: String)

  object IngestTarget:
    /** A readable file here is a file; anything else is an identifier (a DOI, `arxiv:…`). */
    def of(raw: String): IngestTarget =
      scala.util.Try(Path.of(raw)).toOption.filter(Files.isRegularFile(_)).fold(Identifier(raw))(File(_))

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case DiscoverSearch(
        q: String,
        since: Option[Int],
        sort: Option[String],
        limit: Option[Int],
        notInDb: Boolean,
        json: Cli.JsonOutput
    )
    case DiscoverMeta(doi: String, json: Cli.JsonOutput)
    case DiscoverWikirefs(url: String, json: Cli.JsonOutput)
    case Refs(id: String, writes: Cli.Writes, json: Cli.JsonOutput)
    case AssessUpdateRetractions(json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Assess(id: String, writes: Cli.Writes, force: Boolean, json: Cli.JsonOutput)
    case Followups(id: String, since: Option[Int], limit: Option[Int], writes: Cli.Writes, json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Ingest(
        id: IngestTarget,
        citekey: Option[String],
        arxiv: Option[String],
        quality: Boolean,
        force: Boolean,
        title: Option[String],
        authors: List[String],
        year: Option[Int],
        doi: Option[String],
        prov: Option[String],
        following: Cli.Following,
        refetch: Boolean,
        legalOnly: Boolean,
        item: Option[Int],
        json: Cli.JsonOutput
    )
    case ArchiveSearch(q: String, rows: Option[Int], json: Cli.JsonOutput)
    case ArchiveInfo(id: String, json: Cli.JsonOutput)
    case ArchiveImport(id: String, citekey: Option[String], format: Option[String], json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.DiscoverSearch(q, since, sort, limit, notInDb, json) =>
          AcquireClient(c)
            .discover(
              AcquireApi.Query(q),
              since.map(y => Year(y.toLong)),
              sort.map(AcquireApi.Filter(_)),
              limit.map(AcquireApi.Limit(_)),
              notInDb
            )
            .map(r => Output.emit(json, r)(AcquireMarkdown.discover))
        case Command.DiscoverMeta(doi, json) =>
          AcquireClient(c).meta(AcquireApi.Identifier(doi)).map(w => Output.emit(json, w)(AcquireMarkdown.work))
        case Command.DiscoverWikirefs(url, json) =>
          AcquireClient(c).wikirefs(Title(url)).map(r => Output.emit(json, r)(AcquireMarkdown.wikirefs))
        case Command.Refs(id, writes, json) =>
          AcquireClient(c)
            .refs(
              RefsRequest(
                RefsRequest.Id(id),
                (if writes.applies then RefsRequest.Apply.Apply else RefsRequest.Apply.DryRun)
              )
            )
            .map(r => Output.emit(json, r)(AcquireMarkdown.refs))
        case Command.AssessUpdateRetractions(json) =>
          val client = AcquireClient(c)
          client.updateRetractions.flatMap { started =>
            if json.asJson then Right(Output.emit(json, started)(AcquireMarkdown.retractionsUpdate))
            else
              Cli.err.println("refreshing the Retraction Watch mirror (~30 MB)…")
              pollRetractions(client, 0).map(r => Cli.out.println(AcquireMarkdown.retractionsUpdate(r)))
          }
        case Command.Followups(id, since, limit, writes, json) =>
          AcquireClient(c)
            .followups(
              FollowupsRequest(
                FollowupsRequest.Id(id),
                since.map(FollowupsRequest.Since(_)),
                limit.map(FollowupsRequest.Limit(_)),
                (if writes.applies then FollowupsRequest.Apply.Apply else FollowupsRequest.Apply.DryRun)
              )
            )
            .map(r => Output.emit(json, r)(AcquireMarkdown.followups))
        case Command.Ingest(
              id,
              citekey,
              arxiv,
              quality,
              force,
              title,
              authors,
              year,
              doi,
              prov,
              following,
              refetch,
              legalOnly,
              item,
              json
            ) =>
          id match
            case IngestTarget.File(path) =>
              IngestCmd.upload(
                c,
                path,
                Citekey(citekey.getOrElse("")),
                (if quality then Ingest.Params.Quality.Quality else Ingest.Params.Quality.Fast),
                (if force then Ingest.Params.Force.Force else Ingest.Params.Force.Safe),
                title.map(Title(_)),
                authors.map(AuthorName(_)),
                year.map(y => Year(y.toLong)),
                doi.map(Doi(_)),
                following,
                prov.map(Ingest.Params.Provenance(_)),
                item.map(Ingest.Params.AcquireItem(_))
              )
            case IngestTarget.Identifier(_) if item.isDefined =>
              Left(
                Cli.Error(
                  Problem.Unclassified(
                    Legacy.Text(
                      "--acquire-item applies to a file upload; for an identifier, tick the item off with " +
                        "`histmersion queue done <id>` once the source is in"
                    )
                  )
                )
              )
            case IngestTarget.Identifier(id) =>
              AcquireClient(c)
                .ingest(
                  Ingest.Request(
                    Ingest.Request.Id(id),
                    citekey.map(Citekey(_)),
                    arxiv.map(Ingest.Request.Arxiv(_)),
                    (if refetch then Ingest.Request.Refetch.Refetch else Ingest.Request.Refetch.Reuse),
                    (if legalOnly then Ingest.Request.LegalOnly.LegalOnly else Ingest.Request.LegalOnly.AnySource)
                  )
                )
                .map(r => Output.emit(json, r)(AcquireMarkdown.ingest))
        case Command.ArchiveSearch(q, rows, json) =>
          AcquireClient(c)
            .archiveSearch(AcquireApi.Query(q), rows.map(AcquireApi.Limit(_)))
            .map(r => Output.emit(json, r)(AcquireMarkdown.archiveSearch))
        case Command.ArchiveInfo(id, json) =>
          AcquireClient(c).archiveInfo(ArchiveId(id)).map(i => Output.emit(json, i)(AcquireMarkdown.archiveItem))
        case Command.ArchiveImport(id, citekey, format, json) =>
          AcquireClient(c)
            .archiveImport(
              ArchiveImportRequest(
                ArchiveImportRequest.Id(id),
                citekey.map(Citekey(_)),
                format.map(ArchiveImportRequest.Format(_))
              )
            )
            .map(r => Output.emit(json, r)(AcquireMarkdown.ingest))
        case Command.Assess(id, writes, force, json) =>
          AcquireClient(c)
            .assess(
              AssessRequest(
                AssessRequest.Id(id),
                (if writes.applies then AssessRequest.Apply.Apply else AssessRequest.Apply.DryRun),
                (if force then AssessRequest.ForceStatus.ForceStatus else AssessRequest.ForceStatus.KeepStatus)
              )
            )
            .map(a => Output.emit(json, a)(AcquireMarkdown.assessment))

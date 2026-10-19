package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import java.nio.file.{Files, Path}

/** The typed client for the acquisition endpoints — the same `AcquireApi` values the server
  * interprets, so the CLI cannot drift from the REST/MCP surface. It reuses [[Client]]'s transport
  * rather than opening a second one. */
final class AcquireClient(c: Client):
  def discover(q: String, since: Option[Int], sort: Option[String], limit: Option[Int], notInDb: Boolean)
      : Either[CliError, DiscoverResult] = c.call(AcquireApi.discover)((q, since, sort, limit, notInDb))
  def meta(id: String): Either[CliError, WorkRef]                = c.call(AcquireApi.meta)(id)
  def wikirefs(url: String): Either[CliError, WikirefsResult]    = c.call(AcquireApi.wikirefs)(url)
  def refs(req: RefsRequest): Either[CliError, RefsResult]       = c.call(AcquireApi.refs)(req)
  def assess(req: AssessRequest): Either[CliError, Assessment]   = c.call(AcquireApi.assess)(req)
  def updateRetractions: Either[CliError, RetractionsUpdate]     = c.call(AcquireApi.updateRetractions)(())
  def retractionsStatus: Either[CliError, RetractionsUpdate]     = c.call(AcquireApi.retractionsStatus)(())
  def followups(req: FollowupsRequest): Either[CliError, FollowupsResult] = c.call(AcquireApi.followups)(req)
  def ingest(req: IngestRequest): Either[CliError, IngestReport] = c.call(AcquireApi.ingest)(req)
  def archiveSearch(q: String, rows: Option[Int]): Either[CliError, ArchiveSearchResult] =
    c.call(AcquireApi.archiveSearch)((q, rows))
  def archiveInfo(id: String): Either[CliError, ArchiveItem]     = c.call(AcquireApi.archiveInfo)(id)
  def archiveImport(req: ArchiveImportRequest): Either[CliError, IngestReport] =
    c.call(AcquireApi.archiveImport)(req)
  def calibreGaps(limit: Option[Int]): Either[CliError, CalibreGaps] = c.call(AcquireApi.calibreGaps)(limit)
  def calibreMeta(citekey: String): Either[CliError, CalibreGap]     = c.call(AcquireApi.calibreMeta)(citekey)
  def calibrePushed(req: CalibrePushRequest): Either[CliError, CalibrePushResult] =
    c.call(AcquireApi.calibrePushed)(req)

/** `histmersion discover|refs|assess|followups|ingest|archive` — the acquisition & assessment commands
  * (plan §5.2 stream 2.6), each a thin wrapper over one endpoint that prints the SHARED Markdown
  * rendering, or the raw JSON with `--json`. */
object AcquireCmd:

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  private val discoverSearch = Opts.subcommand("search", "OpenAlex search for literature to ingest") {
    (
      Opts.argument[String]("query"),
      Opts.option[Int]("since", "Only works published from this year on").orNone,
      Opts.option[String]("sort", "relevance (default) | cited").orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.flag("not-in-db", "Only works the corpus does NOT have yet").orFalse,
      Output.jsonFlag
    ).mapN { (q, since, sort, limit, notInDb, json) => (c: Client) =>
      AcquireClient(c).discover(q, since, sort, limit, notInDb)
        .map(r => Output.emit(json, r)(AcquireMarkdown.discover))
    }
  }

  private val discoverMeta = Opts.subcommand("meta", "Normalised metadata for one DOI") {
    (Opts.argument[String]("doi"), Output.jsonFlag).mapN { (doi, json) => (c: Client) =>
      AcquireClient(c).meta(doi).map(w => Output.emit(json, w)(AcquireMarkdown.work))
    }
  }

  private val discoverWikirefs =
    Opts.subcommand("wikirefs", "Harvest the DOIs a Wikipedia article cites (the trailhead)") {
      (Opts.argument[String]("wikipedia-url"), Output.jsonFlag).mapN { (url, json) => (c: Client) =>
        AcquireClient(c).wikirefs(url).map(r => Output.emit(json, r)(AcquireMarkdown.wikirefs))
      }
    }

  val discover = Opts.subcommand("discover", "Find literature upstream (OpenAlex / Crossref / Wikipedia)") {
    discoverSearch orElse discoverMeta orElse discoverWikirefs
  }

  // ── refs / assess / followups ───────────────────────────────────────────────────────────────

  val refs = Opts.subcommand("refs", "The reference list a paper deposited, resolved against the corpus") {
    (
      Opts.argument[String]("citekey|doi"),
      Opts.flag("apply", "Write the resolved references as `references` edges").orFalse,
      Output.jsonFlag
    ).mapN { (id, apply, json) => (c: Client) =>
      AcquireClient(c).refs(RefsRequest(id, apply)).map(r => Output.emit(json, r)(AcquireMarkdown.refs))
    }
  }

  private val updateRetractions =
    Opts.subcommand("update-retractions", "Refresh the local Retraction Watch mirror (~60 000 rows)") {
      Output.jsonFlag.map { json => (c: Client) =>
        val client = AcquireClient(c)
        client.updateRetractions.flatMap { started =>
          if json then Right(Output.emit(json, started)(AcquireMarkdown.retractionsUpdate))
          else
            Console.err.println("refreshing the Retraction Watch mirror (~30 MB)…")
            pollRetractions(client, 0).map(r => println(AcquireMarkdown.retractionsUpdate(r)))
        }
      }
    }

  /** The server downloads and rewrites the mirror in the background — one HTTP request cannot hold a
    * 30 MB download plus 60 000 inserts — so the CLI drives the wait, exactly as `reindex` does. */
  @annotation.tailrec
  private def pollRetractions(c: AcquireClient, waited: Int): Either[CliError, RetractionsUpdate] =
    c.retractionsStatus match
      case Left(e) => Left(e)
      case Right(r) if !r.running =>
        r.error match
          case Some(msg) => Left(CliError(s"the refresh failed: $msg", exitCode = ExitCode.Refused))
          case None      => Right(r)
      case Right(_) if waited > 600 =>
        Left(CliError("the refresh is still running after 10 minutes — check the server log", exitCode = ExitCode.Refused))
      case Right(_) =>
        Thread.sleep(2000)
        Console.err.print(".")
        pollRetractions(c, waited + 2)

  private val assessOne =
    (
      Opts.argument[String]("citekey|doi"),
      Opts.flag("apply", "Write the verdict into the source's status (only over 'unassessed'; a retraction always)").orFalse,
      Opts.flag("force-status", "With --apply, overwrite a status that is already set").orFalse,
      Output.jsonFlag
    ).mapN { (id, apply, force, json) => (c: Client) =>
      AcquireClient(c).assess(AssessRequest(id, apply, force)).map(a => Output.emit(json, a)(AcquireMarkdown.assessment))
    }

  val assess = Opts.subcommand("assess",
    "Retraction / correction / impact verdict for a paper (retracted|corrected|current|low-impact)") {
    updateRetractions orElse assessOne
  }

  val followups = Opts.subcommand("followups",
    "Papers citing this one, with intents and a proposed edge kind (confirms|challenges|refutes|supersedes|cites)") {
    (
      Opts.argument[String]("citekey|doi"),
      Opts.option[Int]("since", "Only citing papers published from this year on").orNone,
      Opts.option[Int]("limit", "Max citing papers").orNone,
      Opts.flag("apply", "Record the proposed edges for citing papers already in the corpus").orFalse,
      Output.jsonFlag
    ).mapN { (id, since, limit, apply, json) => (c: Client) =>
      AcquireClient(c).followups(FollowupsRequest(id, since, limit, apply))
        .map(r => Output.emit(json, r)(AcquireMarkdown.followups))
    }
  }

  // ── ingest ──────────────────────────────────────────────────────────────────────────────────

  /** ONE `ingest`, two ways in — because from the user's side it is one intention.
    *
    * A **file** is uploaded and converted here (stream 2.4, §1.8); a **DOI or arxiv:id** is fetched
    * upstream by the server, which then hands the PDF to the very same OCR job. The argument decides:
    * anything that is a readable file on this machine takes the upload path. The flags of both halves
    * are accepted, and the ones that do not apply to the branch taken are simply unused. */
  val ingest = Opts.subcommand("ingest",
    "Bring a paper in: a local PDF/EPUB (uploaded and OCR'd, keeping its figures) or an identifier " +
      "(metadata → legal OA PDF → the same OCR job → reference edges → assessment)") {
    (
      Opts.argument[String]("file|doi|arxiv:id"),
      Opts.option[String]("citekey", "Citekey to store it under (required for a file; overrides the generated one for an identifier)").orNone,
      Opts.option[String]("arxiv", "arXiv id to try when the DOI has no OA copy").orNone,
      Opts.flag("quality", "File upload: slow, accurate OCR (docling) — for image-only scans").orFalse,
      Opts.flag("force", "File upload: accept a file already stored under another citekey").orFalse,
      Opts.option[String]("title", "File upload: title, used only when the source is being created").orNone,
      Opts.options[String]("author", "File upload: author, used only when the source is being created (repeatable)").orEmpty,
      Opts.option[Int]("year", "File upload: publication year, used only when the source is being created").orNone,
      Opts.option[String]("doi", "File upload: DOI, used only when the source is being created").orNone,
      Opts.option[String]("provenance", "How the source was acquired: manual|ill|author|scan|calibre|archive|oa (default: manual for file, oa for DOI)").orNone,
      Opts.flag("no-wait", "File upload: print the job id and return instead of following it").orFalse,
      Opts.flag("refetch", "Identifier: fetch and convert again although the source already has its original (replaces its body)").orFalse,
      Opts.option[Int]("acquire-item", "File upload: id of the acquisition-queue item this file fulfils — ticked off when the OCR job succeeds").orNone,
      Output.jsonFlag
    ).mapN { (id, citekey, arxiv, quality, force, title, authors, year, doi, prov, noWait, refetch, item, json) => (c: Client) =>
      val path = Path.of(id)
      if Files.isRegularFile(path) then
        IngestCmd.upload(c, path, citekey.getOrElse(""), quality, force, title, authors, year, doi, noWait, prov, item)
      else if item.isDefined then
        Left(CliError("--acquire-item applies to a file upload; for an identifier, tick the item off with " +
          "`histmersion queue done <id>` once the source is in", exitCode = ExitCode.Usage))
      else
        AcquireClient(c).ingest(IngestRequest(id, citekey, arxiv, refetch))
          .map(r => Output.emit(json, r)(AcquireMarkdown.ingest))
    }
  }

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  private val archiveSearch = Opts.subcommand("search", "Search archive.org texts") {
    (Opts.argument[String]("query"), Opts.option[Int]("rows", "Max results").orNone, Output.jsonFlag)
      .mapN { (q, rows, json) => (c: Client) =>
        AcquireClient(c).archiveSearch(q, rows).map(r => Output.emit(json, r)(AcquireMarkdown.archiveSearch))
      }
  }

  private val archiveInfo = Opts.subcommand("info", "One archive.org item: files and lending restriction") {
    (Opts.argument[String]("identifier"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
      AcquireClient(c).archiveInfo(id).map(i => Output.emit(json, i)(AcquireMarkdown.archiveItem))
    }
  }

  private val archiveImport = Opts.subcommand("import", "Import a PUBLIC archive.org item (lending-only is refused)") {
    (
      Opts.argument[String]("identifier"),
      Opts.option[String]("citekey", "Override the generated citekey").orNone,
      Opts.option[String]("format", "epub | pdf (default: whichever carries text)").orNone,
      Output.jsonFlag
    ).mapN { (id, citekey, format, json) => (c: Client) =>
      AcquireClient(c).archiveImport(ArchiveImportRequest(id, citekey, format))
        .map(r => Output.emit(json, r)(AcquireMarkdown.ingest))
    }
  }

  val archive = Opts.subcommand("archive", "Internet Archive: search, inspect and import public texts") {
    archiveSearch orElse archiveInfo orElse archiveImport
  }

  /** Everything this stream adds to `histmersion`, in one value so `Main` gains a single line. */
  val all: Opts[Client => Either[CliError, Unit]] =
    discover orElse refs orElse assess orElse followups orElse ingest orElse archive

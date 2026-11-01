package com.histmersion.domain.library.sources

import com.histmersion.api.*
import com.histmersion.config.AppConfig
import com.histmersion.db.Importer
import com.histmersion.domain.Caller
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.files.{Export, Frontmatter}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind
import com.histmersion.values.Citekey

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, LocalDate, ZoneOffset}

/** A source over REST and MCP: read whole or by section, its passages, its note, written whole or in part, renamed,
  * deleted, its formatting checked and repaired, a note imported. Every citekey asked for is resolved first (an old one
  * names the source it was renamed to); every write is refused while maintenance holds the database.
  *
  * `figures` are media's (a source's illustration, a note's figures), `formatting` research's prose lint, `mentions`
  * the "Mentioned in articles" part research writes of a source, `originals` where its stored files are.
  */
final class SourcesControllers(
    sources: SourceService,
    figures: AssetsControllers,
    formatting: SourceFormatting,
    originals: SourceOriginals,
    mentions: String => String,
    current: (AliasKind, String) => String,
    cfg: () => AppConfig,
    maintenance: WriteGate,
    clock: Clock
):

  /** THE citekey resolution (V027): the citekey a source has NOW, when `id` is an old one it was renamed from;
    * otherwise `id` unchanged (and a later lookup reports it unknown).
    */
  def canonical(id: String): String = sources.current(id).getOrElse(id)

  def get(citekey: String, body: Boolean = true): Either[ApiError, SourceWithFindings] =
    sources
      .get(canonical(citekey))
      .map((s, fs) =>
        SourceWithFindings(
          if body then s else s.modify(_.ocrMarkdown).setTo(None),
          fs,
          illustration = figures.sourceThumb(s.citekey)
        )
      )
      .toRight(ApiError(s"No source '$citekey'"))

  def citekeys: List[String] = sources.citekeys

  /** Every old citekey and the source it names now. */
  def aliases: List[SourceAlias] = sources.aliases

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def note(citekey: String): Either[ApiError, String] =
    sources.load(canonical(citekey)).map(Export.note).toRight(Write.notFound(citekey))

  // ── writes ─────────────────────────────────────────────────────────────────────────────────

  /** Create or REPLACE everything under one citekey. An OLD citekey follows the alias (#1799): the put replaces the
    * source it was renamed to — it never creates a second source under the old name, which the alias would then shadow.
    */
  def put(citekey: String, in: SourcePut, strict: Boolean = false): Either[ApiError, SourceWithFindings] =
    maintenance.guard("put_source"):
      Citekey.of(citekey).map(_ => current(AliasKindsAdapter.SourceRecords, citekey)).flatMap { ck =>
        Write
          .applyPut(ck, in, sources.aliasesOf(ck))
          .left
          .map(m => ApiError(m, Some(Write.CodeInvalid)))
          .flatMap(s => strictGate(strict, formatting.issues(s.summary, s.notes)).map(_ => s))
          .flatMap { s =>
            val _ = sources.put(s, Caller.Local); get(ck).map(withFormatting)
          }
      }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patch(citekey: String, p: SourcePatch, strict: Boolean = false): Either[ApiError, SourceWithFindings] =
    maintenance.guard("patch_source"):
      Citekey
        .of(citekey)
        // only what this patch writes is gated: a strict patch of the tags is not refused for old notes
        .flatMap(_ => strictGate(strict, formatting.issues(p.summary, p.notes)))
        .flatMap(_ => sources.patch(citekey, p, Caller.Local))
        .flatMap(_ => get(citekey))
        .map(withFormatting)

  /** `POST /api/sources/{ck}/rename` — everything the rename touches is the service's and the referrers'. */
  def rename(citekey: String, req: RenameRequest): Either[ApiError, SourceRenamed] =
    maintenance.guard("rename_source"):
      val from = current(AliasKindsAdapter.SourceRecords, citekey)
      sources.rename(from, req.newCitekey, Caller.Local).flatMap { counts =>
        val now = req.newCitekey.trim
        get(now, body = false).map(sw => SourceRenamed(from, now, sw.source, counts, sources.calibreId(now)))
      }

  /** `POST /api/sources/{ck}/delete` (#969) — a dry run unless `apply`. Applied, the database half is one transaction
    * (refused while anything cites the source), and the stored originals are removed after the commit, best effort. Not
    * an MCP tool, like deleting an article: a person's decision.
    */
  def delete(citekey: String, req: SourceDeleteRequest): Either[ApiError, SourceDeletion] =
    val ck = current(AliasKindsAdapter.SourceRecords, citekey)
    if !req.apply then sources.deletion(ck).map(p => p.deletion.modify(_.files).setTo(originals.find(p)))
    else
      maintenance.guard("delete_source"):
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString
        // the files are named inside the transaction (they are in its log line) and go only after it committed
        sources
          .delete(ck, today, originals.find, Caller.Local)
          .map { p =>
            scribe.info(SourceDelete.logText(p.deletion))
            p.deletion.modify(_.filesFailed).setTo(originals.remove(p.deletion.files))
          }

  /** An IMPORT of a note must not land under an OLD citekey — the one exception to following the alias: a note named by
    * an old citekey is a stale mirror file the export leaves behind (and prunes), and its findings are numbered under
    * the old name.
    */
  private def notAnAlias(ck: String): Either[ApiError, String] =
    sources.aliasOf(ck) match
      case Some(now) =>
        Left(ApiError(s"'$ck' was renamed to '$now' — write to '$now' instead", Some(Write.CodeInvalid)))
      case None => Right(ck)

  /** Seed ONE note from raw text (the host-side path of `histmersion seed`, for a corpus dir the SERVER cannot see).
    * Applies the seed's "stored row is newer than the file" guard whenever the client sends the file's mtime.
    */
  def importNote(req: NoteImport): Either[ApiError, SeedResult] = maintenance.guard("import"):
    Frontmatter.parseNote(req.text, req.citekey.getOrElse("")) match
      case None         => Left(ApiError("no YAML frontmatter in the note"))
      case Some(parsed) =>
        val ck = parsed.citekey
        if ck.isEmpty then Left(ApiError("note has no citekey (none in the frontmatter, none supplied)"))
        else if Citekey.parse(ck).isLeft then Citekey.of(ck).map(_ => SeedResult(0, Nil, 0, Nil))
        else if sources.aliasOf(ck).isDefined then notAnAlias(ck).map(_ => SeedResult(0, Nil, 0, Nil))
        else if !req.force && req.mtime.exists(m => Importer.isStale(sources.updatedAtMillis, ck, m))
        then Right(SeedResult(0, Nil, 0, List(ck)))
        else
          // `body` is the converted paper text the client read from `md_path`; NUL bytes occur in some
          // OCR output and Postgres `text` rejects them.
          val withBody = req.body.map(_.filterNot(_.toInt == 0)).filter(_.nonEmpty) match
            case Some(b) => parsed.modify(_.ocrMarkdown).setTo(Some(b))
            case None    => parsed
          // figures of the old file pipeline (plan §1.8): every `…/figures/<name>` spelling is
          // normalised to the canonical `assets/<name>` and the OCR's picture-text dumps folded
          // away, and any base64 payloads that travelled with the note stored
          val normalised =
            withBody.modify(_.ocrMarkdown).using(_.map(AssetLinks.normalizeBody(_, withBody.title)))
          val _ = sources.put(normalised, Caller.Local)
          if req.assets.isEmpty then Right(SeedResult(1, Nil, 0, Nil))
          else figures.putFigures(ck, req.assets).map(_ => SeedResult(1, Nil, 0, Nil))

  // ── formatting: what in the agent-written Markdown will not display as meant ─────────────────

  /** A source's formatting problems, its links resolved against the corpus. */
  def formatIssues(summary: Option[String], notes: Option[String]): List[FormatIssue] =
    formatting.issues(summary, notes)

  private def withFormatting(sw: SourceWithFindings): SourceWithFindings =
    sw.modify(_.formatting).setTo(formatting.issues(sw.source.summary, sw.source.notes))

  /** `strict`: refuse a write whose text has a formatting ERROR; otherwise the problems ride along in the response, and
    * the write goes through.
    */
  private def strictGate(strict: Boolean, issues: => List[FormatIssue]): Either[ApiError, Unit] =
    if !strict then Right(())
    else formatting.refusal(issues).fold(Right(()))(m => Left(ApiError(m, Some(Write.CodeInvalid))))

  /** `GET /api/sources/{citekey}/lint`. */
  def lint(citekey: String): Either[ApiError, FormatReport] =
    get(citekey, body = false).map(sw =>
      FormatReport(sw.source.citekey, formatIssues(sw.source.summary, sw.source.notes))
    )

  /** `POST /api/sources/{citekey}/lint/fix` — the wording-safe repairs to summary and notes, written as one patch
    * guarded by the `updatedAt` just read, then the report of what is left.
    */
  def fix(citekey: String): Either[ApiError, FormatReport] =
    maintenance.guard("fix_source_format"):
      sources.load(canonical(citekey)).toRight(Write.notFound(citekey)).flatMap { s =>
        val (summary, fs) =
          s.summary.map(t => formatting.fix(t, FormatField.Summary)).fold((None, Nil))((t, f) => (Some(t), f))
        val (notes, fn) =
          s.notes.map(t => formatting.fix(t, FormatField.Notes)).fold((None, Nil))((t, f) => (Some(t), f))
        val done = fs.map("summary: " + _) ++ fn.map("notes: " + _)
        val write =
          if done.isEmpty then Right(())
          else
            sources
              .patch(
                s.citekey,
                SourcePatch(
                  summary = summary.filter(_ => fs.nonEmpty),
                  notes = notes.filter(_ => fn.nonEmpty),
                  ifUpdatedAt = s.updatedAt
                ),
                Caller.Local
              )
              .map(_ => ())
        write.flatMap(_ => lint(s.citekey)).map(_.modify(_.fixed).setTo(done))
      }

  // ── reading one source by part ─────────────────────────────────────────────────────────────

  /** `get_source` as Markdown, with image links made ABSOLUTE (plan §1.8) so an image-capable MCP client can fetch the
    * figures. The ONE renderer both MCP and the article page go through.
    *
    * Sectioning (A1/A7/A18): `section` selects what to return; `heading` filters to chunks under one heading;
    * `offset`/`chars` slice the body.
    */
  def markdown(
      requested: String,
      fullText: Boolean,
      section: Option[String] = None,
      heading: Option[String] = None,
      offset: Option[Int] = None,
      chars: Option[Int] = None
  ): Either[ApiError, String] =
    val publicUrl = cfg().server.publicUrl
    val sec = section.flatMap(SourceSection.parse)
    val badSection = section.filter(_.nonEmpty).exists(_ => sec.isEmpty)
    if badSection then
      Left(ApiError(s"unknown section '${section.getOrElse("")}' — one of: ${SourceSection.all.mkString(", ")}"))
    else
      get(requested).map { sw =>
        val citekey = sw.source.citekey
        sec match
          case Some(SourceSection.Toc) =>
            TocEntry.markdown(sources.toc(citekey))

          case Some(SourceSection.Body) =>
            val bodyText = bodySlice(citekey, heading, offset, chars)
            AssetLinks.toAbsolute(bodyText, citekey, publicUrl)

          case Some(SourceSection.Summary) =>
            val s = sw.source
            val authStr = Markdown.chipLinks(s.authors, "author")
            val meta = List(authStr, Markdown.periodLabel(s.periodStart, s.periodEnd))
              .filter(_.nonEmpty)
              .mkString(" · ")
            s"# ${s.title}\n\n_${meta}_" +
              s.summary.map(x => s"\n\n${x.trim}").getOrElse("")

          case Some(SourceSection.Findings) =>
            Markdown.sourceFindingsOnly(sw.findings.toVector)

          case Some(SourceSection.Notes) =>
            sw.source.notes
              .map(_.trim)
              .filter(_.nonEmpty)
              .map(n => s"## Notes\n\n$n")
              .getOrElse("_(no notes)_")

          case Some(SourceSection.All) =>
            val ft = fullText || offset.isDefined || heading.isDefined
            val md = if ft && (offset.isDefined || chars.isDefined) then
              val bodyText = bodySlice(citekey, heading, offset, chars)
              Markdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
            else Markdown.source(sw.source, sw.findings.toVector, ft)
            AssetLinks.toAbsolute(md + mentions(citekey), citekey, publicUrl)

          case Some(_) =>
            Markdown.source(sw.source, sw.findings.toVector, false)

          case None =>
            val md =
              if fullText && (offset.isDefined || chars.isDefined) then
                val bodyText = bodySlice(citekey, heading, offset, chars)
                Markdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
              else Markdown.source(sw.source, sw.findings.toVector, fullText)
            AssetLinks.toAbsolute(md + mentions(citekey), citekey, publicUrl)
      }

  /** `passages` (#954): find text inside ONE source's body — what `get --full | grep` is in a shell. The body is read
    * once (chunks, or the raw text of an unchunked source) and searched in memory by [[PassageFinder]], so the offsets
    * are those [[bodySlice]] uses. An old citekey resolves like every read; an unknown one is a 404, an empty query or
    * a bad regex a 400.
    */
  def passages(q: Api.PassagesQuery): Either[ApiError, PassageResult] =
    val lim = cfg().limits.passages
    sources
      .current(q.citekey)
      .toRight(ApiError(s"No source '${q.citekey}'", Some(Write.CodeNotFound)))
      .flatMap { ck =>
        PassageFinder
          .find(
            ck,
            sources.bodyChunks(ck),
            q.q,
            q.regex,
            lim.limitOf(q.limit),
            lim.contextOf(q.context),
            lim.maxMatches,
            lim.regexTimeoutMs.toLong
          )
          .left
          .map(m => ApiError(m, Some(Write.CodeInvalid)))
      }

  /** Read the body (from chunks or raw `ocr_markdown`), optionally filtered by heading and sliced. */
  private def bodySlice(citekey: String, heading: Option[String], offset: Option[Int], chars: Option[Int]): String =
    val raw = heading match
      case Some(h) => sources.bodyUnderHeading(citekey, h)
      case None    => sources.bodyText(citekey)
    val total = raw.length
    val off = offset.filter(_ > 0).getOrElse(0).min(total)
    val cap = chars.filter(_ > 0).getOrElse(cfg().limits.fullTextChars)
    val slice = raw.substring(off, math.min(total, off + cap))
    Markdown.bodySlice(slice, off, total)

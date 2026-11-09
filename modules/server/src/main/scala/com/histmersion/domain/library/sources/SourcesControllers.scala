package com.histmersion
package domain.library.sources

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.library.SourcesMarkdown

import com.histmersion.api.{Api, Markdown, Write}
import com.histmersion.config.{AppConfig, PassageLimits}
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.domain.library.ingest.{SeedImport, SeedNote}
import com.histmersion.domain.Caller
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind
import com.histmersion.domain.library.Citekey

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, LocalDate, ZoneOffset}
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatReport}
import com.histmersion.api.library.{NoteImport, PassageResult, SeedResult, TocEntry}
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.api.personal.UnreadPage
import com.histmersion.util.time.Timer
import io.github.iltotore.iron.constraint.any.Pure

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
    mentions: Citekey => SourcesControllers.Markdown,
    current: (AliasKind, Citekey) => Citekey,
    cfg: () => AppConfig,
    maintenance: WriteGate,
    clock: Clock
)(using timer: Timer):

  /** A citekey as a client sent it, or the 400 that refuses it: the one place a request's citekey becomes a domain one.
    */
  def parseCitekey(ck: com.histmersion.api.library.Citekey): Either[Api.Error, Citekey] =
    Citekey.parse(ck.value).left.map(p => Write.invalid(p))

  private def apiCk(c: Citekey): com.histmersion.api.library.Citekey = com.histmersion.api.library.Citekey(c.value)

  private def currentOf(id: Citekey): Option[Citekey] =
    sources.current(id)

  private def notFound(citekey: Citekey): Api.Error =
    Api.Error(Problem.Unclassified(Legacy.Text(s"No source '$citekey'")), Some(Api.Error.Code(Write.CodeNotFound)))

  private def absolute(md: String, citekey: Citekey): String =
    AssetLinks.toAbsolute(AssetLinks.Body(md), apiCk(citekey), AssetLinks.Url(cfg().server.publicUrl.value)).value

  private def issuesOf(summary: Option[Source.Summary], notes: Option[Source.Notes]): List[FormatIssue] =
    formatting.issues(
      summary.map(s => com.histmersion.domain.library.Source.Summary(s.value)),
      notes.map(n => SourceNote.Text(n.value))
    )

  /** THE citekey resolution (V027): the citekey a source has NOW, when `id` is an old one it was renamed from;
    * otherwise `id` unchanged (and a later lookup reports it unknown).
    */
  def canonical(id: Citekey): Citekey = currentOf(id).getOrElse(id)

  def get(
      citekey: Citekey,
      body: SourcesControllers.GetBody = SourcesControllers.GetBody(true)
  ): Either[Api.Error, Source.WithFindings] =
    val ck = canonical(citekey)
    sources
      .get(ck)
      .map((s, fs) =>
        Source.WithFindings(
          if body.value then s else s.modify(_.ocrMarkdown).setTo(None),
          fs,
          illustration = figures.sourceThumb(ck)
        )
      )
      .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"No source '$citekey'"))))

  def citekeys: List[Citekey] = sources.citekeys

  /** Every old citekey and the source it names now. */
  def aliases: List[Source.Alias] = sources.aliases

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def note(citekey: Citekey): Either[Api.Error, SourceNote.Text] =
    sources.load(canonical(citekey)).map(SourceNote.note).toRight(notFound(citekey))

  // ── writes ─────────────────────────────────────────────────────────────────────────────────

  /** Create or REPLACE everything under one citekey. An OLD citekey follows the alias (#1799): the put replaces the
    * source it was renamed to — it never creates a second source under the old name, which the alias would then shadow.
    */
  def put(
      citekey: Citekey,
      in: Source.Put,
      strict: SourcesControllers.PutStrict = SourcesControllers.PutStrict(false)
  ): Either[Api.Error, Source.WithFindings] =
    maintenance.guard(WriteGate.OperationName("put_source")):
      val ck = current(AliasKinds.SourceRecords, citekey)
      Source.Write
        .applyPut(apiCk(ck), in, sources.aliasesOf(ck).map(apiCk))
        .left
        .map(m => Write.invalid(m))
        .flatMap(s => strictGate(strict.value, issuesOf(s.summary, s.notes)).map(_ => s))
        .flatMap { s =>
          val _ = sources.put(s, Caller.Local); get(ck).map(withFormatting)
        }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patch(
      citekey: Citekey,
      p: Source.Patch,
      strict: SourcesControllers.PatchStrict = SourcesControllers.PatchStrict(false)
  ): Either[Api.Error, Source.WithFindings] =
    maintenance.guard(WriteGate.OperationName("patch_source")):
      // only what this patch writes is gated: a strict patch of the tags is not refused for old notes
      strictGate(strict.value, issuesOf(p.summary, p.notes))
        .flatMap(_ => sources.patch(citekey, p, Caller.Local))
        .flatMap(_ => get(citekey))
        .map(withFormatting)

  /** `POST /api/sources/{ck}/rename` — everything the rename touches is the service's and the referrers'. */
  def rename(citekey: Citekey, req: Rename.Request): Either[Api.Error, Source.Renamed] =
    maintenance.guard(WriteGate.OperationName("rename_source")):
      val from = current(AliasKinds.SourceRecords, citekey)
      Citekeys
        .renameTo(from, Citekeys.RenameToNewRaw(req.newCitekey.value))
        .left
        .map(m => Write.invalid(m))
        .flatMap { now =>
          sources.rename(from, now, Caller.Local).flatMap { counts =>
            get(now, body = SourcesControllers.GetBody(false)).map(sw =>
              Source.Renamed(
                apiCk(from),
                apiCk(now),
                sw.source,
                counts,
                sources.calibreId(now).map(c => Source.CalibreId(c.value))
              )
            )
          }
        }

  /** `POST /api/sources/{ck}/delete` (#969) — a dry run unless `apply`. Applied, the database half is one transaction
    * (refused while anything cites the source), and the stored originals are removed after the commit, best effort. Not
    * an MCP tool, like deleting an article: a person's decision.
    */
  def delete(citekey: Citekey, req: Source.DeleteRequest): Either[Api.Error, Source.Deletion] =
    val ck = current(AliasKinds.SourceRecords, citekey)
    if !req.apply.value then
      sources
        .deletion(ck)
        .map(p => p.deletion.modify(_.files).setTo(originals.find(p).map(f => Source.Deletion.Files(f.value))))
    else
      maintenance.guard(WriteGate.OperationName("delete_source")):
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString
        // the files are named inside the transaction (they are in its log line) and go only after it committed
        sources
          .delete(
            ck,
            SourceReferrers.Day(today),
            p => originals.find(p).map(f => Source.Deletion.Files(f.value)),
            Caller.Local
          )
          .map { p =>
            scribe.info(Source.Delete.logText(p.deletion).value)
            p.deletion
              .modify(_.filesFailed)
              .setTo(
                originals
                  .remove(p.deletion.files.map(f => SourceOriginals.RemoveFiles(f.value)))
                  .map(r => Source.Deletion.FilesFailed(r.value))
              )
          }

  /** An IMPORT of a note must not land under an OLD citekey — the one exception to following the alias: a note named by
    * an old citekey is a stale mirror file the export leaves behind (and prunes), and its findings are numbered under
    * the old name.
    */
  private def notAnAlias(ck: Citekey): Either[Api.Error, Citekey] =
    sources.aliasOf(ck) match
      case Some(now) =>
        Left(Write.invalid(Problem.Unclassified(Legacy.Text(s"'$ck' was renamed to '$now' — write to '$now' instead"))))
      case None => Right(ck)

  /** Seed ONE note from raw text (the host-side path of `histmersion seed`, for a corpus dir the SERVER cannot see).
    * Applies the seed's "stored row is newer than the file" guard whenever the client sends the file's mtime.
    */
  def importNote(req: NoteImport): Either[Api.Error, SeedResult] = maintenance.guard(WriteGate.OperationName("import")):
    SourceNote.parseNote(
      SourceNote.Text(req.text.value),
      req.citekey.getOrElse(com.histmersion.api.library.Citekey(""))
    ) match
      case None         => Left(Api.Error(Problem.Unclassified(Legacy.Text("no YAML frontmatter in the note"))))
      case Some(parsed) =>
        val raw = parsed.citekey
        if raw.value.isEmpty then
          Left(
            Api.Error(Problem.Unclassified(Legacy.Text("note has no citekey (none in the frontmatter, none supplied)")))
          )
        else
          Citekey.parse(raw.value) match
            case Left(problem) => Left(Write.invalid(problem))
            case Right(ck)     =>
              if sources.aliasOf(ck).isDefined then
                notAnAlias(ck).map(_ => SeedResult(SeedResult.Imported(0), Nil, SeedResult.EdgesResolved(0), Nil))
              else if !req.force.value && req.mtime.exists(m =>
                  SeedImport.isStale(sources.updatedAtMillis, ck, SeedNote.ModifiedMillis(m.value))
                )
              then
                Right(
                  SeedResult(
                    SeedResult.Imported(0),
                    Nil,
                    SeedResult.EdgesResolved(0),
                    List(SeedResult.Skipped(raw.value))
                  )
                )
              else
                // `body` is the converted paper text the client read from `md_path`; NUL bytes occur in some
                // OCR output and Postgres `text` rejects them.
                val withBody = req.body.map(_.value.filterNot(_.toInt == 0)).filter(_.nonEmpty) match
                  case Some(b) => parsed.modify(_.ocrMarkdown).setTo(Some(Source.OcrMarkdown(b)))
                  case None    => parsed
                // figures of the old file pipeline (plan §1.8): every `…/figures/<name>` spelling is
                // normalised to the canonical `assets/<name>` and the OCR's picture-text dumps folded
                // away, and any base64 payloads that travelled with the note stored
                val normalised =
                  withBody
                    .modify(_.ocrMarkdown)
                    .using(
                      _.map(m =>
                        Source.OcrMarkdown(
                          AssetLinks
                            .normalizeBody(AssetLinks.Body(m.value), AssetLinks.Title(withBody.title.value))
                            .value
                        )
                      )
                    )
                val _ = sources.put(normalised, Caller.Local)
                if req.assets.isEmpty then
                  Right(SeedResult(SeedResult.Imported(1), Nil, SeedResult.EdgesResolved(0), Nil))
                else
                  figures
                    .putFigures(ck, req.assets)
                    .map(_ => SeedResult(SeedResult.Imported(1), Nil, SeedResult.EdgesResolved(0), Nil))

  // ── formatting: what in the agent-written Markdown will not display as meant ─────────────────

  /** A source's formatting problems, its links resolved against the corpus. */
  def formatIssues(summary: Option[Source.Summary], notes: Option[Source.Notes]): List[FormatIssue] =
    issuesOf(summary, notes)

  private def withFormatting(sw: Source.WithFindings): Source.WithFindings =
    sw.modify(_.formatting).setTo(issuesOf(sw.source.summary, sw.source.notes))

  /** `strict`: refuse a write whose text has a formatting ERROR; otherwise the problems ride along in the response, and
    * the write goes through.
    */
  private def strictGate(strict: Boolean, issues: => List[FormatIssue]): Either[Api.Error, Unit] =
    if !strict then Right(())
    else formatting.refusal(issues).fold(Right(()))(m => Left(Write.invalid(m)))

  /** `GET /api/sources/{citekey}/lint`. */
  def lint(citekey: Citekey): Either[Api.Error, FormatReport] =
    get(citekey, body = SourcesControllers.GetBody(false)).map(sw =>
      FormatReport(FormatReport.Subject(sw.source.citekey.value), formatIssues(sw.source.summary, sw.source.notes))
    )

  /** `POST /api/sources/{citekey}/lint/fix` — the wording-safe repairs to summary and notes, written as one patch
    * guarded by the `updatedAt` just read, then the report of what is left.
    */
  def fix(citekey: Citekey): Either[Api.Error, FormatReport] =
    maintenance.guard(WriteGate.OperationName("fix_source_format")):
      val ck = canonical(citekey)
      sources.load(ck).toRight(notFound(citekey)).flatMap { s =>
        val (summary, fs) =
          s.summary
            .map(t => formatting.fix(com.histmersion.domain.library.Source.Text(t.value), FormatField.Summary))
            .fold((None, Nil))((t, f) => (Some(t), f))
        val (notes, fn) =
          s.notes
            .map(t => formatting.fix(com.histmersion.domain.library.Source.Text(t.value), FormatField.Notes))
            .fold((None, Nil))((t, f) => (Some(t), f))
        val done = fs.map(f => "summary: " + f.value) ++ fn.map(f => "notes: " + f.value)
        val write =
          if done.isEmpty then Right(())
          else
            sources
              .patch(
                ck,
                Source.Patch(
                  summary = summary.filter(_ => fs.nonEmpty).map(t => Source.Summary(t.value)),
                  notes = notes.filter(_ => fn.nonEmpty).map(t => Source.Notes(t.value)),
                  ifUpdatedAt = s.updatedAt
                ),
                Caller.Local
              )
              .map(_ => ())
        write.flatMap(_ => lint(ck)).map(_.modify(_.fixed).setTo(done.map(FormatReport.Fixed(_))))
      }

  // ── reading one source by part ─────────────────────────────────────────────────────────────

  /** `get_source` as Markdown, with image links made ABSOLUTE (plan §1.8) so an image-capable MCP client can fetch the
    * figures. The ONE renderer both MCP and the article page go through.
    *
    * Sectioning (A1/A7/A18): `section` selects what to return; `heading` filters to chunks under one heading;
    * `offset`/`chars` slice the body.
    */
  def markdown(
      requested: Citekey,
      fullText: SourcesControllers.MarkdownFullText,
      section: Option[Source.Section.Wire] = None,
      heading: Option[SourceService.Heading] = None,
      offset: Option[Offset] = None,
      chars: Option[SourcesControllers.Chars] = None
  ): Either[Api.Error, SourcesControllers.Markdown] =
    val sec = section.flatMap(Source.Section.parse).map(_.value)
    val badSection = section.filter(_.value.nonEmpty).exists(_ => sec.isEmpty)
    if badSection then
      Left(
        Api.Error(
          Problem.Unclassified(
            Legacy.Text(
              s"unknown section '${section.map(_.value).getOrElse("")}' — one of: ${Source.Section.all.map(_.value).mkString(", ")}"
            )
          )
        )
      )
    else
      get(requested).map { sw =>
        val citekey = canonical(requested)
        val text: String = sec match
          case Some(Source.Section.Toc) =>
            TocEntry.markdown(sources.toc(citekey)).value

          case Some(Source.Section.Body) =>
            val bodyText = bodySlice(citekey, heading, offset, chars)
            absolute(bodyText, citekey)

          case Some(Source.Section.Summary) =>
            val s = sw.source
            val authStr = Markdown.chipLinks(s.authors.map(_.value), "author")
            val meta = List(authStr, Markdown.periodLabel(s.periodStart, s.periodEnd))
              .filter(_.nonEmpty)
              .mkString(" · ")
            s"# ${s.title}\n\n_${meta}_" +
              s.summary.map(x => s"\n\n${x.value.trim}").getOrElse("")

          case Some(Source.Section.Findings) =>
            SourcesMarkdown.sourceFindingsOnly(sw.findings.toVector)

          case Some(Source.Section.Notes) =>
            sw.source.notes
              .map(_.value.trim)
              .filter(_.nonEmpty)
              .map(n => s"## Notes\n\n$n")
              .getOrElse("_(no notes)_")

          case Some(Source.Section.All) =>
            val ft = fullText.value || offset.isDefined || heading.isDefined
            val md = if ft && (offset.isDefined || chars.isDefined) then
              val bodyText = bodySlice(citekey, heading, offset, chars)
              SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
            else SourcesMarkdown.source(sw.source, sw.findings.toVector, ft)
            absolute(md + mentions(citekey).value, citekey)

          case Some(_) =>
            SourcesMarkdown.source(sw.source, sw.findings.toVector, false)

          case None =>
            val md =
              if fullText.value && (offset.isDefined || chars.isDefined) then
                val bodyText = bodySlice(citekey, heading, offset, chars)
                SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
              else SourcesMarkdown.source(sw.source, sw.findings.toVector, fullText.value)
            absolute(md + mentions(citekey).value, citekey)
        SourcesControllers.Markdown(text)
      }

  /** `passages` (#954): find text inside ONE source's body — what `get --full | grep` is in a shell. The body is read
    * once (chunks, or the raw text of an unchunked source) and searched in memory by [[PassageFinder]], so the offsets
    * are those [[bodySlice]] uses. An old citekey resolves like every read; an unknown one is a 404, an empty query or
    * a bad regex a 400.
    */
  def passages(q: Api.PassagesQuery): Either[Api.Error, PassageResult] =
    val lim = cfg().limits.passages
    Citekey
      .parse(q.citekey.value)
      .toOption
      .flatMap(currentOf)
      .toRight(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"No source '${q.citekey}'")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )
      .flatMap { ck =>
        PassageFinder
          .find(
            ck,
            sources.bodyChunks(ck),
            PassageFinder.Query(q.q.value),
            PassageFinder.Regex(q.regex.value),
            PassageFinder.Limit(lim.limitOf(q.limit.map(l => PassageLimits.Limit(l.value))).value),
            PassageFinder.ContextChars(lim.contextOf(q.context.map(c => PassageLimits.Context(c.value))).value),
            PassageFinder.MaxMatches(lim.maxMatches.value),
            PassageFinder.TimeoutMs(lim.regexTimeoutMs.value.toLong)
          )
          .left
          .map(m => Write.invalid(m))
      }

  /** Read the body (from chunks or raw `ocr_markdown`), optionally filtered by heading and sliced. */
  private def bodySlice(
      citekey: Citekey,
      heading: Option[SourceService.Heading],
      offset: Option[Offset],
      chars: Option[SourcesControllers.Chars]
  ): String =
    val raw = (heading match
      case Some(h) => sources.bodyUnderHeading(citekey, h)
      case None    => sources.bodyText(citekey)
    ).value
    val total = raw.length
    val off = offset.map(_.value).filter(_ > 0).getOrElse(0).min(total)
    val cap = chars.map(_.value).filter(_ > 0).getOrElse(cfg().limits.fullTextChars.value)
    val slice = raw.substring(off, math.min(total, off + cap))
    Markdown.bodySlice(Source.Body(slice), UnreadPage.Offset(off), total)

object SourcesControllers:
  type Chars = Chars.T
  object Chars extends Newtype[Int, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  type GetBody = GetBody.T
  object GetBody extends Newtype[Boolean, Pure]
  type PutStrict = PutStrict.T
  object PutStrict extends Newtype[Boolean, Pure]
  type PatchStrict = PatchStrict.T
  object PatchStrict extends Newtype[Boolean, Pure]
  type MarkdownFullText = MarkdownFullText.T
  object MarkdownFullText extends Newtype[Boolean, Pure]

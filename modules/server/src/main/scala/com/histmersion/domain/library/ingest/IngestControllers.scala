package com.histmersion
package domain.library.ingest

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Text.sentence
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.worklists.{QueueItemId, QueueService}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.storage.{Blob, Store}
import com.histmersion.domain.library.Citekey

import hearth.kindlings.optics.syntax.*

import scala.util.Using
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.Ingest
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.Api
import com.histmersion.domain.Title
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.acquisition.AcquireAttemptRow
import com.histmersion.api.library.AuthorName
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The ingest surface (plan §1.8) — REST routes, MCP tools and the CLI all reach these:
  *
  *   1. the upload is stored in the originals' store as `<sha256>.<ext>` — which is what makes `reocr` possible without
  *      a second upload, and what makes a re-run idempotent;
  *   2. a duplicate (same sha256, different citekey) is refused with 409 naming the existing citekey, unless `force`;
  *   3. a `jobs` row is created and queued; the caller gets its id immediately (202) — the job is [[IngestQueue]]'s.
  *
  * `ingest <doi>`, `archive import` and `calibre import` come through here too ([[QueuedIngest]]).
  */
final class IngestControllers(
    sources: SourceService,
    queues: QueueService,
    queue: IngestQueue,
    originals: () => Store,
    tools: ToolFiles,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  /** `POST /api/ingest`. `upload` is copied into the originals' store BEFORE returning: the HTTP layer deletes its file
    * as soon as the request completes, and the job runs long after that.
    */
  def ingest(p: Ingest.Params, upload: Upload)(using Creator.New): Either[Api.Error, Ingest.Accepted] =
    maintenance.guard(WriteGate.OperationName("ingest"))(ingestNow(p, upload))

  private def error(message: Problem, code: Option[Api.Error.Code] = None): Api.Error =
    Api.Error(message, code)

  private def invalid(p: Problem): Api.Error = Write.invalid(p)

  private def invalidCitekey(p: Problem): Api.Error =
    Write.invalid(p, Api.Error.InvalidCitekey)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def ingestNow(p: Ingest.Params, upload: Upload)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, Ingest.Accepted] =
    // VALIDATED, not merely non-empty: the citekey becomes part of a stored file's report below.
    Citekey.parse(p.citekey.value).left.map(invalidCitekey).flatMap { validated =>
      val citekey: Citekey = validated
      val size = upload.size.value
      val limits = cfg().limits
      if size <= 0 then Left(error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.UploadGiven)))
      else if size > limits.uploadBytes.value then
        Left(
          error(
            Problem.Invalid.Broken.here(
              Problem.Invalid.Rule.UploadBytes(Problem.Count.of(upload.size), Problem.Count.of(limits.uploadBytes))
            )
          )
        )
      else
        for
          _ <- acquireItemOk(p.acquireItem)
          sha <- digest(upload)
          _ <- duplicate(sha, citekey, p.force)
          stored <- store(upload, sha, p.filename.fold(upload.name.value)(_.value))
        yield queue.enqueue(
          IngestPending.IngestKind,
          citekey,
          sha,
          stored,
          p.modify(_.citekey)
            .setTo(com.histmersion.api.library.Citekey(citekey.value))
            .modify(_.filename)
            .setTo(Some(Ingest.Params.Filename(stored.name.value)))
        )
    }

  /** The MCP `ingest` tool: a model has no multipart transport, so it supplies EITHER a path the server can see (a
    * bind-mounted corpus) OR a base64 payload bounded by `limits.inlineBytes`. Both end up in the same [[ingest]].
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def fromTool(
      citekey: Citekey,
      path: Option[ToolFiles.Path],
      base64: Option[IngestControllers.Base64],
      filename: Option[Ingest.Params.Filename],
      quality: Ingest.Params.Quality,
      force: Ingest.Params.Force,
      title: Option[Title],
      authors: List[IngestMeta.Authors],
      year: Option[Year],
      doi: Option[Doi],
      acquireItem: Option[AcquireAttemptRow.Id] = None
  )(using Creator.New): Either[Api.Error, Ingest.Accepted] =
    def params(name: Ingest.Params.Filename) =
      Ingest.Params(
        com.histmersion.api.library.Citekey(citekey.value.trim),
        Some(name),
        quality,
        force,
        title.map(t => com.histmersion.api.library.Title(t.value)),
        authors.map(a => AuthorName(a.value)),
        year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
        doi.map(d => com.histmersion.api.library.Doi(d.value)),
        acquireItem = acquireItem.map(i => Ingest.Params.AcquireItem(i.value))
      )
    (path.map(_.value.trim).filter(_.nonEmpty), base64.map(_.value.trim).filter(_.nonEmpty)) match
      case (Some(p), _) =>
        tools.file(ToolFiles.Path(p)).left.map(why => error(why)).flatMap {
          case None =>
            Left(error(Problem.Invalid.Broken(Problem.Invalid.Rule.ServerSees, Vector(Problem.Field(s"'$p'")))))
          case Some(f) => ingest(params(filename.getOrElse(Ingest.Params.Filename(f.name.value))), f)
        }
      case (None, Some(b64)) =>
        scala.util
          .Try(java.util.Base64.getDecoder.decode(b64))
          .toEither
          .left
          .map(e => error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.Base64Decodable(External.Reported.of(e)))))
          .flatMap { raw =>
            val limit = cfg().limits.inlineBytes.value
            if raw.length > limit then
              Left(
                error(
                  Problem.Invalid.Broken.here(
                    Problem.Invalid.Rule
                      .InlineBytes(Problem.Count(raw.length.toLong), Problem.Count.of(cfg().limits.inlineBytes))
                  )
                )
              )
            else
              val name = AssetLinks.safeName(filename.fold("upload.pdf")(_.value))
              ingest(
                params(Ingest.Params.Filename(name.value)),
                Upload.inline(Upload.Name(name.value), Upload.Bytes(raw))
              )
          }
      case (None, None) =>
        Left(error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.PathOrBase64)))

  /** `POST /api/sources/{ck}/reocr` — convert the stored original again (a scan first read on the fast path, re-run
    * under docling with `quality`).
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def reocr(citekey: Citekey, quality: Ingest.Params.Quality)(using Creator.New): Either[Api.Error, Ingest.Accepted] =
    maintenance.guard(WriteGate.OperationName("reocr"))(
      Citekey.parse(citekey.value).left.map(invalidCitekey).flatMap(_ => reocrValid(citekey, quality))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def reocrValid(citekey: Citekey, quality: Ingest.Params.Quality)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, Ingest.Accepted] =
    sources.load(citekey) match
      case None    => Left(Write.notFound(com.histmersion.api.library.Citekey(citekey.value)))
      case Some(_) =>
        sources.original(citekey).flatMap(sha => storedFor(sha).map(sha -> _)) match
          case None =>
            Left(
              error(
                Problem.Invalid.Broken(Problem.Invalid.Rule.OriginalForReocr, Vector(Problem.Field(s"'$citekey'")))
              )
            )
          case Some((sha, file)) =>
            Right(
              queue.enqueue(
                IngestPending.ReocrKind,
                citekey,
                sha,
                file,
                Ingest.Params(
                  com.histmersion.api.library.Citekey(citekey.value),
                  Some(Ingest.Params.Filename(file.name.value)),
                  quality = quality
                )
              )
            )

  /** `GET /api/sources/{ck}/original` — the stored original and its download name. Read-only, so no maintenance guard;
    * 404 when the source or its stored file is missing.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def original(citekey: Citekey): Either[Api.Error, (Blob, Ingest.Disposition)] =
    Citekey.parse(citekey.value).left.map(invalidCitekey).flatMap { _ =>
      sources.load(citekey) match
        case None    => Left(Write.notFound(com.histmersion.api.library.Citekey(citekey.value)))
        case Some(s) =>
          sources.original(citekey).flatMap(storedFor).flatMap(originals().get) match
            case None =>
              Left(
                error(
                  Problem.Invalid
                    .Broken(Problem.Invalid.Rule.OriginalForDownload, Vector(Problem.Field(s"'${s.citekey}'"))),
                  Some(Api.Error.Code(Write.CodeNotFound))
                )
              )
            case Some(file) =>
              Right(file -> Ingest.originalDisposition(s.citekey, Ingest.StoredName(file.key.name.value)))
    }

  // ── the acquisition item ───────────────────────────────────────────────────────────────────

  /** Refuse an upload that names an item it cannot fulfil — BEFORE the file is stored and a long OCR is started for
    * nothing.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def acquireItemOk(item: Option[Ingest.Params.AcquireItem]): Either[Api.Error, Unit] =
    item.fold(Right(()))(id =>
      Queue
        .fulfillable(Queue.QueueItem.Id(id.value), queues.get(QueueItemId(id.value)))
        .map(_ => ())
        .left
        .map(p => error(p))
    )

  // ── the originals' store ───────────────────────────────────────────────────────────────────

  /** `<sha256>.<ext>` — content-addressed, so the same file uploaded twice is stored once and `reocr` can find the
    * original from `sources.sha256` alone.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def store(upload: Upload, sha: Sha256, filename: String): Either[Api.Error, Store.Key] =
    val ext = AssetLinks.safeName(filename).value.reverse.takeWhile(_ != '.').reverse match
      case e if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) => e.toLowerCase
      case _                                                               => "bin"
    val key = Store.Key.applyUnsafe(s"${sha.value}.$ext")
    val store = originals()
    if store.exists(key) then Right(key)
    else
      store
        .reserve(key)
        .flatMap { r =>
          Using.resource(r) { r =>
            scala.util
              .Try(upload.bytes.read(in => Using.resource(r.output())(out => in.transferTo(out))))
              .toEither
              .left
              .map(e =>
                Store.Error.Failed(
                  Problem.Io.File(Problem.Io.Access.Write, Problem.Io.Path(s"$key"), External.Reported.of(e))
                )
              )
              .flatMap(_ => r.commit())
          }
        }
        .left
        .map(e => error(e.message))
        .map(_ => key)

  /** The stored original of content hash `sha`: `<sha>.<ext>`, if it is there. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def storedFor(sha: Sha256): Option[Store.Key] =
    originals().names().find(_.value.startsWith(sha.value + ".")).map(n => Store.Key.applyUnsafe(n.value))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def digest(upload: Upload): Either[Api.Error, Sha256] =
    scala.util
      .Try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        upload.bytes.read { in =>
          val buf = new Array[Byte](64 * 1024)
          @scala.annotation.tailrec
          def loop(): Unit =
            val n = in.read(buf) // streaming: a 200 MB scan never sits in the heap
            if n > 0 then
              md.update(buf, 0, n)
              loop()
          loop()
        }
        Sha256.applyUnsafe(md.digest().map(b => f"${b & 0xff}%02x").mkString)
      }
      .toEither
      .left
      .map(e =>
        error(Problem.Io.File(Problem.Io.Access.Read, Problem.Io.Path(s"${upload.name}"), External.Reported.of(e)))
      )

  /** 409 unless `force`: the same bytes are already stored under another citekey. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def duplicate(sha: Sha256, citekey: Citekey, force: Ingest.Params.Force): Either[Api.Error, Unit] =
    sources.holderOf(sha) match
      case Some(other) if other != citekey && !force.force =>
        Left(
          error(
            Problem.Conflict.Held(
              Problem.Where(s"this file (sha256 ${sha.value.take(12)}…)"),
              Problem.Conflict.Hold.StoredAs(Problem.Raw.of(other))
            ),
            Some(Api.Error.Code(Ingest.CodeConflict))
          )
        )
      case _ => Right(())

object IngestControllers:
  type Base64 = Base64.T
  object Base64 extends Newtype[String, Pure]

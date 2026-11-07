package com.histmersion.domain.library.ingest

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
  def ingest(p: Ingest.Params, upload: Upload): Either[Api.Error, Ingest.Accepted] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("ingest"))(ingestNow(p, upload))

  private def ingestNow(p: Ingest.Params, upload: Upload): Either[Api.Error, Ingest.Accepted] =
    // VALIDATED, not merely non-empty: the citekey becomes part of a stored file's report below.
    Citekey.parse(p.citekey).left.map(Write.invalid).flatMap { validated =>
      val citekey: com.histmersion.domain.library.Citekey = validated.value
      val size = upload.size
      val limits = cfg().limits
      if size <= 0 then Left(Api.Error("the upload is empty"))
      else if size > limits.uploadBytes then
        Left(Api.Error(s"upload is $size bytes, over the ${limits.uploadBytes}-byte limit (limits.upload-bytes)"))
      else
        for
          _ <- acquireItemOk(p.acquireItem)
          sha <- digest(upload)
          _ <- duplicate(sha, citekey, p.force)
          stored <- store(upload, sha, p.filename.getOrElse(upload.name))
        yield queue.enqueue(
          Ingest.KindIngest,
          citekey,
          sha,
          stored,
          p.modify(_.citekey).setTo(citekey).modify(_.filename).setTo(Some(stored.name))
        )
    }

  /** The MCP `ingest` tool: a model has no multipart transport, so it supplies EITHER a path the server can see (a
    * bind-mounted corpus) OR a base64 payload bounded by `limits.inlineBytes`. Both end up in the same [[ingest]].
    */
  def fromTool(
      citekey: com.histmersion.domain.library.Citekey,
      path: Option[com.histmersion.domain.library.ingest.IngestControllers.Path],
      base64: Option[com.histmersion.domain.library.ingest.IngestControllers.Base64],
      filename: Option[com.histmersion.domain.library.ingest.IngestControllers.Filename],
      quality: com.histmersion.domain.library.ingest.IngestControllers.FromToolQuality,
      force: com.histmersion.domain.library.ingest.IngestControllers.FromToolForce,
      title: Option[com.histmersion.domain.Title],
      authors: List[com.histmersion.domain.library.ingest.IngestMeta.Authors],
      year: Option[com.histmersion.Year],
      doi: Option[com.histmersion.domain.library.Doi],
      acquireItem: Option[com.histmersion.domain.library.acquisition.AcquireCheckRow.Item] = None
  ): Either[Api.Error, Ingest.Accepted] =
    def params(name: com.histmersion.domain.library.ingest.IngestControllers.ParamsName) =
      Ingest.Params(citekey.trim, Some(name), quality, force, title, authors, year, doi, acquireItem = acquireItem)
    (path.map(_.trim).filter(_.nonEmpty), base64.map(_.trim).filter(_.nonEmpty)) match
      case (Some(p), _) =>
        tools.file(p).left.map(Api.Error(_)).flatMap {
          case None =>
            Left(
              Api.Error(
                s"the server cannot see a file at '$p' (paths are resolved INSIDE the " +
                  "server, not on your machine) — use `base64`, or `histmersion ingest <file>` from the host"
              )
            )
          case Some(f) => ingest(params(filename.getOrElse(f.name)), f)
        }
      case (None, Some(b64)) =>
        scala.util
          .Try(java.util.Base64.getDecoder.decode(b64))
          .toEither
          .left
          .map(e => Api.Error(s"base64 payload is not decodable: ${e.getMessage}"))
          .flatMap { raw =>
            val limit = cfg().limits.inlineBytes
            if raw.length > limit then
              Left(
                Api.Error(
                  s"payload is ${raw.length} bytes, over the $limit-byte " +
                    "limit (limits.inline-bytes) — pass `path` instead, or upload with `histmersion ingest`"
                )
              )
            else
              val name = AssetLinks.safeName(filename.getOrElse("upload.pdf"))
              ingest(params(name), Upload.inline(name, raw))
          }
      case (None, None) =>
        Left(Api.Error("give either `path` (a file the SERVER can see) or `base64` (the document itself)"))

  /** `POST /api/sources/{ck}/reocr` — convert the stored original again (a scan first read on the fast path, re-run
    * under docling with `quality`).
    */
  def reocr(citekey: com.histmersion.domain.library.Citekey, quality: com.histmersion.domain.library.ingest.IngestControllers.ReocrQuality): Either[Api.Error, Ingest.Accepted] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("reocr"))(
      Citekey.parse(citekey).left.map(Write.invalid).flatMap(_ => reocrValid(citekey, quality))
    )

  private def reocrValid(citekey: com.histmersion.domain.library.Citekey, quality: com.histmersion.domain.library.ingest.IngestControllers.ReocrValidQuality): Either[Api.Error, Ingest.Accepted] =
    sources.load(citekey) match
      case None    => Left(Write.notFound(citekey))
      case Some(_) =>
        sources.original(citekey).flatMap(sha => storedFor(sha).map(sha -> _)) match
          case None =>
            Left(
              Api.Error(
                s"no original stored for '$citekey' — the server keeps uploads in " +
                  s"${cfg().converter.spoolDir}; re-run `histmersion ingest <file> --citekey $citekey --force`"
              )
            )
          case Some((sha, file)) =>
            Right(
              queue.enqueue(
                Ingest.KindReocr,
                citekey,
                sha,
                file,
                Ingest.Params(citekey, Some(file.name), quality = quality)
              )
            )

  /** `GET /api/sources/{ck}/original` — the stored original and its download name. Read-only, so no maintenance guard;
    * 404 when the source or its stored file is missing.
    */
  def original(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, (Blob, com.histmersion.domain.library.ingest.IngestControllers.OriginalResultPart2Part2)] =
    Citekey.parse(citekey).left.map(Write.invalid).flatMap { _ =>
      sources.load(citekey) match
        case None    => Left(Write.notFound(citekey))
        case Some(s) =>
          sources.original(s.citekey).flatMap(storedFor).flatMap(originals().get) match
            case None =>
              Left(
                Api.Error(
                  s"no original stored for '${s.citekey}' — the server keeps uploads in " +
                    s"${cfg().converter.spoolDir}; pass the file yourself (--file)",
                  Some(Write.CodeNotFound)
                )
              )
            case Some(file) => Right(file -> Ingest.originalDisposition(s.citekey, file.key.name))
    }

  // ── the acquisition item ───────────────────────────────────────────────────────────────────

  /** Refuse an upload that names an item it cannot fulfil — BEFORE the file is stored and a long OCR is started for
    * nothing.
    */
  private def acquireItemOk(item: Option[com.histmersion.domain.library.acquisition.AcquireCheckRow.Item]): Either[Api.Error, Unit] =
    item.fold(Right(()))(id => Queue.fulfillable(id, queues.get(QueueItemId(id))).map(_ => ()).left.map(Api.Error(_)))

  // ── the originals' store ───────────────────────────────────────────────────────────────────

  /** `<sha256>.<ext>` — content-addressed, so the same file uploaded twice is stored once and `reocr` can find the
    * original from `sources.sha256` alone.
    */
  private def store(upload: Upload, sha: com.histmersion.Sha256, filename: com.histmersion.domain.library.ingest.IngestControllers.Filename): Either[Api.Error, Store.Key] =
    val ext = AssetLinks.safeName(filename).reverse.takeWhile(_ != '.').reverse match
      case e if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) => e.toLowerCase
      case _                                                               => "bin"
    val key = Store.Key.applyUnsafe(s"$sha.$ext")
    def failed(why: com.histmersion.domain.library.ingest.IngestControllers.Problem) = Api.Error(s"cannot spool the upload into ${cfg().converter.spoolDir}: $why")
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
              .map(e => com.histmersion.util.storage.Store.Error.Failed(e.getMessage))
              .flatMap(_ => r.commit())
          }
        }
        .left
        .map(e => failed(e.message))
        .map(_ => key)

  /** The stored original of content hash `sha`: `<sha>.<ext>`, if it is there. */
  private def storedFor(sha: com.histmersion.Sha256): Option[Store.Key] =
    originals().names().find(_.startsWith(sha + ".")).map(Store.Key.applyUnsafe)

  private def digest(upload: Upload): Either[Api.Error, com.histmersion.Sha256] =
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
        md.digest().map(b => f"${b & 0xff}%02x").mkString
      }
      .toEither
      .left
      .map(e => Api.Error(s"cannot read the upload: ${e.getMessage}"))

  /** 409 unless `force`: the same bytes are already stored under another citekey. */
  private def duplicate(sha: com.histmersion.Sha256, citekey: com.histmersion.domain.library.Citekey, force: com.histmersion.domain.library.ingest.IngestControllers.DuplicateForce): Either[Api.Error, Unit] =
    sources.holderOf(sha) match
      case Some(other) if other != citekey && !force =>
        Left(
          Api.Error(
            s"this file is already stored as '$other' (sha256 ${sha.take(12)}…) — " +
              "pass force to ingest it again under a different citekey",
            Some(Ingest.CodeConflict)
          )
        )
      case _ => Right(())

object IngestControllers:
  type Base64 = Base64.T
  object Base64 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Filename = Filename.T
  object Filename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParamsName = ParamsName.T
  object ParamsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FromToolQuality = FromToolQuality.T
  object FromToolQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromToolForce = FromToolForce.T
  object FromToolForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReocrQuality = ReocrQuality.T
  object ReocrQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReocrValidQuality = ReocrValidQuality.T
  object ReocrValidQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalResultPart2Part2 = OriginalResultPart2Part2.T
  object OriginalResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicateForce = DuplicateForce.T
  object DuplicateForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

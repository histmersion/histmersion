package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{AssetRepo, JobRepo, QueueRepo, SourceRepo}

import java.nio.file.{Files, Path, StandardCopyOption}
import javax.sql.DataSource
import scala.jdk.OptionConverters.*

/** The ONE implementation of the ingest surface (plan §1.8) — REST routes, MCP tools and the CLI all
  * reach these, exactly like [[Handlers]] does for the read/write surface. It lives in its own file
  * rather than inside `Handlers` only so that concurrent streams are not all appending to the same
  * one; `Handlers.ingest` is how everything gets at it.
  *
  * The shape of an ingest:
  *   1. the upload is spooled to `converter.spoolDir/<sha256>.<ext>` — which is what makes `reocr`
  *      possible without a second upload, and what makes a re-run idempotent;
  *   2. a duplicate (same sha256, different citekey) is refused with 409 naming the existing citekey,
  *      unless `force`;
  *   3. a `jobs` row is created and queued; the caller gets its id immediately (202);
  *   4. a worker calls the converter, then writes body + assets: a `patch` when the source already
  *      exists (so agent enrichment survives an OCR re-run) and a skeleton `put` when it does not;
  *   5. when the upload named an acquisition item (`acquireItem`), the worker ticks it off as
  *      `ingested as [[citekey]]` — only after 4 succeeded, so a failed OCR leaves the item open.
  *      When it named none, the worker matches the open items itself (#970, [[AcquireMatch]]): an
  *      item naming the source's DOI is ticked off the same way, one whose text merely resembles the
  *      source is listed in the job's progress as a possible match and left open. A re-OCR matches
  *      nothing: it imports nothing new. The web UI, the CLI and the MCP tool all come through here,
  *      and so do `ingest <doi>`, `archive import` and `calibre import`, so all of them get this;
  *   6. `afterStore` is asked last (`agents.auto-enrich`: queue an `enrich-source` run for the source,
  *      unless it is already enriched); what it answers joins the job's progress line. It never fails
  *      the job and never holds it: queueing a run is a row and a submit.
  */
final class IngestHandlers(deps: Deps, converter: ConverterClient,
    afterStore: String => Option[String] = _ => None):
  private val ds          = deps.ds
  private def cfg         = deps.cfg
  private val maintenance = deps.maintenance

  /** Everything a queued job needs that is not in its row. Concurrent because the HTTP thread writes
    * it and a worker thread reads it; entries die with the process, exactly like the queue. */
  private val pending = java.util.concurrent.ConcurrentHashMap[String, IngestHandlers.Pending]()

  private val pool = Jobs(ds, cfg.converter.parallelism, runJob, deps.pins)

  /** Started by the composition root, after construction. Returns the jobs a restart had to fail. */
  def start(): Int =
    val n = pool.start()
    if n > 0 then scribe.warn(s"$n unfinished job(s) marked failed: ${JobState.Interrupted}")
    n

  def stop(): Unit = pool.stop()

  /** Held by a restore: see [[Jobs.pause]]. */
  def pause(): Unit  = pool.pause()
  def resume(): Unit = pool.resume()
  def paused: Boolean = pool.isPaused

  /** The last converter probe: `(takenAtMillis, answer)`. A `var` would need a comment; this is an
    * `AtomicReference` because the HTTP thread reads it and the probe thread writes it. */
  private val convHealth =
    java.util.concurrent.atomic.AtomicReference[(Long, String)]((0L, "unknown"))

  /** At most ONE probe in flight. Without it, every health request while the sidecar hangs started
    * another thread that would sit there for the full connect timeout. */
  private val probing = java.util.concurrent.atomic.AtomicBoolean(false)

  /** The sidecar's health, BOUNDED and CACHED.
    *
    * `converter.health` opens a connection (10 s connect + 5 s read), so a dead sidecar froze
    * `/api/health` for fifteen seconds — the one request that must always answer. The probe now runs
    * on its own thread with `converter.health-timeout-ms` to answer in; past that the caller is told
    * "degraded" immediately and the probe is left to finish and refresh the cache for next time. */
  def converterHealth: ConverterState =
    ConverterState.of(probeConverter, inConverter.stream().sorted().findFirst().toScala)

  /** Jobs whose document is with the converter RIGHT NOW (#692). The sidecar is a single-worker
    * uvicorn: while it converts, its `/health` route does not answer either, and a failed probe then
    * means "busy", not "down". Concurrent: workers add and remove, the HTTP thread reads. */
  private val inConverter = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  private def probeConverter: String =
    val (at, last) = convHealth.get()
    val timedOut   = s"degraded: no answer from the converter within ${cfg.converter.healthTimeoutMs} ms"
    if System.currentTimeMillis() - at < math.max(0, cfg.converter.healthCacheMs) then last
    else if !probing.compareAndSet(false, true) then
      // a probe from an earlier request is STILL hanging on the dead sidecar; do not start a second
      // one and do not wait for it — that is how a health endpoint turns into a thread pile-up.
      if last == "unknown" then timedOut else last
    else
      val probe = java.util.concurrent.CompletableFuture.supplyAsync { () =>
        try converter.health.fold(identity, identity)
        finally probing.set(false)
      }
      val _ = probe.thenAccept(r => convHealth.set((System.currentTimeMillis(), r)))
      try probe.get(math.max(1, cfg.converter.healthTimeoutMs).toLong,
        java.util.concurrent.TimeUnit.MILLISECONDS)
      catch
        case _: java.util.concurrent.TimeoutException => timedOut
        case e: Throwable                             => s"degraded: ${e.getClass.getSimpleName}"

  // ── ingest ─────────────────────────────────────────────────────────────────────────────────
  /** `POST /api/ingest`. `upload` is the temporary file tapir streamed the body into — it is copied
    * into our own spool BEFORE returning, because the server deletes its temp file as soon as the
    * request completes and the job runs long after that. */
  def ingest(p: IngestParams, upload: java.io.File): Either[ApiError, IngestAccepted] =
    maintenance.guard("ingest")(ingestNow(p, upload))

  private def ingestNow(p: IngestParams, upload: java.io.File): Either[ApiError, IngestAccepted] =
    // VALIDATED, not merely non-empty: the citekey becomes a spool FILE NAME below.
    Citekey.of(p.citekey).flatMap { validated =>
      val citekey = validated.toString
      val size = upload.length()
      if size <= 0 then Left(ApiError("the upload is empty"))
      else if size > cfg.limits.uploadBytes then
        Left(ApiError(s"upload is $size bytes, over the ${cfg.limits.uploadBytes}-byte limit (limits.upload-bytes)"))
      else
        val path = upload.toPath
        for
          _   <- acquireItemOk(p.acquireItem)
          sha <- digest(path)
          _   <- duplicate(sha, citekey, p.force)
          spooled <- spool(path, sha, p.filename.getOrElse(upload.getName))
        yield enqueue(Ingest.KindIngest, citekey, sha, spooled, p.quality,
          p.copy(citekey = citekey, filename = Some(spooled.getFileName.toString)))
    }

  /** The MCP `ingest` tool: a model has no multipart transport, so it supplies EITHER a path the
    * server can see (a bind-mounted corpus, the spool) OR a base64 payload bounded by
    * `limits.inlineBytes`. Both end up in the same [[ingest]]. */
  def fromTool(
      citekey: String,
      path: Option[String],
      base64: Option[String],
      filename: Option[String],
      quality: Boolean,
      force: Boolean,
      title: Option[String],
      authors: List[String],
      year: Option[Int],
      doi: Option[String],
      acquireItem: Option[Int] = None
  ): Either[ApiError, IngestAccepted] =
    def params(name: String) =
      IngestParams(citekey.trim, Some(name), quality, force, title, authors, year, doi, acquireItem = acquireItem)
    (path.map(_.trim).filter(_.nonEmpty), base64.map(_.trim).filter(_.nonEmpty)) match
      case (Some(p), _) =>
        // `path` is a SERVER-side path a MODEL chose. Confined to `corpus.dir` when that is set;
        // when it is not, the old behaviour stands and the operator is told why.
        val root = Option(cfg.corpus.dir).map(_.trim).filter(_.nonEmpty)
        if !SafePath.configured(root) then
          scribe.warn("histmersion.corpus.dir is not configured — the `ingest` tool's `path` is " +
            "unconfined; set it (HISTMERSION_CORPUS_DIR) to bound server-side reads")
        SafePath.confine(root, p).left.map(ApiError(_)).flatMap { f =>
          if !Files.isRegularFile(f) then
            Left(ApiError(s"the server cannot see a file at '$p' (paths are resolved INSIDE the " +
              "server, not on your machine) — use `base64`, or `histmersion ingest <file>` from the host"))
          else ingest(params(filename.getOrElse(f.getFileName.toString)), f.toFile)
        }
      case (None, Some(b64)) =>
        scala.util.Try(java.util.Base64.getDecoder.decode(b64)).toEither.left
          .map(e => ApiError(s"base64 payload is not decodable: ${e.getMessage}"))
          .flatMap { raw =>
            if raw.length > cfg.limits.inlineBytes then
              Left(ApiError(s"payload is ${raw.length} bytes, over the ${cfg.limits.inlineBytes}-byte " +
                "limit (limits.inline-bytes) — pass `path` instead, or upload with `histmersion ingest`"))
            else
              val name = AssetLinks.safeName(filename.getOrElse("upload.pdf"))
              scala.util.Try {
                Files.createDirectories(spoolDir)
                val tmp = Files.createTempFile(spoolDir, "mcp-", "-" + name)
                Files.write(tmp, raw)
                tmp
              }.toEither.left.map(e => ApiError(s"cannot stage the payload: ${e.getMessage}"))
                .flatMap { tmp =>
                  val r = ingest(params(name), tmp.toFile)
                  Files.deleteIfExists(tmp)   // spool() has taken its own content-addressed copy
                  r
                }
          }
      case (None, None) =>
        Left(ApiError("give either `path` (a file the SERVER can see) or `base64` (the document itself)"))

  /** `POST /api/sources/{ck}/reocr` — convert the spooled original again (a scan first read on the
    * fast path, re-run under docling with `quality`). */
  def reocr(citekey: String, quality: Boolean): Either[ApiError, IngestAccepted] =
    maintenance.guard("reocr")(Citekey.of(citekey).flatMap(_ => reocrValid(citekey, quality)))

  private def reocrValid(citekey: String, quality: Boolean): Either[ApiError, IngestAccepted] =
    SourceRepo.load(ds, citekey) match
      case None => Left(Write.notFound(citekey))
      case Some(_) =>
        AssetRepo.sha256Of(ds, citekey).flatMap(sha => spooledFor(sha).map(sha -> _)) match
          case None =>
            Left(ApiError(s"no original stored for '$citekey' — the server keeps uploads in " +
              s"${cfg.converter.spoolDir}; re-run `histmersion ingest <file> --citekey $citekey --force`"))
          case Some((sha, file)) =>
            Right(enqueue(Ingest.KindReocr, citekey, sha, file, quality,
              IngestParams(citekey, Some(file.getFileName.toString), quality = quality)))

  /** `GET /api/sources/{ck}/original` — the spooled original and its download name. Read-only, so no
    * maintenance guard; 404 when the source or its stored file is missing. */
  def original(citekey: String): Either[ApiError, (java.io.File, String)] =
    Citekey.of(citekey).flatMap { _ =>
      SourceRepo.load(ds, citekey) match
        case None => Left(Write.notFound(citekey))
        case Some(s) =>
          AssetRepo.sha256Of(ds, s.citekey).flatMap(spooledFor) match
            case None =>
              Left(ApiError(s"no original stored for '${s.citekey}' — the server keeps uploads in " +
                s"${cfg.converter.spoolDir}; pass the file yourself (--file)", Some(Write.CodeNotFound)))
            case Some(file) =>
              Right(file.toFile -> Ingest.originalDisposition(s.citekey, file.getFileName.toString))
    }

  private def enqueue(
      kind: String, citekey: String, sha: String, file: Path, quality: Boolean, params: IngestParams
  ): IngestAccepted =
    val job = JobRepo.create(ds, kind, Some(citekey), Some(sha), params.acquireItem)
    val _   = pending.put(job.id, IngestHandlers.Pending(params.copy(quality = quality), file, sha, kind))
    pool.submit(job.id)
    IngestAccepted(job.id, citekey, job.status, Some(sha))

  // ── jobs ───────────────────────────────────────────────────────────────────────────────────
  def job(id: String): Either[ApiError, JobInfo] =
    JobRepo.get(ds, id).toRight(ApiError(s"No job '$id'", Some(Write.CodeNotFound)))

  def jobs(status: Option[String], limit: Option[Int]): List[JobInfo] =
    JobRepo.list(ds, status, cfg.limits.page(limit, cfg.limits.search)).toList

  /** `GET /api/jobs` — one page, newest first ([[Paging]]), with the per-state counts of every job. */
  def jobsPage(status: Option[String], limit: Option[Int], before: Option[String] = None,
      upTo: Option[String] = None): Either[ApiError, (List[JobInfo], Paging.Page)] =
    val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
    JobRepo.page(ds, st, cfg.limits.page(limit, cfg.limits.search), clean(before), clean(upTo))
      .left.map(ApiError(_, Some(Write.CodeNotFound)))
      .map((rows, more) => (rows.toList, Paging.Page.of(JobRepo.stateCounts(ds), st, Option.when(more)(rows.last.id))))

  private def clean(cursor: Option[String]): Option[String] = cursor.map(_.trim).filter(_.nonEmpty)

  // ── assets ─────────────────────────────────────────────────────────────────────────────────
  /** Asset URLs embed the citekey, so an OLD one (V027) is resolved first, as every read does. */
  private def canonical(ck: String): String = SourceRepo.canonical(ds, ck).getOrElse(ck)

  def assets(citekey: String): List[AssetInfo] = AssetRepo.list(ds, canonical(citekey)).toList

  def asset(citekey: String, name: String): Either[ApiError, (Array[Byte], String)] =
    AssetRepo.read(ds, canonical(citekey), AssetLinks.safeName(name))
      .map((mime, bytes) => (bytes, mime))
      .toRight(ApiError(s"No asset '$name' for '$citekey'", Some(Write.CodeNotFound)))

  /** Add figures to an existing source (the host-side seed path). */
  def putAssets(citekey: String, in: List[AssetInput]): Either[ApiError, AssetsStored] =
    maintenance.guard("put_assets")(putAssetsNow(citekey, in))

  private def putAssetsNow(citekey: String, in: List[AssetInput]): Either[ApiError, AssetsStored] =
    Citekey.of(citekey).flatMap { _ =>
      if SourceRepo.load(ds, citekey).isEmpty then Left(Write.notFound(citekey))
      else
        decode(in).map { stored =>
          val n = AssetRepo.add(ds, citekey, stored)
          AssetsStored(citekey, n)
        }
    }

  /** Wire assets → storable ones, failing the whole batch on the first unusable payload. */
  def decode(in: List[AssetInput]): Either[ApiError, List[AssetRepo.Stored]] =
    in.foldLeft[Either[ApiError, List[AssetRepo.Stored]]](Right(Nil)) { (acc, a) =>
      acc.flatMap(xs => AssetRepo.Stored.of(a, cfg.limits.assetBytes).left.map(ApiError(_)).map(xs :+ _))
    }

  // ── the job body ───────────────────────────────────────────────────────────────────────────
  private def runJob(id: String): Unit =
    Option(pending.remove(id)) match
      case None =>
        val _ = JobRepo.transition(ds, id, JobState.Queued, JobState.Failed,
          error = Some(s"${JobState.Interrupted}: the upload is no longer held by this process"))
      case Some(job) =>
        JobRepo.transition(ds, id, JobState.Queued, JobState.Running, Some("converting")) match
          case None => () // someone else claimed it, or it was cancelled — leave the row alone
          case Some(_) =>
            val converted =
              inConverter.add(id)
              try converter.convert(job.file, job.params.filename.getOrElse(job.file.getFileName.toString),
                job.params.quality)
              finally inConverter.remove(id): Unit
            val outcome =
              converted
                .flatMap(doc => store(id, job, doc).map(n => (doc, n)))
            outcome match
              case Left(msg) =>
                scribe.warn(s"job $id failed: $msg")
                val _ = JobRepo.transition(ds, id, JobState.Running, JobState.Failed, error = Some(msg))
              case Right((doc, n)) =>
                // the source is written; only now is the item it was acquired for fulfilled. This
                // worker holds the job in `running`, so the transition below cannot be refused
                val ticked = job.params.acquireItem match
                  case Some(item)                              => List(tickOff(item, job.params.citekey))
                  case None if job.kind == Ingest.KindIngest   => matchAcquisitions(job.params.citekey)
                  case None                                    => Nil
                val enrich = afterStoreSafely(job.params.citekey)
                val _ = JobRepo.transition(ds, id, JobState.Running, JobState.Done,
                  progress = Some((s"${doc.pages} page(s), $n asset(s)" :: ticked ++ enrich).mkString(" · ")))

  /** [[afterStore]], whose failure is a note on the job, never a failed job. */
  private def afterStoreSafely(citekey: String): List[String] =
    try afterStore(citekey).toList
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"after storing $citekey: ${e.getClass.getSimpleName}: ${e.getMessage}")
        List(s"enrichment not queued: ${e.getClass.getSimpleName}")

  /** Write the conversion: body first (patch when the source exists, so enrichment survives), then
    * the figures, then the digest of the original. Returns how many assets were kept. */
  private def store(jobId: String, job: IngestHandlers.Pending, doc: ConvertedDoc): Either[String, Int] =
    if maintenance.active then Left("a restore is in progress; this job was not written")
    else storeNow(jobId, job, doc)

  private def storeNow(jobId: String, job: IngestHandlers.Pending, doc: ConvertedDoc): Either[String, Int] =
    val citekey = job.params.citekey
    // NUL bytes occur in OCR output and Postgres `text` rejects them
    val body    = doc.markdown.filterNot(_.toInt == 0)
    val assets  = doc.images.map(i => AssetInput(i.name, i.mime, i.base64, i.width, i.height, i.sha256))
    decode(assets).left.map(_.message).flatMap { stored =>
      JobRepo.progress(ds, jobId, s"storing ${stored.size} asset(s)")
      val written =
        if SourceRepo.load(ds, citekey).isDefined then
          SourceRepo.patch(ds, citekey, SourcePatch(body = Some(body)), cfg.limits.chunkChars,
            cfg.places.pointToleranceKm)
            .map(_ => ()).left.map(_.message)
        else
          Write.applyPut(citekey, skeleton(job.params, body)).map(s =>
            val _ = SourceRepo.put(ds, s, cfg.limits.chunkChars, cfg.places.pointToleranceKm))
      written.map { _ =>
        val kept = AssetRepo.replace(ds, citekey, stored)
        AssetRepo.setSha256(ds, citekey, job.sha256)
        kept
      }
    }

  /** The minimal source an ingest creates when nothing is there yet: enough to find it, no invented
    * enrichment. Everything else is the agent's job, through `patch_source`. */
  private def skeleton(p: IngestParams, body: String): SourcePut =
    SourcePut(
      title = p.title.map(_.trim).filter(_.nonEmpty).getOrElse(p.citekey),
      authors = p.authors.map(_.trim).filter(_.nonEmpty),
      doi = p.doi.map(_.trim).filter(_.nonEmpty),
      year = p.year,
      status = Some(SourceStatus.Unassessed),
      body = Some(body),
      provenance = Some("ocr"))

  // ── the acquisition item ───────────────────────────────────────────────────────────────────

  /** Refuse an upload that names an item it cannot fulfil — BEFORE the file is spooled and a long
    * OCR is started for nothing. */
  private def acquireItemOk(item: Option[Int]): Either[ApiError, Unit] =
    item.fold(Right(()))(id => Queue.fulfillable(id, QueueRepo.get(ds, id)).map(_ => ()).left.map(ApiError(_)))

  /** Tick `id` off as fulfilled by `citekey`, and say what happened for the job's progress line. It
    * never fails the job: the source is stored either way. An item deleted or ticked off by hand
    * while the OCR ran is left as it is. */
  private def tickOff(id: Int, citekey: String): String =
    def warning(message: String): String =
      scribe.warn(s"$citekey is stored, but acquisition item $id could not be ticked off: $message")
      s"acquisition item $id NOT ticked off: $message"
    try
      QueueRepo.get(ds, id).filter(_.status == Queue.Open) match
        case None => s"acquisition item $id was no longer open"
        case Some(item) =>
          QueueRepo.patch(ds, id, Queue.fulfil(item, citekey)) match
            case Right(_) => s"ticked off acquisition item $id"
            case Left(e)  => warning(e.message)
    catch
      // Storage failures are not Left(ApiError). The source is already committed; preserve its
      // successful job and report the bookkeeping failure, without swallowing interruption.
      case scala.util.control.NonFatal(e) => warning(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))

  /** No item was named (#970): close the open acquisition items that name the stored source's DOI,
    * and list the ones that only resemble it ([[AcquireMatch.decide]]). One line per item, for the
    * job's progress. Like [[tickOff]] it never fails the job: the source is stored either way. */
  private def matchAcquisitions(citekey: String): List[String] =
    try
      SourceRepo.load(ds, citekey).toList.flatMap { s =>
        val items = QueueRepo.list(ds, Queue.Kind.Acquire, all = false).toList
        AcquireMatch.decide(items, s.doi, s.title, s.authors, cfg.acquire.defaults.titleMatchAt).map { d =>
          AcquireMatch.describe(d, id =>
            QueueRepo.get(ds, id).filter(_.status == Queue.Open)
              .toRight("it was no longer open")
              .flatMap(item => QueueRepo.patch(ds, id, Queue.fulfil(item, citekey)).left.map(_.message))
              .map(_ => ()))
        }
      }
    catch
      case scala.util.control.NonFatal(e) =>
        val why = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        scribe.warn(s"$citekey is stored, but its acquisition items could not be matched: $why")
        List(s"acquisition items NOT matched: $why")

  // ── the spool ──────────────────────────────────────────────────────────────────────────────
  private def spoolDir: Path = Path.of(cfg.converter.spoolDir)

  /** `<spoolDir>/<sha256>.<ext>` — content-addressed, so the same file uploaded twice is stored once
    * and `reocr` can find the original from `sources.sha256` alone. */
  private def spool(src: Path, sha: String, filename: String): Either[ApiError, Path] =
    val ext  = AssetLinks.safeName(filename).reverse.takeWhile(_ != '.').reverse match
      case e if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) => e.toLowerCase
      case _                                                              => "bin"
    val dest = spoolDir.resolve(s"$sha.$ext")
    scala.util.Try {
      Files.createDirectories(spoolDir)
      if !Files.exists(dest) then Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
      dest
    }.toEither.left.map(e => ApiError(s"cannot spool the upload into $spoolDir: ${e.getMessage}"))

  private def spooledFor(sha: String): Option[Path] = IngestHandlers.spooled(spoolDir, sha)

  private def digest(p: Path): Either[ApiError, String] =
    scala.util.Try {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      val in = Files.newInputStream(p)
      try
        val buf = new Array[Byte](64 * 1024)
        var n   = in.read(buf)                      // streaming: a 200 MB scan never sits in the heap
        while n > 0 do { md.update(buf, 0, n); n = in.read(buf) }
      finally in.close()
      md.digest().map(b => f"${b & 0xff}%02x").mkString
    }.toEither.left.map(e => ApiError(s"cannot read the upload: ${e.getMessage}"))

  /** 409 unless `force`: the same bytes are already stored under another citekey. */
  private def duplicate(sha: String, citekey: String, force: Boolean): Either[ApiError, Unit] =
    AssetRepo.citekeyOfSha256(ds, sha) match
      case Some(other) if other != citekey && !force =>
        Left(ApiError(s"this file is already stored as '$other' (sha256 ${sha.take(12)}…) — " +
          "pass force to ingest it again under a different citekey", Some(Ingest.CodeConflict)))
      case _ => Right(())

/** What `/api/health` says about the OCR sidecar (#692). */
enum ConverterState:
  case Ok
  /** A conversion is in flight and the probe got no answer: the single worker is busy, not down. */
  case Busy(jobId: String)
  case Down(reason: String)

  /** The `converter` line of [[Health]]. */
  def render: String = this match
    case Ok           => "ok"
    case Busy(id)     => s"converter busy: job $id"
    case Down(reason) => reason

  /** Busy is healthy: the server is doing exactly what it is for. */
  def healthy: Boolean = this match
    case Down(_) => false
    case _       => true

object ConverterState:
  /** From the raw probe answer (`"ok"` or why not) and the job the converter is working on, if any. */
  def of(probe: String, busyJob: Option[String]): ConverterState =
    if probe == "ok" then Ok else busyJob.fold(Down(probe))(Busy(_))

object IngestHandlers:
  /** The stored original of content hash `sha`: `<spoolDir>/<sha>.<ext>`, if it is there. */
  def spooled(spoolDir: Path, sha: String): Option[Path] =
    scala.util.Try {
      val s = Files.list(spoolDir)
      try s.filter(p => p.getFileName.toString.startsWith(sha + ".")).findFirst().orElse(null)
      finally s.close()
    }.toOption.flatMap(Option(_))

  /** Every content hash the spool holds an original for (`<sha>.<ext>`, one listing) — what the
    * Calibre gaps listing marks as downloadable. Empty when the spool cannot be read. */
  def spooledHashes(spoolDir: Path): Set[String] =
    import scala.jdk.CollectionConverters.*
    scala.util.Try {
      val s = Files.list(spoolDir)
      try s.iterator.asScala.map(_.getFileName.toString).filter(_.contains('.'))
        .map(n => n.substring(0, n.indexOf('.'))).filter(_.nonEmpty).toSet
      finally s.close()
    }.getOrElse(Set.empty)

  /** The part of a queued job that lives in memory: the spooled original and the metadata the
    * uploader supplied. */
  final case class Pending(params: IngestParams, file: Path, sha256: String, kind: String = Ingest.KindIngest)

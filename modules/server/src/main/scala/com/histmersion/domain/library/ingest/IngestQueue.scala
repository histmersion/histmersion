package com.histmersion
package domain.library.ingest

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.api.jobs.JobInfo

import io.scalaland.chimney.dsl.*

import com.histmersion.config.AppConfig
import com.histmersion.domain.AcquireMatch
import com.histmersion.domain.Caller
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.domain.automation.worklists.QueueScope
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{Job, JobPool, JobRow}
import com.histmersion.domain.automation.agents.Halt.Stopped
import com.histmersion.util.jobs.JobRecords
import com.histmersion.util.jobs.Pinning
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.util.ocr.ConverterState
import com.histmersion.util.storage.Store

import hearth.kindlings.optics.syntax.*

import java.time.Clock
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, TimeoutException, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.sql.DataSource
import scala.jdk.OptionConverters.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.Agents.PauseReason
import com.histmersion.api.library.Ingest
import com.histmersion.api.media.AssetInput
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.library.Source
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.Title
import com.histmersion.domain.AuthorName
import io.github.iltotore.iron.constraint.any.Pure

/** The ingest's job queue (plan §1.8): a job is created and queued, and a worker of the bounded pool calls the
  * converter on the stored original, then writes body + figures — a `patch` when the source already exists (so agent
  * enrichment survives an OCR re-run) and a skeleton `put` when it does not.
  *
  * When the upload named an acquisition item (`acquireItem`), the worker ticks it off as `ingested as [[citekey]]` —
  * only after the write succeeded, so a failed OCR leaves the item open. When it named none, the worker matches the
  * open items itself (#970, [[AcquireMatch]]): an item naming the source's DOI is ticked off the same way, one whose
  * text merely resembles the source is listed in the job's progress as a possible match and left open. A re-OCR matches
  * nothing: it imports nothing new.
  *
  * `afterStore` is asked last (`agents.auto-enrich`: queue an `enrich-source` run for the source, unless it is already
  * enriched); what it answers joins the job's progress line. It never fails the job and never holds it.
  */
final class IngestQueue(
    sources: SourceService,
    figures: AssetService,
    queues: QueueService,
    jobs: JobRecords,
    originals: () => Store,
    converter: ConverterClient,
    cfg: () => AppConfig,
    maintenance: WriteGate,
    clock: Clock,
    ds: () => DataSource,
    pins: Pinning,
    afterStore: Citekey => Option[IngestQueue.Note]
):

  /** Everything a queued job needs that is not in its row. Concurrent because the HTTP thread writes it and a worker
    * thread reads it; entries die with the process, exactly like the queue.
    */
  private val pending = ConcurrentHashMap[Job.Id, IngestPending]()

  private val pool = JobPool(ds(), JobPool.Parallelism(cfg().converter.parallelism.value), runJob, pins)

  /** Started by the composition root, after construction. Returns the jobs a restart had to fail. */
  def start(): RowCount =
    val n = pool.start()
    if n.value > 0 then scribe.warn(s"$n unfinished job(s) marked failed: ${JobState.Interrupted}")
    n

  def stop(): Unit = pool.stop()

  /** Held by a restore: see [[JobPool.pause]]. */
  def pause(): Unit = pool.pause()
  def resume(): Unit = pool.resume()
  def paused: Boolean = pool.isPaused

  /** Queue the conversion of the stored original `file`; the job as the caller is told of it. */
  def enqueue(kind: Job.Kind, citekey: Citekey, sha: Sha256, file: Store.Key, params: Ingest.Params): Ingest.Accepted =
    val job = jobs.create(kind, Some(citekey), Some(sha), params.acquireItem.map(i => JobRow.AcquireItem(i.value)))
    val jobId = Job.Id.assume(job.id.value)
    val _ = pending.put(jobId, IngestPending(params, citekey, file, sha, kind))
    pool.submit(jobId)
    Ingest.Accepted(
      job.id,
      com.histmersion.api.library.Citekey(citekey.value),
      Ingest.Accepted.Status(job.status.value),
      Some(com.histmersion.api.Sha256(sha.value))
    )

  // ── the converter's health ─────────────────────────────────────────────────────────────────

  /** The last converter probe: `(takenAtMillis, answer)`, written by the probe thread and read by the HTTP thread. */
  private val convHealth = AtomicReference[(Long, String)]((0L, "unknown"))

  /** At most ONE probe in flight: without it, every health request while the sidecar hangs started another thread that
    * would sit there for the full connect timeout.
    */
  private val probing = AtomicBoolean(false)

  /** Jobs whose document is with the converter RIGHT NOW (#692): while the single-worker sidecar converts, its
    * `/health` does not answer either, and a failed probe then means "busy", not "down".
    */
  private val inConverter = ConcurrentHashMap.newKeySet[Job.Id]()

  /** The sidecar's health, BOUNDED and CACHED: the probe runs on its own thread with `converter.health-timeout-ms` to
    * answer in; past that the caller is told "degraded" at once and the probe is left to refresh the cache.
    */
  def converterHealth: ConverterState =
    ConverterState.of(ConverterState.Probe(probeConverter), inConverter.stream().sorted().findFirst().toScala)

  private def probeConverter: String =
    val (at, last) = convHealth.get()
    val c = cfg().converter
    val timedOut = s"degraded: no answer from the converter within ${c.healthTimeoutMs.value} ms"
    if clock.millis() - at < math.max(0, c.healthCacheMs.value) then last
    else if !probing.compareAndSet(false, true) then
      // a probe from an earlier request is STILL hanging on the dead sidecar; do not start a second
      // one and do not wait for it — that is how a health endpoint turns into a thread pile-up.
      if last == "unknown" then timedOut else last
    else
      val probe = CompletableFuture.supplyAsync[String] { () =>
        try converter.health.fold(p => p.sentence, h => h.value)
        finally probing.set(false)
      }
      val _ = probe.thenAccept(r => convHealth.set((clock.millis(), r)))
      try probe.get(math.max(1, c.healthTimeoutMs.value).toLong, TimeUnit.MILLISECONDS)
      catch
        case _: TimeoutException => timedOut
        case e: Throwable        => s"degraded: ${e.getClass.getSimpleName}"

  // ── the job body ───────────────────────────────────────────────────────────────────────────

  /** A conversion's figures → storable ones, failing the whole batch on the first unusable payload. */
  private def decode(in: List[AssetInput]): Either[Api.Error, List[NewFigure]] =
    ByteCount
      .either(cfg().limits.assetBytes.value)
      .left
      .map(why => Api.Error(Problem.Unclassified(Legacy.Text(why))))
      .flatMap { max =>
        in.foldLeft[Either[Api.Error, List[NewFigure]]](Right(Nil)) { (acc, a) =>
          acc.flatMap(xs => NewFigure.of(a, max).left.map(p => Api.Error(p)).map(xs :+ _))
        }
      }

  private def runJob(id: Job.Id): Unit =
    Option(pending.remove(id)) match
      case None =>
        val _ = jobs.transition(
          id,
          JobState.Queued,
          JobState.Failed,
          error = Some(
            Stopped.write(
              Stopped.Cause.Paused(
                PauseReason.Interrupted,
                Problem.Unclassified(Legacy.Text("the upload is no longer held by this process"))
              )
            )
          )
        )
      case Some(job) =>
        jobs.transition(id, JobState.Queued, JobState.Running, Some(JobRow.Progress("converting"))) match
          case None    => () // someone else claimed it, or it was cancelled — leave the row alone
          case Some(_) =>
            val converted =
              inConverter.add(id)
              try
                val name = job.file.name
                val store = originals()
                val where = store.where(job.file)
                store
                  .get(job.file)
                  .toRight(s"cannot read the upload at $where: $where")
                  .flatMap(blob =>
                    converter
                      .convert(
                        blob,
                        ConverterClient.Filename(job.params.filename.fold(name.value)(_.value)),
                        ConverterClient.HighQuality(job.params.quality.value)
                      )
                      .left
                      .map(_.sentence)
                  )
              finally inConverter.remove(id): Unit
            val outcome = converted.flatMap(doc => store(id, job, doc).map(n => (doc, n)))
            outcome match
              case Left(msg) =>
                scribe.warn(s"job $id failed: $msg")
                val _ = jobs.transition(
                  id,
                  JobState.Running,
                  JobState.Failed,
                  error = Some(Problem.Unclassified(Legacy.Text(msg)))
                )
              case Right((doc, n)) =>
                // the source is written; only now is the item it was acquired for fulfilled. This
                // worker holds the job in `running`, so the transition below cannot be refused
                val ticked = job.params.acquireItem match
                  case Some(item)                                   => List(tickOff(item, job.citekey, id))
                  case None if job.kind == IngestPending.IngestKind => matchAcquisitions(job.citekey, id)
                  case None                                         => Nil
                val enrich = afterStoreSafely(job.citekey)
                val _ = jobs.transition(
                  id,
                  JobState.Running,
                  JobState.Done,
                  progress =
                    Some(JobRow.Progress((s"${doc.pages} page(s), $n asset(s)" :: ticked ++ enrich).mkString(" · ")))
                )

  /** [[afterStore]], whose failure is a note on the job, never a failed job. */
  private def afterStoreSafely(citekey: Citekey): List[String] =
    try afterStore(citekey).toList.map(_.value)
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"after storing $citekey: ${e.getClass.getSimpleName}: ${e.getMessage}")
        List(s"enrichment not queued: ${e.getClass.getSimpleName}")

  /** Write the conversion: body first (patch when the source exists, so enrichment survives), then the figures, then
    * the digest of the original. Returns how many assets were kept.
    */
  private def store(
      jobId: Job.Id,
      job: IngestPending,
      doc: ConvertedDoc
  ): Either[String, AssetService.ReplacedFigureCount] =
    if maintenance.active then Left("a restore is in progress; this job was not written")
    else storeNow(jobId, job, doc)

  private def storeNow(
      jobId: Job.Id,
      job: IngestPending,
      doc: ConvertedDoc
  ): Either[String, AssetService.ReplacedFigureCount] =
    val citekey = job.citekey
    // NUL bytes occur in OCR output and Postgres `text` rejects them
    val body = Source.OcrMarkdown(doc.markdown.value.filterNot(_.toInt == 0))
    val assets = doc.images.map(i => AssetInput(i.name, i.mime, i.base64, i.width, i.height, i.sha256))
    decode(assets).left.map(_.message.sentence).flatMap { stored =>
      jobs.progress(jobId, JobRow.Progress(s"storing ${stored.size} asset(s)"))
      val written =
        if sources.load(citekey).isDefined then
          sources
            .patch(citekey, Source.Patch(body = Some(body)), Caller.Local)
            .map(_ => ())
            .left
            .map(_.message.sentence)
        else
          Source.Write
            .applyPut(job.params.citekey, skeleton(job.params, body))
            .left
            .map(_.sentence)
            .map(s =>
              val _ = sources.put(s, Caller.Local)
            )
      written.map { _ =>
        val kept = figures.replaceFigures(citekey, stored)
        sources.setOriginal(citekey, job.sha256, Caller.Local)
        kept
      }
    }

  // ── the acquisition item ───────────────────────────────────────────────────────────────────

  /** Tick `id` off as fulfilled by `citekey`, and say what happened for the job's progress line. It never fails the
    * job: the source is stored either way. An item deleted or ticked off by hand while the OCR ran is left as it is.
    */
  private def tickOff(id: Ingest.Params.AcquireItem, citekey: Citekey, jobId: Job.Id): String =
    def warning(message: String): String =
      scribe.warn(s"$citekey is stored, but acquisition item $id could not be ticked off: $message")
      s"acquisition item $id NOT ticked off: $message"
    try
      queues.get(QueueItemId(id.value)).filter(_.status == Queue.QueueItem.Status(Queue.Open)) match
        case None       => s"acquisition item $id was no longer open"
        case Some(item) =>
          queues.patch(
            QueueItemId(id.value),
            Queue.fulfil(
              item,
              com.histmersion.api.library.Citekey(citekey.value),
              Some(Queue.QueueItem.ClosedBy(jobId.value))
            )
          ) match
            case Right(_) => s"ticked off acquisition item $id"
            case Left(e)  => warning(e.message)
    catch
      // Storage failures are not Left(ApiError). The source is already committed; preserve its
      // successful job and report the bookkeeping failure, without swallowing interruption.
      case scala.util.control.NonFatal(e) => warning(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))

  /** No item was named (#970): close the open acquisition items that name the stored source's DOI, and list the ones
    * that only resemble it ([[AcquireMatch.decide]]). One line per item, for the job's progress. Like [[tickOff]] it
    * never fails the job: the source is stored either way.
    */
  private def matchAcquisitions(citekey: Citekey, jobId: Job.Id): List[String] =
    try
      sources.load(citekey).toList.flatMap { s =>
        val items = queues.list(Queue.Kind.Acquire, QueueScope.Open).toList
        AcquireMatch
          .decide(
            items,
            s.doi.flatMap(d => Doi.option(d.value)),
            Title(s.title.value),
            s.authors.map(a => AuthorName(a.value)),
            AcquireMatch.ProposeThreshold(cfg().acquire.defaults.titleMatchAt.value)
          )
          .map { d =>
            AcquireMatch
              .describe(
                d,
                id =>
                  queues
                    .get(QueueItemId(id.value))
                    .filter(_.status == Queue.QueueItem.Status(Queue.Open))
                    .toRight(Problem.Unclassified(Legacy.Text("it was no longer open")))
                    .flatMap(item =>
                      queues
                        .patch(
                          QueueItemId(id.value),
                          Queue.fulfil(
                            item,
                            com.histmersion.api.library.Citekey(citekey.value),
                            Some(Queue.QueueItem.ClosedBy(jobId.value))
                          )
                        )
                        .left
                        .map(e => Problem.Unclassified(Legacy.Text(e.message)))
                    )
                    .map(_ => ())
              )
              .value
          }
      }
    catch
      case scala.util.control.NonFatal(e) =>
        val why = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        scribe.warn(s"$citekey is stored, but its acquisition items could not be matched: $why")
        List(s"acquisition items NOT matched: $why")

  /** The minimal source an ingest creates when nothing is there yet: enough to find it, no invented enrichment.
    * Everything else is the agent's job, through `patch_source`.
    */
  private def skeleton(p: Ingest.Params, body: Source.OcrMarkdown): Source.Put =
    Source.Put(
      title =
        com.histmersion.api.library.Title(p.title.map(_.value.trim).filter(_.nonEmpty).getOrElse(p.citekey.value)),
      authors = p.authors.map(_.value.trim).filter(_.nonEmpty).map(com.histmersion.api.library.AuthorName(_)),
      doi = p.doi.map(_.value.trim).filter(_.nonEmpty).map(com.histmersion.api.library.Doi(_)),
      year = p.year,
      status = Some(Source.Status.Unassessed),
      body = Some(body),
      provenance = Some(Source.Provenance("ocr"))
    )

object IngestQueue:
  /** A line of a job's progress report: what happened to the source after its text was stored. */
  type Note = Note.T
  object Note extends Newtype[String, Pure]

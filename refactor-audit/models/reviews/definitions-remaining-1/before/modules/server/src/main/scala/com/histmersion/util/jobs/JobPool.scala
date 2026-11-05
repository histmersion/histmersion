package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.api.jobs.JobState
import com.histmersion.util.jobs.JobRepo

import javax.sql.DataSource
import java.util.concurrent.LinkedBlockingQueue

/** The bounded OCR worker pool (plan §1.8).
  *
  * OCR is a JOB, not a request: docling on a 500-page scan runs for tens of minutes, far past any sane HTTP timeout.
  * `POST /api/ingest` therefore stores the upload, writes a `jobs` row and returns its id; `converter.parallelism`
  * workers drain an in-memory queue and move the row through `queued → running → done|failed`.
  *
  * Nothing survives the process, and the design leans on that rather than pretending otherwise: [[start]] first marks
  * every unfinished row `failed: interrupted`, so a restart never leaves a job claiming to be in progress. Re-running
  * is idempotent — the same file has the same sha256.
  *
  * Structured concurrency is Ox's: the workers are `forkUser`s inside one `supervised` scope, which lives on a single
  * daemon thread so that binding the HTTP port is not blocked by it.
  */
final class JobPool(
    ds: DataSource,
    parallelism: Int,
    run: String => Unit,
    pins: com.histmersion.util.jobs.Pinning = com.histmersion.util.jobs.Pinning.none
):

  /** A job id and the generation it was submitted on: it runs there — on the database its row is in, even after a
    * config reload swapped the database (docs/research-plugins.md §3.6).
    */
  final private case class Queued(id: String, work: com.histmersion.util.jobs.Carried[Unit])
  private val queue = LinkedBlockingQueue[Queued]()

  /** The jobs this pool holds — queued in memory or running: a reconciliation never fails them (a config reload that
    * swapped back to their database runs one, §3.6).
    */
  private val held = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  /** The thread hosting the Ox scope. A `var` because the pool is started by the composition root AFTER construction
    * (no side effects in initialisers) and stopped by tests.
    */
  private var scope: Option[Thread] = None

  /** The pause gate, held while a RESTORE runs (plan §1.10): a job that writes a source while `pg_restore --clean` is
    * dropping the tables writes into a schema that is being replaced. A `var` guarded by `gate`'s monitor, because a
    * worker must BLOCK on it rather than spin.
    */
  private val gate = Object()
  private var paused = false

  /** Stop handing queued jobs to the workers. A job already running is left to finish. */
  def pause(): Unit = gate.synchronized { paused = true }

  def resume(): Unit = gate.synchronized { paused = false; gate.notifyAll() }

  def isPaused: Boolean = gate.synchronized(paused)

  private def awaitResume(): Unit = gate.synchronized(while paused do gate.wait())

  /** Reconcile interrupted jobs, then start `parallelism` workers. Returns how many rows the restart had to fail.
    * Idempotent: calling it twice does not start a second pool.
    */
  def start(): Int =
    import scala.jdk.CollectionConverters.*
    val interrupted = Transactor(ds).transact(JobRepo.failInterrupted(held.asScala.toList))
    if scope.isEmpty then
      val t = Thread.ofPlatform().daemon(true).name("histmersion-jobs").unstarted(() => pool())
      scope = Some(t)
      t.start()
    interrupted

  /** Hand a job id to the pool. The queue is unbounded on purpose: admission control is the `jobs` table (a duplicate
    * upload is refused before it ever gets here), and dropping an accepted job would strand a row in `queued` forever.
    */
  def submit(jobId: String): Unit =
    val _ = held.add(jobId)
    queue.put(
      Queued(
        jobId,
        pins.carry(try runOne(jobId)
        finally { val _ = held.remove(jobId) })
      )
    )

  /** One job, its unexpected failure recorded — both on the job's own generation. */
  private def runOne(id: String): Unit =
    try run(id)
    catch
      case e: InterruptedException => throw e
      case e: Throwable            =>
        // a worker must outlive one bad job: record the failure and take the next id
        scribe.error(s"job $id failed unexpectedly: ${e.getMessage}", e)
        val _ = Transactor(ds).transact(
          JobRepo.transition(
            id,
            JobState.Running,
            JobState.Failed,
            error = Some(s"${e.getClass.getSimpleName}: ${e.getMessage}")
          )
        )

  /** Stop the pool (tests, shutdown). Interrupting the scope thread cancels every fork. */
  def stop(): Unit =
    // a queued job's row stays queued, and the next start fails it as interrupted; its pin goes now
    Iterator.continually(queue.poll()).takeWhile(_ != null).foreach { q => q.work.drop(); val _ = held.remove(q.id) }
    scope.foreach(_.interrupt())
    scope = None

  def queued: Int = queue.size

  private def pool(): Unit =
    try
      ox.supervised {
        (1 to math.max(1, parallelism)).foreach { _ =>
          val _ = ox.forkUser(worker())
        }
        ()
      }
    catch
      case _: InterruptedException => () // stop() — expected
      case e: Throwable            => scribe.error(s"job pool died: ${e.getMessage}", e)

  private def worker(): Unit =
    var running = true // the loop's exit flag; interruption clears it
    while running do
      try
        val q = queue.take()
        awaitResume() // a restore holds the pool here
        try q.work.run()
        catch
          case e: InterruptedException => throw e
          case e: Throwable            => scribe.error(s"job ${q.id}: recording its failure failed: ${e.getMessage}", e)
      catch case _: InterruptedException => running = false

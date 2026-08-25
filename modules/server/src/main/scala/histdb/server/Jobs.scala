package histdb.server

import histdb.core.JobState
import histdb.db.JobRepo

import javax.sql.DataSource
import java.util.concurrent.LinkedBlockingQueue

/** The bounded OCR worker pool (plan §1.8).
  *
  * OCR is a JOB, not a request: docling on a 500-page scan runs for tens of minutes, far past any
  * sane HTTP timeout. `POST /api/ingest` therefore stores the upload, writes a `jobs` row and
  * returns its id; `converter.parallelism` workers drain an in-memory queue and move the row through
  * `queued → running → done|failed`.
  *
  * Nothing survives the process, and the design leans on that rather than pretending otherwise:
  * [[start]] first marks every unfinished row `failed: interrupted`, so a restart never leaves a job
  * claiming to be in progress. Re-running is idempotent — the same file has the same sha256.
  *
  * Structured concurrency is Ox's: the workers are `forkUser`s inside one `supervised` scope, which
  * lives on a single daemon thread so that binding the HTTP port is not blocked by it. */
final class Jobs(ds: DataSource, parallelism: Int, run: String => Unit):

  private val queue = LinkedBlockingQueue[String]()

  /** The thread hosting the Ox scope. A `var` because the pool is started by the composition root
    * AFTER construction (no side effects in initialisers) and stopped by tests. */
  private var scope: Option[Thread] = None

  /** The pause gate, held while a RESTORE runs (plan §1.10): a job that writes a source while
    * `pg_restore --clean` is dropping the tables writes into a schema that is being replaced. A
    * `var` guarded by `gate`'s monitor, because a worker must BLOCK on it rather than spin. */
  private val gate = Object()
  private var paused = false

  /** Stop handing queued jobs to the workers. A job already running is left to finish. */
  def pause(): Unit = gate.synchronized { paused = true }

  def resume(): Unit = gate.synchronized { paused = false; gate.notifyAll() }

  def isPaused: Boolean = gate.synchronized(paused)

  private def awaitResume(): Unit = gate.synchronized { while paused do gate.wait() }

  /** Reconcile interrupted jobs, then start `parallelism` workers. Returns how many rows the restart
    * had to fail. Idempotent: calling it twice does not start a second pool. */
  def start(): Int =
    val interrupted = JobRepo.failInterrupted(ds)
    if scope.isEmpty then
      val t = Thread.ofPlatform().daemon(true).name("histdb-jobs").unstarted(() => pool())
      scope = Some(t)
      t.start()
    interrupted

  /** Hand a job id to the pool. The queue is unbounded on purpose: admission control is the `jobs`
    * table (a duplicate upload is refused before it ever gets here), and dropping an accepted job
    * would strand a row in `queued` forever. */
  def submit(jobId: String): Unit = queue.put(jobId)

  /** Stop the pool (tests, shutdown). Interrupting the scope thread cancels every fork. */
  def stop(): Unit =
    scope.foreach(_.interrupt())
    scope = None

  def queued: Int = queue.size

  private def pool(): Unit =
    try
      ox.supervised {
        (1 to math.max(1, parallelism)).foreach(_ => { val _ = ox.forkUser(worker()) })
        ()
      }
    catch
      case _: InterruptedException => ()             // stop() — expected
      case e: Throwable            => scribe.error(s"job pool died: ${e.getMessage}", e)

  private def worker(): Unit =
    var running = true                               // the loop's exit flag; interruption clears it
    while running do
      try
        val id = queue.take()
        awaitResume()                                // a restore holds the pool here
        try run(id)
        catch
          case e: InterruptedException => throw e
          case e: Throwable =>
            // a worker must outlive one bad job: record the failure and take the next id
            scribe.error(s"job $id failed unexpectedly: ${e.getMessage}", e)
            val _ = JobRepo.transition(ds, id, JobState.Running, JobState.Failed,
              error = Some(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
      catch case _: InterruptedException => running = false

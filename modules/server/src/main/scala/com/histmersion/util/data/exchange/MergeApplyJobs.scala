package com.histmersion
package util.data.exchange

import com.histmersion.api.backup.Backup
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult, MergeJobState}
import com.histmersion.api.exchange.{Bundle, MergePlan}
import com.histmersion.util.jobs.{Job, MergeApplyRuns, WorkCarrier}

import java.util.concurrent.{CompletableFuture, TimeoutException, TimeUnit}
import com.histmersion.api.Api

/** `merge apply` in the background (docs/research-merge-import.md §7.7, open question 3): the apply takes a `pg_dump`
  * before it writes, which on a large collection outlasts an HTTP request. One apply at a time — a second is refused
  * while one runs (the import's advisory lock would serialise them anyway, but a person should not queue a second merge
  * blind).
  *
  * The job is a `jobs` row (kind `merge-apply`, #888), so the CLI and the web screen can follow it across a restart:
  * what it reported once done, and — when the server stopped while it ran — `interrupted` (the startup reconciliation
  * fails every unfinished job; the apply's transaction committed or rolled back as a whole). Only the wait for the
  * first answer is in memory.
  */
final class MergeApplyJobs(runs: MergeApplyRuns, waitSeconds: => Int, carrier: WorkCarrier = WorkCarrier.none):

  /** Start `run` on its own thread and wait up to `waitSeconds` for it. */
  def start(bundle: Sha256, planHash: MergeRow.PlanHash)(
      run: () => Either[MergeApplyRuns.Failure, MergeApplyResult]
  ): Either[Api.Error, MergeApplyJob] =
    synchronized {
      if runs.running then
        Left(
          Api.Error(
            Problem(
              "a merge apply is still running — follow it with " +
                "`GET /api/merge/apply/{job}` and apply again when it is done"
            ),
            Some(Api.Error.Code(Backup.CodeMaintenance))
          )
        )
      else
        val id = runs.start(bundle, planHash)
        val done = CompletableFuture[Unit]()
        // on the generation (database) the request was pinned to
        val work = carrier.carryWork { () =>
          val outcome = scala.util
            .Try(run())
            .toEither
            .left
            .map(e => MergeApplyRuns.Failure(s"merge apply crashed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
            .flatten
          try runs.finish(id, outcome)
          catch case e: Exception => scribe.error(s"merge apply $id: could not record its end: ${e.getMessage}", e)
          finally { val _ = done.complete(()) }
        }
        val _ = Thread.ofPlatform().daemon(true).name("histmersion-merge-apply").start(() => work())
        Right(id -> done)
    }.map { (id, done) =>
      try { val _ = done.get(math.max(0, waitSeconds).toLong, TimeUnit.SECONDS) }
      catch case _: TimeoutException => ()
      status(id).getOrElse(
        MergeApplyJob(
          MergeApplyJob.Job(id.value),
          MergeJobState.Running,
          Bundle.Name(bundle.value),
          MergePlan.PlanHash(planHash.value),
          MergeApplyJob.ElapsedMs(0L)
        )
      )
    }

  def status(id: Job.Id): Option[MergeApplyJob] = runs.status(id)

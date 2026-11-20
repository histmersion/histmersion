package com.histmersion
package server
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.api.jobs.{JobDetail, JobInfo, JobKinds}
import com.histmersion.api.instance.Version
import com.histmersion.config.{BasemapConfig, SeaFloorSource}
import com.histmersion.domain.HttpPolicy
import com.histmersion.util.basemap.{
  BasemapJobs,
  BasemapService,
  JavaRangeHttp,
  RangeHttp,
  RetryPolicy,
  RetryWait,
  SeaFloorGrid
}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.storage.Store

import java.net.http.HttpClient
import java.time.{Duration, Instant}
import com.histmersion.api.Api
import com.histmersion.util.jobs.Job
import com.histmersion.util.database.WritePermit

/** The base map wired into the server: its store, the configuration read at each use, its fetch run as a background
  * job, the sea floor the settings choose, and the providers' client under the product's outbound-HTTP policy.
  */
object BasemapWiring:
  /** The base map of `deps`, reading its providers over the network. */
  def service(deps: Deps): BasemapService = service(deps, () => http(deps.cfg.basemap)(using deps.clock))

  /** The base map of `deps`; `http` and `freeSpace` are the seams a test substitutes for the network and the disk. */
  def service(deps: Deps, http: () => RangeHttp, freeSpace: Store => ByteCount = _.usableSpace): BasemapService =
    BasemapService(
      () => deps.cfg.basemap,
      deps.storage,
      () => seaFloor(deps.cfg.basemap.bathymetry.source),
      jobs(deps),
      http,
      freeSpace
    )(using deps.clock)

  /** The client of the base map's providers: HTTP/1.1 (one stream per request; the providers are CDNs), following
    * redirects.
    */
  def http(cfg: BasemapConfig)(using clock: java.time.Clock): RangeHttp =
    JavaRangeHttp(
      HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
      JavaRangeHttp.TimeoutSeconds(cfg.timeoutSeconds.value),
      JavaRangeHttp.Retries(cfg.retries.value),
      JavaRangeHttp.UserAgent(
        HttpPolicy.userAgent(HttpPolicy.UserAgent(s"Histmersion/${Version.version} (terrain base map)")).value
      ),
      Retries
    )(using clock)

  /** The grid of `basemap.bathymetry.source`. */
  def seaFloor(source: SeaFloorSource): SeaFloorGrid = source match
    case SeaFloorSource.Etopo1 => SeaFloorGrid.Etopo1
    case SeaFloorSource.Gebco  => SeaFloorGrid.Gebco

  /** [[HttpPolicy]]: 1, 2, 4 … s after a dropped connection; a slow-down answer after its `Retry-After`. */
  val Retries: RetryPolicy = new RetryPolicy:
    def backoffMs(attempt: Int): RetryWait.Ms = RetryWait.Ms(HttpPolicy.backoffMs(HttpPolicy.Attempt(attempt)).value)

    def slowDown(
        code: RangeHttp.StatusCode,
        retryAfter: Option[RetryPolicy.RetryAfterHeader],
        attempt: Int,
        maxRetries: RetryPolicy.MaxRetries,
        maxRetryAfterSeconds: RetryPolicy.RetryAfterCap,
        now: Instant
    ): Option[RetryWait] =
      HttpPolicy.decide(
        HttpPolicy.StatusCode(code.value),
        retryAfter.map(h => HttpPolicy.RetryAfter(h.value)),
        HttpPolicy.Attempt(attempt),
        HttpPolicy.MaxRetries(maxRetries.value),
        HttpPolicy.MaxRetryAfterSeconds(maxRetryAfterSeconds.value),
        now
      ) match
        case HttpPolicy.Step.Wait(ms, _) => Some(RetryWait(RetryWait.Ms(ms.value), code))
        case _                           => None

  /** The fetch as one of the server's background jobs: behind its write guard, pinned to the caller's generation, and
    * stoppable by a person.
    */
  def jobs(deps: Deps): BasemapJobs = new BasemapJobs:
    private val slot = BackgroundJob.Slot()

    def running: Option[BasemapJobs.Description] = slot.current.map(w => BasemapJobs.Description(w.value))

    def guarded[A](op: BasemapJobs.Operation)(body: WritePermit ?=> Either[Api.Error, A]): Either[Api.Error, A] =
      deps.maintenance.guard(WriteGate.OperationName(op.value))(body)

    def busy(what: BasemapJobs.Target, busyCode: Option[Api.Error.Code]): Api.Error =
      BackgroundJob.busy(BackgroundJob.Work(what.value), slot.current, busyCode)

    def start(
        kind: Job.Kind,
        about: BasemapJobs.Description,
        what: BasemapJobs.Target,
        busyCode: Option[Api.Error.Code]
    )(
        body: (BasemapJobs.Target => Unit) => Either[Problem, BasemapJobs.Description]
    )(using WritePermit): Either[Api.Error, JobInfo] =
      BackgroundJob.start(
        deps.ds,
        deps.maintenance.workers,
        kind,
        Some(JobDetail.Purpose(Some(JobKinds.Subject(about.value)))),
        slot,
        BackgroundJob.Work(what.value),
        busyCode,
        deps.pins,
        Some(deps.jobStops)
      )((_, note) =>
        BackgroundJob.Outcome.of(
          body(t => note(JobInfo.Progress(t.value)))
            .map(d => BackgroundJob.Outcome.Summary(d.value))
        )
      )

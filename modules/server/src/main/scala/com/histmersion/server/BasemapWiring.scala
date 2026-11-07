package com.histmersion.server

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.api.jobs.{JobDetail, JobInfo}
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
import com.histmersion.util.storage.Store

import java.net.http.HttpClient
import java.time.{Duration, Instant}
import com.histmersion.api.Api

/** The base map wired into the server: its store, the configuration read at each use, its fetch run as a background
  * job, the sea floor the settings choose, and the providers' client under the product's outbound-HTTP policy.
  */
object BasemapWiring:
  type Attempt = Attempt.T
  object Attempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BusyCode = BusyCode.T
  object BusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobAbout = JobAbout.T
  object JobAbout extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobWhat = JobWhat.T
  object JobWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Operation = Operation.T
  object Operation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBody = StartBody.T
  object StartBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The base map of `deps`, reading its providers over the network. */

  type BackoffMsResult = BackoffMsResult.T
  object BackoffMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownCode = SlowDownCode.T
  object SlowDownCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownRetryAfter = SlowDownRetryAfter.T
  object SlowDownRetryAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownMaxRetries = SlowDownMaxRetries.T
  object SlowDownMaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownMaxRetryAfterSeconds = SlowDownMaxRetryAfterSeconds.T
  object SlowDownMaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def service(deps: Deps): BasemapService = service(deps, () => http(deps.cfg.basemap)(using deps.clock))

  /** The base map of `deps`; `http` and `freeSpace` are the seams a test substitutes for the network and the disk. */
  def service(deps: Deps, http: () => RangeHttp, freeSpace: Store => com.histmersion.ByteCount = _.usableSpace.value): BasemapService =
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
      cfg.timeoutSeconds,
      cfg.retries,
      HttpPolicy.userAgent(s"Histmersion/${Version.version} (terrain base map)"),
      Retries
    )(using clock)

  /** The grid of `basemap.bathymetry.source`. */
  def seaFloor(source: SeaFloorSource): SeaFloorGrid = source match
    case SeaFloorSource.Etopo1 => SeaFloorGrid.Etopo1
    case SeaFloorSource.Gebco  => SeaFloorGrid.Gebco

  /** [[HttpPolicy]]: 1, 2, 4 … s after a dropped connection; a slow-down answer after its `Retry-After`. */
  val Retries: RetryPolicy = new RetryPolicy:
    def backoffMs(attempt: com.histmersion.server.BasemapWiring.Attempt): com.histmersion.server.BasemapWiring.BackoffMsResult = HttpPolicy.backoffMs(attempt)

    def slowDown(
        code: com.histmersion.server.BasemapWiring.SlowDownCode,
        retryAfter: Option[com.histmersion.server.BasemapWiring.SlowDownRetryAfter],
        attempt: com.histmersion.server.BasemapWiring.Attempt,
        maxRetries: com.histmersion.server.BasemapWiring.SlowDownMaxRetries,
        maxRetryAfterSeconds: com.histmersion.server.BasemapWiring.SlowDownMaxRetryAfterSeconds,
        now: Instant
    ): Option[RetryWait] =
      HttpPolicy.decide(code, retryAfter, attempt, maxRetries, maxRetryAfterSeconds, now) match
        case HttpPolicy.Step.Wait(ms, why) => Some(RetryWait(ms, why))
        case _                             => None

  /** The fetch as one of the server's background jobs: behind its write guard, pinned to the caller's generation, and
    * stoppable by a person.
    */
  def jobs(deps: Deps): BasemapJobs = new BasemapJobs:
    private val slot = BackgroundJob.Slot()

    def running: Option[com.histmersion.util.jobs.Job.Id] = slot.current

    def guarded[A](op: com.histmersion.server.BasemapWiring.Operation)(body: => Either[Api.Error, A]): Either[Api.Error, A] = deps.maintenance.guard(op)(body)

    def busy(what: com.histmersion.server.BasemapWiring.JobWhat, busyCode: Option[com.histmersion.server.BasemapWiring.BusyCode]): Api.Error = BackgroundJob.busy(what, slot.current, busyCode)

    def start(kind: com.histmersion.util.jobs.Job.Kind, about: com.histmersion.server.BasemapWiring.JobAbout, what: com.histmersion.server.BasemapWiring.JobWhat, busyCode: Option[com.histmersion.server.BasemapWiring.BusyCode])(
        body: (com.histmersion.server.BasemapWiring.StartBody => Unit) => Either[com.histmersion.server.BasemapWiring.StartBody, com.histmersion.server.BasemapWiring.StartBody]
    ): Either[Api.Error, JobInfo] =
      BackgroundJob.start(
        deps.ds,
        kind,
        Some(JobDetail.Purpose(Some(about))),
        slot,
        what,
        busyCode,
        deps.pins,
        Some(deps.jobStops)
      )((_, note) => BackgroundJob.Outcome.of(body(note)))

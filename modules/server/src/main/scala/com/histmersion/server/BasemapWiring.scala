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

  /** The base map of `deps`, reading its providers over the network. */
  type JobsRunningResult = JobsRunningResult.T
  object JobsRunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsGuardedOp = JobsGuardedOp.T
  object JobsGuardedOp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsBusyWhat = JobsBusyWhat.T
  object JobsBusyWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsBusyBusyCode = JobsBusyBusyCode.T
  object JobsBusyBusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartKind = JobsStartKind.T
  object JobsStartKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartAbout = JobsStartAbout.T
  object JobsStartAbout extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartWhat = JobsStartWhat.T
  object JobsStartWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartBusyCode = JobsStartBusyCode.T
  object JobsStartBusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartBodyArg1Arg1 = JobsStartBodyArg1Arg1.T
  object JobsStartBodyArg1Arg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartBodyResultPart1 = JobsStartBodyResultPart1.T
  object JobsStartBodyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStartBodyResultPart2 = JobsStartBodyResultPart2.T
  object JobsStartBodyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ServiceFreeSpaceResult = ServiceFreeSpaceResult.T
  object ServiceFreeSpaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMsResult = BackoffMsResult.T
  object BackoffMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMsAttempt = BackoffMsAttempt.T
  object BackoffMsAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownCode = SlowDownCode.T
  object SlowDownCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownRetryAfter = SlowDownRetryAfter.T
  object SlowDownRetryAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownAttempt = SlowDownAttempt.T
  object SlowDownAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownMaxRetries = SlowDownMaxRetries.T
  object SlowDownMaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownMaxRetryAfterSeconds = SlowDownMaxRetryAfterSeconds.T
  object SlowDownMaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def service(deps: Deps): BasemapService = service(deps, () => http(deps.cfg.basemap)(using deps.clock))

  /** The base map of `deps`; `http` and `freeSpace` are the seams a test substitutes for the network and the disk. */
  def service(deps: Deps, http: () => RangeHttp, freeSpace: Store => com.histmersion.server.BasemapWiring.ServiceFreeSpaceResult = _.usableSpace.value): BasemapService =
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
    def backoffMs(attempt: com.histmersion.server.BasemapWiring.BackoffMsAttempt): com.histmersion.server.BasemapWiring.BackoffMsResult = HttpPolicy.backoffMs(attempt)

    def slowDown(
        code: com.histmersion.server.BasemapWiring.SlowDownCode,
        retryAfter: Option[com.histmersion.server.BasemapWiring.SlowDownRetryAfter],
        attempt: com.histmersion.server.BasemapWiring.SlowDownAttempt,
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

    def running: Option[com.histmersion.server.BasemapWiring.JobsRunningResult] = slot.current

    def guarded[A](op: com.histmersion.server.BasemapWiring.JobsGuardedOp)(body: => Either[Api.Error, A]): Either[Api.Error, A] = deps.maintenance.guard(op)(body)

    def busy(what: com.histmersion.server.BasemapWiring.JobsBusyWhat, busyCode: Option[com.histmersion.server.BasemapWiring.JobsBusyBusyCode]): Api.Error = BackgroundJob.busy(what, slot.current, busyCode)

    def start(kind: com.histmersion.server.BasemapWiring.JobsStartKind, about: com.histmersion.server.BasemapWiring.JobsStartAbout, what: com.histmersion.server.BasemapWiring.JobsStartWhat, busyCode: Option[com.histmersion.server.BasemapWiring.JobsStartBusyCode])(
        body: (com.histmersion.server.BasemapWiring.JobsStartBodyArg1Arg1 => Unit) => Either[com.histmersion.server.BasemapWiring.JobsStartBodyResultPart1, com.histmersion.server.BasemapWiring.JobsStartBodyResultPart2]
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

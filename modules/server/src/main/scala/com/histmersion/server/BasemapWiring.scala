package com.histmersion.server

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.api.{ApiError, JobDetail, JobInfo, Version}
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

/** The base map wired into the server: its store, the configuration read at each use, its fetch run as a background
  * job, the sea floor the settings choose, and the providers' client under the product's outbound-HTTP policy.
  */
object BasemapWiring:

  /** The base map of `deps`, reading its providers over the network. */
  def service(deps: Deps): BasemapService = service(deps, () => http(deps.cfg.basemap))

  /** The base map of `deps`; `http` and `freeSpace` are the seams a test substitutes for the network and the disk. */
  def service(deps: Deps, http: () => RangeHttp, freeSpace: Store => Long = _.usableSpace.value): BasemapService =
    BasemapService(
      () => deps.cfg.basemap,
      deps.storage,
      () => seaFloor(deps.cfg.basemap.bathymetry.source),
      jobs(deps),
      http,
      freeSpace
    )

  /** The client of the base map's providers: HTTP/1.1 (one stream per request; the providers are CDNs), following
    * redirects.
    */
  def http(cfg: BasemapConfig): RangeHttp =
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
    )

  /** The grid of `basemap.bathymetry.source`. */
  def seaFloor(source: SeaFloorSource): SeaFloorGrid = source match
    case SeaFloorSource.Etopo1 => SeaFloorGrid.Etopo1
    case SeaFloorSource.Gebco  => SeaFloorGrid.Gebco

  /** [[HttpPolicy]]: 1, 2, 4 … s after a dropped connection; a slow-down answer after its `Retry-After`. */
  val Retries: RetryPolicy = new RetryPolicy:
    def backoffMs(attempt: Int): Long = HttpPolicy.backoffMs(attempt)

    def slowDown(
        code: Int,
        retryAfter: Option[String],
        attempt: Int,
        maxRetries: Int,
        maxRetryAfterSeconds: Int,
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

    def running: Option[String] = slot.current

    def guarded[A](op: String)(body: => Either[ApiError, A]): Either[ApiError, A] = deps.maintenance.guard(op)(body)

    def busy(what: String, busyCode: Option[String]): ApiError = BackgroundJob.busy(what, slot.current, busyCode)

    def start(kind: String, about: String, what: String, busyCode: Option[String])(
        body: (String => Unit) => Either[String, String]
    ): Either[ApiError, JobInfo] =
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

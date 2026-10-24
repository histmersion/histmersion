package com.histmersion.web

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.concurrent.Promise
import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import sttp.client4.fetch.FetchBackend
import sttp.model.Method
import sttp.tapir.{DecodeResult, PublicEndpoint}
import sttp.tapir.client.sttp4.SttpClientInterpreter

import com.histmersion.core.*

/** A request the server answered with an error status: the status and the server's own message.
  * `unauthorized` is a write refused by the API-key gate; `conflict` a stale `ifUpdatedAt`. */
final case class ApiFailure(status: Int, error: ApiError) extends Exception(error.message):
  def unauthorized: Boolean = status == 401 || status == 403
  def conflict: Boolean     = status == 412
  def duplicate: Boolean    = status == 409

object ApiFailure:
  /** Pure: what a failed response means, from its status and whatever body could be decoded. */
  def of(status: Int, decoded: Option[ApiError]): ApiFailure =
    ApiFailure(status, decoded.getOrElse(ApiError(status match
      case 401 | 403 => "the server refused the change: an API key is required"
      case 0         => "the server could not be reached"
      case s         => s"unexpected response from the server (HTTP $s)")))

object ApiClient:

  private val backend = FetchBackend()
  private val interp  = SttpClientInterpreter()

  /** Pure: does a request of this method carry the API key? Only writes — the gate lets reads by. */
  private[web] def isWrite(m: Method): Boolean = m != Method.GET && m != Method.HEAD && m != Method.OPTIONS

  /** THE one place a write is given its `Authorization` header ([[Auth.currentHeader]]). */
  private def authorize[T](req: Request[T]): Request[T] =
    if isWrite(req.method) then Auth.currentHeader.fold(req)((k, v) => req.header(k, v)) else req

  /** A refused write drops the key and asks for it again, wherever it came from. */
  private def refuse(f: ApiFailure): ApiFailure =
    if f.unauthorized then Auth.rejected()
    f

  private def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Future[O] =
    val req = authorize(interp.toRequest(ep, None).apply(in))
    req.send(backend).flatMap { resp =>
      val code = resp.code.code
      resp.body match
        case _ if code == 401 || code == 403  => Future.failed(refuse(ApiFailure.of(code, None)))
        case DecodeResult.Value(Right(o))     => Future.successful(o)
        case DecodeResult.Value(Left(e))      => Future.failed(refuse(ApiFailure(code, e)))
        case other                            =>
          Future.failed(ApiFailure(code, ApiError(s"could not read the server's answer (HTTP $code): $other")))
    }

  def config(): Future[UiConfig]                                       = call(Api.config)(())
  def sources(f: Filter): Future[SearchResult]                         = call(Api.sources)(f)
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]]             = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram]          = call(Api.aggregate)(q)
  def facets(field: String, prefix: Option[String], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def resolveId(id: String): Future[ResolvedId]                         = call(Api.resolveId)(id)
  def getSource(ck: String, body: Boolean = true): Future[SourceWithFindings] =
    call(Api.getSource)((ck, body))
  def neighbors(ck: String, kinds: List[String] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def articles(q: Api.ArticleQuery): Future[ArticleList]                = call(Api.articles)(q)
  def getArticle(slug: String, brief: Boolean = false): Future[ArticleView] = call(Api.getArticle)((slug, brief))
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): Future[List[PlaceHit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: String, asOf: Option[Long], counts: Boolean = false): Future[PlaceDetail] =
    call(Api.place)((id, asOf, Option.when(counts)(true)))
  def about(): Future[About]                                            = call(Api.about)(())
  def basemap(): Future[BasemapStatus]                                  = call(BasemapApi.status)(())
  def uiCounters(): Future[UiNotices.Counters]                          = call(UiApi.counters)(())
  def markSeen(board: String, at: String): Future[UiNotices.Counters]  =
    call(UiApi.markSeen)((board, UiNotices.SeenMark(Some(at))))

  def queueList(list: String, all: Boolean = false): Future[Queue.QueueListing] =
    call(OpsApi.queueList)((list, all))
  def queuePatch(id: Int, p: Queue.QueuePatch): Future[Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueAdd(list: String, in: Queue.QueueAdd): Future[Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queueDelete(id: Int): Future[Queue.QueueItem] =
    call(OpsApi.queueDelete)(id)

  def graph(citekey: String, depth: Option[Int] = None): Future[SourceGraph] =
    call(Api.graph)((citekey, depth))

  def periods(q: Option[String] = None, region: Option[String] = None,
      from: Option[Long] = None, to: Option[Long] = None): Future[List[HistoricalPeriod]] =
    call(Api.periods)((q, region, from, to))

  /** The places' GeoJSON — megabytes over a whole range, read as text by the browser ([[text]]). */
  def placesGeo(q: Api.PlacesGeoQuery): Future[String] = text(Api.placesGeo)(q)

  def mediaGeo(q: Api.MediaGeoQuery): Future[List[MediaGeoHit]] = call(Api.mediaGeo)(q)
  def mediaFacets(): Future[MediaFacets] = call(Api.mediaFacets)(())
  def getEvent(id: String): Future[EventDetail] = call(Api.getEvent)(id)

  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)
  def eventsGeo(q: Api.EventQuery): Future[EventGeoList] = call(Api.eventsGeo)(q)
  /** The literature search for `q`, works not in the corpus yet (an event card's lead, #1449). */
  def discover(q: String, limit: Int): Future[DiscoverResult] = call(AcquireApi.discover)((q, None, None, Some(limit), true))

  /** One page of the jobs that match `f` ([[Paging]]): `before` = older than that job, `upTo` = down to it. */
  def jobs(f: ListFilters.JobFilter, limit: Option[Int], before: Option[String] = None,
      upTo: Option[String] = None): Future[(List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit, before, upTo))

  def patchSource(ck: String, p: SourcePatch): Future[SourceWithFindings] = call(Api.patchSource)((ck, p, false))

  // ── embedded agents ──────────────────────────────────────────────────────────────────────────────

  def agentRuns(f: ListFilters.RunFilter, limit: Option[Int], before: Option[String] = None,
      upTo: Option[String] = None): Future[(List[Agents.RunInfo], Paging.Page)] =
    call(AgentsApi.runs)((f, limit, before, upTo))
  def startRun(req: Agents.RunRequest): Future[Agents.RunInfo] = call(AgentsApi.startRun)(req)
  def agentRun(id: String): Future[Agents.RunDetail] = call(AgentsApi.run)(id)
  /** pause | resume | cancel; a resume may raise the run's cost cap (the way out of a budget pause). */
  def agentControl(id: String, action: String, budgetUsd: Option[Double] = None): Future[Agents.RunInfo] =
    call(AgentsApi.control)((id, action, budgetUsd, None))
  def agentModels: Future[Agents.ModelsInfo] = call(AgentsApi.models)(())
  def setChain(role: String, models: List[String]): Future[ModelChains.RoleChain] =
    call(AgentsApi.setChain)((role, ModelChains.ChainSet(models)))
  def resetChain(role: String): Future[ModelChains.RoleChain] = call(AgentsApi.resetChain)(role)
  def setAllowance(in: ModelChains.AllowanceSet): Future[ModelChains.AllowanceStatus] = call(AgentsApi.setAllowance)(in)
  def removeAllowance(target: String): Future[GoalPlans.Removed] = call(AgentsApi.removeAllowance)(target)
  def agentDoctor(offline: Boolean): Future[Agents.DoctorReport] = call(AgentsApi.doctor)(Some(offline))
  def agentQuota: Future[PlanQuota.QuotaInfo] = call(AgentsApi.quota)(())
  def setPool(pool: String, in: PlanQuota.PoolSet): Future[PlanQuota.PoolStatus] = call(AgentsApi.setPool)((pool, in))
  def refreshQuota(pool: String): Future[PlanQuota.QuotaRefreshed] = call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(Some(pool)))
  def approvals(f: ListFilters.ApprovalFilter, limit: Option[Int], before: Option[String] = None,
      upTo: Option[String] = None): Future[(List[Agents.ApprovalInfo], Paging.Page)] =
    call(AgentsApi.approvals)((f, limit, before, upTo))
  def decide(id: Long, d: Agents.Decision, note: Option[String]): Future[Agents.ApprovalInfo] =
    call(AgentsApi.decide)((id, d.wire, Agents.DecisionRequest(note)))

  // ── goals: the list, one goal's view, its links and schedules ───────────────────────────────────

  def goals(limit: Option[Int]): Future[GoalList] = call(Api.goals)(Api.GoalQuery(None, limit, None))
  def goalView(slug: String): Future[GoalPlans.GoalView] = call(AgentsApi.goalView)(slug)
  def getGoal(slug: String): Future[Goal] = call(Api.getGoal)(slug)
  /** Create (or REPLACE) a goal — the New research area form checks the slug is free first. */
  def putGoal(slug: String, in: GoalPut): Future[Goal] = call(Api.putGoal)((slug, in))
  def addGoalLink(slug: String, in: GoalPlans.LinkAdd): Future[GoalPlans.GoalLink] = call(AgentsApi.addGoalLink)((slug, in))
  def removeGoalLink(slug: String, kind: GoalPlans.LinkKind, ref: String): Future[GoalPlans.Removed] =
    call(AgentsApi.removeGoalLink)((slug, kind.wire, ref))
  def setSchedule(slug: String, kind: GoalPlans.ScheduleKind, in: GoalPlans.ScheduleSet): Future[GoalPlans.GoalSchedule] =
    call(AgentsApi.setSchedule)((slug, kind.wire, in))
  def removeSchedule(slug: String, kind: GoalPlans.ScheduleKind): Future[GoalPlans.Removed] =
    call(AgentsApi.removeSchedule)((slug, kind.wire))

  /** `POST /api/ingest`, with upload progress (`fetch` reports none, so this one request goes
    * through `XMLHttpRequest`). The URL and its query parameters still come from the tapir endpoint,
    * and the key from [[authorize]] — the same two places as every other call. */
  def ingest(params: IngestParams, file: dom.File, progress: Double => Unit): Future[IngestAccepted] =
    val req    = authorize(interp.toRequest(Api.ingest, None).apply((params, file)))
    val result = Promise[IngestAccepted]()
    val xhr    = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers.filter(h => h.name.equalsIgnoreCase("Authorization")).foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 202 || xhr.status == 200 then
        io.circe.parser.decode[IngestAccepted](body).fold(
          e => result.failure(ApiFailure(xhr.status, ApiError(s"could not read the server's answer: ${e.getMessage}"))),
          ok => result.success(ok))
      else
        val decoded = io.circe.parser.decode[ApiError](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  // ── merging another collection's bundle (the /merge review screen) ──────────────────────────────

  def mergePlan(req: MergePlanRequest): Future[MergePlan]                     = call(Api.mergePlan)(req)
  def mergeSession(bundle: String): Future[MergeSession]                      = call(Api.mergeSession)(bundle)
  def putMergeSession(bundle: String, r: MergeResolutions): Future[MergeSession] = call(Api.putMergeSession)((bundle, r))
  def parseResolutions(yaml: String): Future[MergeResolutions]                = call(Api.parseResolutions)(yaml)
  def mergeApply(req: MergeApplyRequest): Future[MergeApplyJob]               = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: String): Future[MergeApplyJob]                    = call(Api.mergeApplyStatus)(job)

  /** `POST /api/bundle/upload`, with upload progress — a bundle can be large; through
    * `XMLHttpRequest` for the same reason as [[ingest]]. */
  def uploadBundle(file: dom.File, progress: Double => Unit): Future[BundleUpload] =
    val req    = authorize(interp.toRequest(Api.uploadBundle, None).apply(file))
    val result = Promise[BundleUpload]()
    val xhr    = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers.filter(h => h.name.equalsIgnoreCase("Authorization")).foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 200 then
        io.circe.parser.decode[BundleUpload](body).fold(
          e => result.failure(ApiFailure(xhr.status, ApiError(s"could not read the server's answer: ${e.getMessage}"))),
          ok => result.success(ok))
      else
        val decoded = io.circe.parser.decode[ApiError](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  // ── process managers (docs/research-process-managers.md) ─────────────────────────────────────────

  def managers: Future[List[Managers.ManagerInfo]] = call(ManagersApi.list)(())
  def autopilot: Future[Autopilot.AutopilotStatus] = call(ManagersApi.autopilot)(())
  def setAutopilot(on: Boolean): Future[Autopilot.AutopilotStatus] =
    call(ManagersApi.setAutopilot)(Autopilot.AutopilotSet(on, Some("web")))
  /** The autopilot's focus: pin/unpin/priority a goal, or the cap (`max`; no value: the config's). */
  def setFocus(action: String, goal: Option[String] = None, value: Option[Int] = None): Future[Autopilot.FocusInfo] =
    call(ManagersApi.setFocus)(Autopilot.FocusSet(action, goal, value, Some("web")))
  /** The central scheduler: its status and queue (`limit` waiting items), a live strategy switch, one item's why. */
  def scheduler(limit: Option[Int] = None): Future[AgentScheduler.SchedulerStatus] = call(ManagersApi.scheduler)(limit)
  def setScheduler(strategy: String): Future[AgentScheduler.SchedulerStatus] =
    call(ManagersApi.setScheduler)(AgentScheduler.SchedulerSet(strategy, Some("web")))
  def schedulerWhy(item: String): Future[AgentScheduler.WhyInfo] = call(ManagersApi.schedulerWhy)(item)
  /** Everything waiting for a person, by kind. */
  def approvalDigest: Future[ApprovalDigest.Digest] = call(ManagersApi.digest)(())
  def manager(id: String): Future[Managers.ManagerDetail] = call(ManagersApi.get)(id)
  def managerUpdate(id: String, in: Managers.ManagerUpdate): Future[Managers.ManagerInfo] = call(ManagersApi.update)((id, in))
  /** pause | resume | stop; `runs` = a pause takes its runs in flight with it. */
  def managerAction(id: String, action: String, runs: Boolean = false): Future[Managers.ManagerInfo] =
    call(ManagersApi.action)((id, action, Option.when(runs)(true)))
  def managerItems(id: String, status: Option[String], offset: Int, limit: Int): Future[Managers.ItemPage] =
    call(ManagersApi.items)((id, status, Some(offset), Some(limit)))
  def managerPatchItem(id: String, item: String, p: Managers.ItemPatch): Future[Managers.ManagerItem] =
    call(ManagersApi.patchItem)((id, item, p))
  def managerAddHint(id: String, in: Managers.HintAdd): Future[Managers.ManagerHint] = call(ManagersApi.addHint)((id, in))
  def managerRemoveHint(id: String, hint: Long): Future[GoalPlans.Removed] = call(ManagersApi.removeHint)((id, hint))
  def managerEvents(id: String, before: Option[Long], limit: Int): Future[Managers.EventPage] =
    call(ManagersApi.events)((id, before, Some(limit)))
  /** A page button — Review, Extend, Enrich (an article or a goal), Extend a goal (`seed`): the target
    * goes to the manager of its kind, first in line. */
  def enqueue(kind: Managers.EnqueueKind, target: String): Future[Managers.EnqueueResult] =
    call(ManagersApi.enqueue)(Managers.EnqueueRequest(kind.wire, target, Some(10)))
  /** The article window's Review button: the article goes to the review manager, first in line. */
  def enqueueReview(slug: String): Future[Managers.EnqueueResult] = enqueue(Managers.EnqueueKind.Review, slug)
  /** A new manager (the New research area form's seeding manager); created paused. */
  def managerCreate(in: Managers.ManagerCreate): Future[Managers.ManagerInfo] = call(ManagersApi.create)(in)

  /** A page's HTML fragment, whatever the status (an error page is shown as it is). */
  def fetchHtml(url: String): Future[String] = fetchText(url).map(_._2)

  /** A text answer of `ep` read by the BROWSER (#1949): sttp's fetch backend reads a body as bytes and
    * decodes them in Scala.js, a byte at a time — tens of ms per MB, on the page's one thread, for a
    * whole range's 7 MB of boundaries. `Response.text()` decodes natively. The request is still the
    * endpoint's own (its path and query, from the same Tapir definition); a GET, so no key. */
  private def text[I](ep: PublicEndpoint[I, ApiError, String, Any])(in: I): Future[String] =
    fetchText(interp.toRequest(ep, None).apply(in).uri.toString).flatMap { (status, body) =>
      if status >= 200 && status < 300 then Future.successful(body)
      else Future.failed(ApiFailure.of(status, decodeError(body)))
    }

  private def decodeError(body: String): Option[ApiError] =
    io.circe.parser.decode[ApiError](body).toOption

  /** GET `url`: the status and the body, decoded by the browser. */
  private def fetchText(url: String): Future[(Int, String)] =
    import scala.scalajs.js.Thenable.Implicits.*
    for
      r    <- dom.fetch(url).toFuture
      body <- r.text().toFuture
    yield (r.status, body)

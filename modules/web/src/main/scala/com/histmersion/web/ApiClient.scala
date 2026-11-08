package com.histmersion
package web

import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*

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

import com.histmersion.api.{personal, Api, OpsApi, Paging, Sha256}
import com.histmersion.api.automation.{
  Activity,
  AgentMode,
  Agents,
  AgentsApi,
  AgentScheduler,
  ApprovalDigest,
  Autopilot,
  Goal,
  ListFilters,
  Managers,
  ManagersApi,
  ModelChains,
  PlanQuota,
  Queue,
  Stalls
}
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.{
  CatalogProviderInfo,
  EventImportRequest,
  EventList,
  EventsApi,
  HistoricalPeriod,
  TilesApi
}
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyRequest, MergePlan, MergeResolutions, MergeSession}
import com.histmersion.api.instance.{About, UiApi, UiConfig, UiNotices}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.library.{
  AcquireApi,
  DiscoverResult,
  Facet,
  Filter,
  GeoHit,
  NeighborRow,
  SearchResult,
  TimeHistogram
}
import com.histmersion.api.media.{MediaFacets, MediaGeoHit, MediaImportRequest}
import com.histmersion.api.research.Backlink
import com.histmersion.api.settings.{ConfigEdit, ConfigEditApi}
import com.histmersion.api.views.{AutopilotApi, AutopilotStatus, EventDetail, PlaceDetail, QueueListing, ResolvedId}
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Place
import com.histmersion.api.exchange.Bundle as ExchangeBundle
import com.histmersion.api.library.Acquire
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Edge
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.personal.Bookmark
import com.histmersion.api.personal.PersonalApi
import com.histmersion.api.personal.PersonalIds
import com.histmersion.api.personal.Note
import com.histmersion.api.personal.ReadMark
import com.histmersion.api.personal.ReadOpened
import com.histmersion.api.ItemKind
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.media.MediaItem
import com.histmersion.api.library.AcquisitionWorkApi
import com.histmersion.api.instance.CorpusCounts
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Event
import com.histmersion.api.Jobs
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.ItemStatus

object ApiClient:
  private given CanEqual[Method, Method] = CanEqual.derived

  type Url = Url.T
  object Url extends Newtype[String, Pure]

  private val backend = FetchBackend()
  private val interp = SttpClientInterpreter()

  /** Pure: does a request of this method carry the API key? Only writes — the gate lets reads by. */
  private[web] def isWrite(m: Method): Boolean = m != Method.GET && m != Method.HEAD && m != Method.OPTIONS

  /** THE one place a write is given its `Authorization` header ([[Auth.currentHeader]]). */
  private def authorize[T](req: Request[T]): Request[T] =
    if isWrite(req.method) then Auth.currentHeader.fold(req)((k, v) => req.header(k, v)) else req

  /** A refused write drops the key and asks for it again, wherever it came from. */
  private def refuse(f: ApiFailure): ApiFailure =
    if f.unauthorized then Auth.rejected()
    f

  private def call[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Future[O] =
    val req = authorize(interp.toRequest(ep, None).apply(in))
    req.send(backend).flatMap { resp =>
      val code = resp.code.code
      resp.body match
        case _ if code == 401 || code == 403 => Future.failed(refuse(ApiFailure.of(ApiFailure.Status(code), None)))
        case DecodeResult.Value(Right(o))    => Future.successful(o)
        case DecodeResult.Value(Left(e))     => Future.failed(refuse(ApiFailure(ApiFailure.Status(code), e)))
        case other                           =>
          Future.failed(
            ApiFailure(
              ApiFailure.Status(code),
              Api.Error(Problem(s"could not read the server's answer (HTTP $code): $other"))
            )
          )
    }

  /** The URL template of a layer's tiles (`{z}`, `{x}`, `{y}` for the map to fill in): `q`'s request as the tile
    * endpoint builds it, the tile given as placeholder numbers no tile has, swapped for the template's markers.
    * Absolute: the map's workers fetch it.
    */
  def tileTemplate(q: TilesApi.TileQuery): Url =
    val req = interp
      .toRequest(TilesApi.tile, None)
      .apply(
        q.modify(_.z).setTo(TilePlaceholder.Z).modify(_.x).setTo(TilePlaceholder.X).modify(_.y).setTo(TilePlaceholder.Y)
      )
    Url(dom.window.location.origin + TilePlaceholder.fill(req.uri.toString))

  def config(): Future[UiConfig] = call(Api.config)(())
  def sources(f: Filter): Future[SearchResult] = call(Api.sources)(f)
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]] = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram] = call(Api.aggregate)(q)
  def facets(field: Api.Facets.Field, prefix: Option[Api.Facets.Prefix], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def resolveId(id: Api.Lookup.Text): Future[ResolvedId] = call(Api.resolveId)(id)
  def getSource(
      ck: Citekey,
      body: Api.SourceViewQuery.FullText = Api.SourceViewQuery.FullText(true)
  ): Future[Source.WithFindings] =
    call(Api.getSource)((ck, body))

  /** The articles that cite a source (`GET /api/sources/{citekey}/articles`). */
  def sourceArticles(ck: Citekey): Future[List[Backlink]] = call(Api.sourceArticles)(ck)

  /** `POST /api/acquire/ingest`: a DOI or arXiv id brought in ([[library.DoiIngest]] builds the request) — by the legal
    * open-access chain only, whatever the request says: the server may be configured to allow fetchers without the
    * rights holder's authorisation, and the web UI never sets them off.
    */
  def ingestId(req: Ingest.Request): Future[Ingest.Report] =
    call(AcquireApi.ingest)(req.modify(_.legalOnly).setTo(Ingest.Request.LegalOnly(true)))
  def neighbors(ck: Citekey, kinds: List[Edge.Kind.Wire] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def articles(q: Api.ArticleQuery): Future[Article.List] = call(Api.articles)(q)
  def getArticle(slug: Article.Slug, brief: Article.FullText = Article.FullText(false)): Future[Article.View] =
    call(Api.getArticle)((ReviewGoal.Slug(slug.value), brief))
  def places(
      q: Option[Api.Search.Text],
      asOf: Option[Api.AggregateQuery.AsOf],
      limit: Option[Paging.Limit]
  ): Future[List[Place.Hit]] =
    call(Api.places)((q.map(t => Place.Kind.Wire(t.value)), asOf, limit.map(l => Api.PlacesGeoQuery.Limit(l.value))))
  def place(
      id: Place.Id,
      asOf: Option[Api.AggregateQuery.AsOf],
      counts: Place.Detail = Place.Detail(false)
  ): Future[PlaceDetail] =
    call(Api.place)((id, asOf, Option.when(counts.value)(counts)))
  def about(): Future[About] = call(Api.about)(())
  def basemap(): Future[Basemap.Status] = call(BasemapApi.status)(())
  def basemapFetch(q: Basemap.FetchQuery): Future[JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: Basemap.RemoveQuery): Future[Basemap.Removed] = call(BasemapApi.remove)(q)
  def job(id: JobInfo.Id): Future[JobInfo] = call(Api.job)(SpatialHierarchyResult.Job.Id(id.value))
  def uiCounters(): Future[UiNotices.Counters] = call(UiApi.counters)(())
  def markSeen(board: UiNotices.Board.Wire, at: UiNotices.Counter.Seen): Future[UiNotices.Counters] =
    call(UiApi.markSeen)((board, UiNotices.SeenMark(Some(at))))

  def queueList(list: Queue.Kind.Label, all: Boolean = false): Future[QueueListing] =
    call(OpsApi.queueList)((Queue.Wire(list.value), all, None, None))
  def queueForGoal(list: Queue.Kind.Label, goal: Goal.Slug, all: Boolean = false): Future[QueueListing] =
    call(OpsApi.queueList)((Queue.Wire(list.value), all, Some(Queue.Wire(goal.value)), None))
  def queueOfJob(list: Queue.Kind.Label, job: JobInfo.Id): Future[QueueListing] =
    call(OpsApi.queueList)((Queue.Wire(list.value), true, None, Some(Queue.Wire(job.value))))
  def activity(
      show: Option[Activity.Show.Wire],
      since: Option[Activity.Actor],
      goal: Option[Goal.Slug],
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor]
  ): Future[Activity.Page] = call(Api.activity)(
    (
      show.map(s => Activity.Kind(s.value)),
      since,
      goal.map(g => Activity.Subject(g.value)),
      limit.map(l => Activity.Limit(l.value)),
      before.map(c => Activity.Cursor(c.value))
    )
  )
  def jobControl(id: SpatialHierarchyResult.Job.Id, action: Api.JobControl.Action): Future[JobInfo] =
    call(Api.jobControl)((id, action))

  // ── a person's bookmarks, notes and read marks ───────────────────────────────────────────────────

  def bookmarks(): Future[List[Bookmark]] =
    call(PersonalApi.bookmarks)((None, None, Some(PersonalIds.MaxLimit)))
  def addBookmark(b: Bookmark.New): Future[Bookmark] =
    call(PersonalApi.addBookmark)(b)
  def editBookmark(id: Bookmark.Id, e: Bookmark.Edit): Future[Bookmark] =
    call(PersonalApi.editBookmark)((id, e))
  def removeBookmark(id: Bookmark.Id): Future[Bookmark] = call(PersonalApi.removeBookmark)(id)
  def notes(): Future[List[Note]] =
    call(PersonalApi.notes)((None, None, None, Some(PersonalIds.MaxLimit)))
  def addNote(n: Note.New): Future[Note] = call(PersonalApi.addNote)(n)
  def editNote(id: Note.Id, e: Note.Edit): Future[Note] =
    call(PersonalApi.editNote)((id, e))
  def removeNote(id: Note.Id): Future[Note] = call(PersonalApi.removeNote)(id)
  def readMarks(): Future[List[ReadMark]] =
    call(PersonalApi.reading)((None, Some(Activity.Show.Wire("all")), Some(PersonalIds.MaxLimit)))
  def setRead(m: ReadMark.Set): Future[ReadMark] =
    call(PersonalApi.setRead)(m)
  def opened(o: ReadOpened): Future[ReadMark] = call(PersonalApi.opened)(o)
  def unread(kind: ItemKind.Wire, limit: Paging.Limit, offset: Offset): Future[UnreadPage] =
    call(PersonalApi.unread)((Some(kind), Some(limit.value), Some(UnreadPage.Offset(offset.value))))

  // ── Settings and the setup ───────────────────────────────────────────────────────────────────────

  def settings(prefix: Option[ConfigEdit.Page.Selector] = None): Future[ConfigEdit.Page] =
    call(ConfigEditApi.get)(prefix)
  def writeSettings(w: ConfigEdit.Write): Future[ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer(): Future[ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backupNow(): Future[Backup.Status] = call(Api.backup)(())
  def backups(): Future[List[Backup.Info]] = call(Api.backups)(())
  def initData(fetch: Api.Overwrite): Future[JobInfo] = call(Api.init)(fetch)
  def placeSources(): Future[List[Gazetteer.SourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: Api.ExternalPlaces.Source): Future[JobInfo] = call(Api.loadPlaces)((source, Api.Apply(true)))
  def eventCatalogues(): Future[List[CatalogProviderInfo]] = call(EventsApi.catalogues)(())
  def importEvents(provider: EventImportRequest.Provider): Future[JobInfo] =
    call(EventsApi.importEvents)(EventImportRequest(provider, dryRun = EventImportRequest.DryRun(false)))
  def mediaProviders(): Future[List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  def importMedia(provider: MediaItem.Provider): Future[JobInfo] =
    call(Api.mediaImportJob)(MediaImportRequest(provider, dryRun = MediaImportRequest.DryRun(false)))
  def setAgentMode(mode: AgentMode): Future[AutopilotStatus] =
    call(AutopilotApi.setMode)(AgentMode.ModeSet(mode.wire, Some(AgentMode.ModeSet.By("web"))))
  def acquireRecheck(in: Acquire.Checks.RecheckRequest): Future[Acquire.Checks.RecheckResult] =
    call(AcquisitionWorkApi.acquireRecheck)(in)
  def queuePatch(id: Queue.QueueItem.Id, p: Queue.QueuePatch): Future[Queue.QueueItem] =
    call(OpsApi.queuePatch)((Queue.Id(id.value), p))
  def queueAdd(list: Queue.Kind.Label, in: Queue.QueueAdd): Future[Queue.QueueItem] =
    call(OpsApi.queueAdd)((Queue.Wire(list.value), in))
  def queueDelete(id: Queue.QueueItem.Id): Future[Queue.QueueItem] =
    call(OpsApi.queueDelete)(Queue.Id(id.value))

  def graph(citekey: Citekey, depth: Option[Source.Graph.DepthCount] = None): Future[Source.Graph] =
    call(Api.graph)((citekey, depth))

  def periods(
      q: Option[Api.Search.Text] = None,
      region: Option[CorpusCounts.Periods.Collection] = None,
      from: Option[Year] = None,
      to: Option[Year] = None
  ): Future[List[HistoricalPeriod]] =
    call(Api.periods)(
      (
        q.map(t => CorpusCounts.Periods.Source(t.value)),
        region,
        from.map(y => Api.PlacesGeoQuery.From(y.value)),
        to.map(y => Api.PlacesGeoQuery.To(y.value))
      )
    )

  /** The places' GeoJSON — megabytes over a whole range, read as text by the browser ([[text]]). */
  def placesGeo(q: Api.PlacesGeoQuery): Future[Api.PlacesGeo.Json] = text(Api.placesGeo)(q).map(Api.PlacesGeo.Json(_))

  def mediaGeo(q: Api.MediaGeoQuery): Future[List[MediaGeoHit]] = call(Api.mediaGeo)(q)
  def mediaFacets(): Future[MediaFacets] = call(Api.mediaFacets)(())
  def getEvent(id: Event.Id): Future[EventDetail] = call(Api.getEvent)(Event.Uid(id.value))

  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)
  def eventsGeo(q: Api.EventQuery): Future[EventGeo.List] = call(Api.eventsGeo)(q)

  /** The literature search for `q`, works not in the corpus yet (an event card's lead, #1449). */
  def discover(q: Api.Search.Text, limit: Paging.Limit): Future[DiscoverResult] =
    call(AcquireApi.discover)((AcquireApi.Query(q.value), None, None, Some(AcquireApi.Limit(limit.value)), true))

  /** One page of the jobs that match `f` ([[Paging]]): `before` = older than that job, `upTo` = down to it. */
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Future[(List[JobInfo], Paging.Page)] =
    call(Api.jobs)(
      (
        f,
        limit.map(l => Api.Jobs.Limit(l.value)),
        before.map(c => Api.Jobs.Status(c.value)),
        upTo.map(c => Jobs.Filter(c.value))
      )
    )

  def patchSource(ck: Citekey, p: Source.Patch): Future[Source.WithFindings] =
    call(Api.patchSource)((ck, p, Api.Apply(false)))

  // ── embedded agents ──────────────────────────────────────────────────────────────────────────────

  def agentRuns(
      f: ListFilters.RunFilter,
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Future[(List[Agents.RunInfo], Paging.Page)] =
    call(AgentsApi.runs)(
      (f, limit, before.map(c => Agents.RunInfo.Status(c.value)), upTo.map(c => Agents.RunInfo.Pipeline(c.value)))
    )
  def startRun(req: Agents.RunRequest): Future[Agents.RunInfo] = call(AgentsApi.startRun)(req)
  def agentRun(id: Agents.RunInfo.Id): Future[Agents.RunDetail] = call(AgentsApi.run)(id)

  /** pause | resume | cancel; a resume may raise the run's cost cap (the way out of a budget pause). */
  def agentControl(
      id: Agents.RunInfo.Id,
      action: Api.JobControl.Action,
      budgetUsd: Option[Agents.RunRequest.BudgetUsd] = None
  ): Future[Agents.RunInfo] =
    call(AgentsApi.control)((id, Agents.Action(action.value), budgetUsd, None))
  def agentModels: Future[Agents.ModelsInfo] = call(AgentsApi.models)(())
  def setChain(
      role: ModelChains.RoleChain.Role,
      models: List[ModelChains.ChainSet.Models]
  ): Future[ModelChains.RoleChain] =
    call(AgentsApi.setChain)((AgentRole.Wire(role.value), ModelChains.ChainSet(models)))
  def resetChain(role: ModelChains.RoleChain.Role): Future[ModelChains.RoleChain] =
    call(AgentsApi.resetChain)(AgentRole.Wire(role.value))
  def setAllowance(in: ModelChains.AllowanceSet): Future[ModelChains.AllowanceStatus] = call(AgentsApi.setAllowance)(in)
  def removeAllowance(target: ModelChains.AllowanceStatus.Target): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeAllowance)(ReviewGoal.Slug(target.value))
  def agentDoctor(offline: Agents.DoctorReport.Online): Future[Agents.DoctorReport] =
    call(AgentsApi.doctor)(Some(offline))
  def agentQuota: Future[PlanQuota.QuotaInfo] = call(AgentsApi.quota)(())
  def setPool(pool: PlanQuota.PoolId.Wire, in: PlanQuota.PoolSet): Future[PlanQuota.PoolStatus] =
    call(AgentsApi.setPool)((PlanQuota.PoolIdText(pool.value), in))
  def refreshQuota(pool: PlanQuota.PoolId.Wire): Future[PlanQuota.QuotaRefreshed] =
    call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(Some(pool)))
  def approvals(
      f: ListFilters.ApprovalFilter,
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Future[(List[Agents.ApprovalInfo], Paging.Page)] =
    call(AgentsApi.approvals)(
      (f, limit, before.map(c => Approval.State.Wire(c.value)), upTo.map(c => Approval.Kind.Wire(c.value)))
    )
  def decide(
      id: Agents.ApprovalInfo.Id,
      d: Agents.Decision,
      note: Option[Agents.DecisionRequest.Note]
  ): Future[Agents.ApprovalInfo] =
    call(AgentsApi.decide)((id, d.wire, Agents.DecisionRequest(note)))

  // ── goals: the list, one goal's view, its links and schedules ───────────────────────────────────

  def goals(limit: Option[Paging.Limit]): Future[Goal.List] =
    call(Api.goals)(Api.GoalQuery(None, limit.map(l => Api.GoalQuery.Limit(l.value)), None))
  def goalView(slug: Goal.Slug): Future[Goal.Plans.GoalView] = call(AgentsApi.goalView)(ReviewGoal.Slug(slug.value))
  def getGoal(slug: Goal.Slug): Future[Goal] = call(Api.getGoal)(Goal.Uid(slug.value))

  /** Create (or REPLACE) a goal — the New research area form checks the slug is free first. */
  def putGoal(slug: Goal.Slug, in: Goal.Put): Future[Goal] = call(Api.putGoal)((Goal.Uid(slug.value), in))
  def addGoalLink(slug: Goal.Slug, in: Goal.Plans.LinkAdd): Future[Goal.Plans.GoalLink] =
    call(AgentsApi.addGoalLink)((ReviewGoal.Slug(slug.value), in))
  def removeGoalLink(
      slug: Goal.Slug,
      kind: Goal.Plans.LinkKind,
      ref: Goal.Plans.GoalLink.Ref
  ): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeGoalLink)((ReviewGoal.Slug(slug.value), kind.wire, Citekey(ref.value)))
  def setSchedule(
      slug: Goal.Slug,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet
  ): Future[Goal.Plans.GoalSchedule] =
    call(AgentsApi.setSchedule)((ReviewGoal.Slug(slug.value), kind.wire, in))
  def removeSchedule(slug: Goal.Slug, kind: Goal.Plans.ScheduleKind): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeSchedule)((ReviewGoal.Slug(slug.value), kind.wire))

  /** `POST /api/ingest`, with upload progress (`fetch` reports none, so this one request goes through
    * `XMLHttpRequest`). The URL and its query parameters still come from the tapir endpoint, and the key from
    * [[authorize]] — the same two places as every other call.
    */
  def ingest(params: Ingest.Params, file: dom.File, progress: Double => Unit): Future[Ingest.Accepted] =
    val req = authorize(interp.toRequest(Api.ingest, None).apply((params, file)))
    val result = Promise[Ingest.Accepted]()
    val xhr = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers
      .filter(h => h.name.equalsIgnoreCase("Authorization"))
      .foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 202 || xhr.status == 200 then
        io.circe.parser
          .decode[Ingest.Accepted](body)
          .fold(
            e =>
              result.failure(
                ApiFailure(
                  ApiFailure.Status(xhr.status),
                  Api.Error(Problem(s"could not read the server's answer: ${e.getMessage}"))
                )
              ),
            ok => result.success(ok)
          )
      else
        val decoded = io.circe.parser.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(ApiFailure.Status(xhr.status), decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(ApiFailure.Status(0), None))
    xhr.send(file)
    result.future

  // ── merging another collection's bundle (the /merge review screen) ──────────────────────────────

  def mergePlan(req: MergePlan.Request): Future[MergePlan] = call(Api.mergePlan)(req)
  def mergeSession(bundle: Sha256): Future[MergeSession] = call(Api.mergeSession)(Api.Merge.Id(bundle.value))
  def putMergeSession(bundle: Sha256, r: MergeResolutions): Future[MergeSession] =
    call(Api.putMergeSession)((Api.Merge.Id(bundle.value), r))
  def parseResolutions(yaml: Api.Merge.Id): Future[MergeResolutions] = call(Api.parseResolutions)(yaml)
  def mergeApply(req: MergeApplyRequest): Future[MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: MergeApplyJob.Job): Future[MergeApplyJob] =
    call(Api.mergeApplyStatus)(Api.Merge.Id(job.value))

  /** `POST /api/bundle/upload`, with upload progress — a bundle can be large; through `XMLHttpRequest` for the same
    * reason as [[ingest]].
    */
  def uploadBundle(file: dom.File, progress: Double => Unit): Future[ExchangeBundle.Upload] =
    val req = authorize(interp.toRequest(Api.uploadBundle, None).apply(file))
    val result = Promise[ExchangeBundle.Upload]()
    val xhr = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers
      .filter(h => h.name.equalsIgnoreCase("Authorization"))
      .foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 200 then
        io.circe.parser
          .decode[ExchangeBundle.Upload](body)
          .fold(
            e =>
              result.failure(
                ApiFailure(
                  ApiFailure.Status(xhr.status),
                  Api.Error(Problem(s"could not read the server's answer: ${e.getMessage}"))
                )
              ),
            ok => result.success(ok)
          )
      else
        val decoded = io.circe.parser.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(ApiFailure.Status(xhr.status), decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(ApiFailure.Status(0), None))
    xhr.send(file)
    result.future

  // ── process managers (docs/research-process-managers.md) ─────────────────────────────────────────

  def managers: Future[List[Managers.ManagerInfo]] = call(ManagersApi.list)(())
  def autopilot: Future[AutopilotStatus] = call(AutopilotApi.autopilot)(())
  def setAutopilot(on: Autopilot.AutopilotSet.On): Future[AutopilotStatus] =
    call(AutopilotApi.setAutopilot)(Autopilot.AutopilotSet(on, Some(Autopilot.AutopilotSet.By("web"))))

  /** The autopilot's focus: pin/unpin/priority a goal, or the cap (`max`; no value: the config's). */
  def setFocus(
      action: Autopilot.FocusSet.Action,
      goal: Option[Goal.Slug] = None,
      value: Option[Autopilot.FocusSet.Value] = None
  ): Future[Autopilot.FocusInfo] =
    call(ManagersApi.setFocus)(Autopilot.FocusSet(action, goal, value, Some(Autopilot.FocusSet.By("web"))))

  /** The central scheduler: its status and queue (`limit` waiting items), a live strategy switch, one item's why. */
  def scheduler(limit: Option[Paging.Limit] = None): Future[AgentScheduler.SchedulerStatus] =
    call(ManagersApi.scheduler)(limit.map(l => Managers.Scheduler.Limit(l.value)))
  def setScheduler(strategy: AgentScheduler.SchedulerSet.Strategy): Future[AgentScheduler.SchedulerStatus] =
    call(ManagersApi.setScheduler)(AgentScheduler.SchedulerSet(strategy, Some(AgentScheduler.SchedulerSet.By("web"))))
  def schedulerWhy(item: Managers.ManagerInfo.Id): Future[AgentScheduler.WhyInfo] = call(ManagersApi.schedulerWhy)(item)

  /** Drain (nothing new starts while the runs in flight finish) or lift it. */
  def drain(on: Autopilot.DrainSet.On): Future[AutopilotStatus] =
    call(AutopilotApi.drain)(Autopilot.DrainSet(on, Some(Autopilot.DrainSet.By("web"))))

  /** The goals with no progress in `days` days, why, and what unblocks them. */
  def stalls(days: Option[Stalls.Limit] = None): Future[Stalls.StallReport] = call(ManagersApi.stalls)(days)

  /** Everything waiting for a person, by kind. */
  def approvalDigest: Future[ApprovalDigest.Digest] = call(ManagersApi.digest)(())
  def manager(id: Managers.ManagerInfo.Id): Future[Managers.ManagerDetail] = call(ManagersApi.get)(id)
  def managerUpdate(id: Managers.ManagerInfo.Id, in: Managers.ManagerUpdate): Future[Managers.ManagerInfo] =
    call(ManagersApi.update)((id, in))

  /** pause | resume | stop; `runs` = a pause takes its runs in flight with it. */
  def managerAction(
      id: Managers.ManagerInfo.Id,
      action: ManagersApi.Action,
      runs: Boolean = false
  ): Future[Managers.ManagerInfo] =
    call(ManagersApi.action)((id, action, Option.when(runs)(true)))
  def managerItems(
      id: Managers.ManagerInfo.Id,
      status: Option[ItemStatus.Wire],
      offset: Offset,
      limit: Paging.Limit
  ): Future[Managers.ItemPage] =
    call(ManagersApi.items)(
      (id, status, Some(Managers.ItemPage.Limit(offset.value)), Some(UnreadPage.Offset(limit.value)))
    )
  def managerPatchItem(
      id: Managers.ManagerInfo.Id,
      item: Managers.ManagerItem.Id,
      p: Managers.ItemPatch
  ): Future[Managers.ManagerItem] =
    call(ManagersApi.patchItem)((id, Managers.ManagerItem.Ref(item.value.toString), p))
  def managerAddHint(id: Managers.ManagerInfo.Id, in: Managers.HintAdd): Future[Managers.ManagerHint] =
    call(ManagersApi.addHint)((id, in))
  def managerRemoveHint(id: Managers.ManagerInfo.Id, hint: Managers.ManagerHint.Id): Future[Goal.Plans.Removed] =
    call(ManagersApi.removeHint)((id, hint))
  def managerEvents(
      id: Managers.ManagerInfo.Id,
      before: Option[Managers.EventPage.Before],
      limit: Paging.Limit
  ): Future[Managers.EventPage] =
    call(ManagersApi.events)((id, before, Some(Managers.EventPage.Limit(limit.value))))

  /** A page button — Review, Extend, Enrich (an article or a goal), Extend a goal (`seed`): the target goes to the
    * manager of its kind, first in line.
    */
  def enqueue(kind: Managers.EnqueueKind, target: Managers.EnqueueRequest.Target): Future[Managers.EnqueueResult] =
    call(ManagersApi.enqueue)(Managers.EnqueueRequest(kind.wire, target, Some(Managers.EnqueueRequest.Priority(10))))

  /** The article window's Review button: the article goes to the review manager, first in line. */
  def enqueueReview(slug: Article.Slug): Future[Managers.EnqueueResult] =
    enqueue(Managers.EnqueueKind.Review, Managers.EnqueueRequest.Target(slug.value))

  /** A new manager (the New research area form's seeding manager); created paused. */
  def managerCreate(in: Managers.ManagerCreate): Future[Managers.ManagerInfo] = call(ManagersApi.create)(in)

  /** A page's HTML fragment, whatever the status (an error page is shown as it is). */
  def fetchHtml(url: Url): Future[String] = fetchText(url.value).map(_._2)

  /** A text answer of `ep` read by the BROWSER (#1949): sttp's fetch backend reads a body as bytes and decodes them in
    * Scala.js, a byte at a time — tens of ms per MB, on the page's one thread, for a whole range's 7 MB of boundaries.
    * `Response.text()` decodes natively. The request is still the endpoint's own (its path and query, from the same
    * Tapir definition); a GET, so no key.
    */
  private def text[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Future[String] =
    fetchText(interp.toRequest(ep, None).apply(in).uri.toString).flatMap { (status, body) =>
      if status >= 200 && status < 300 then Future.successful(body)
      else Future.failed(ApiFailure.of(ApiFailure.Status(status), decodeError(body)))
    }

  private def decodeError(body: String): Option[Api.Error] =
    io.circe.parser.decode[Api.Error](body).toOption

  /** GET `url`: the status and the body, decoded by the browser. */
  private def fetchText(url: String): Future[(Int, String)] =
    import scala.scalajs.js.Thenable.Implicits.*
    for
      r <- dom.fetch(url).toFuture
      body <- r.text().toFuture
    yield (r.status, body)

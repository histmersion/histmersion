package com.histmersion
package web

import com.histmersion.api.automation.activity.Show
import com.histmersion.api.catalogue.gazetteer.SourceInfo
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.QueuePatch
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.listfilters.JobFilter
import com.histmersion.api.automation.listfilters.RunFilter
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.agents.RunRequest
import com.histmersion.api.automation.agents.RunDetail
import com.histmersion.api.automation.agents.ModelsInfo
import com.histmersion.api.automation.modelchains.RoleChain
import com.histmersion.api.automation.modelchains.ChainSet
import com.histmersion.api.automation.modelchains.AllowanceSet
import com.histmersion.api.automation.modelchains.AllowanceStatus
import com.histmersion.api.automation.agents.DoctorReport
import com.histmersion.api.automation.planquota.QuotaInfo
import com.histmersion.api.automation.planquota.PoolId
import com.histmersion.api.automation.planquota.PoolSet
import com.histmersion.api.automation.planquota.PoolStatus
import com.histmersion.api.automation.planquota.QuotaRefreshed
import com.histmersion.api.automation.planquota.QuotaRefresh
import com.histmersion.api.automation.listfilters.ApprovalFilter
import com.histmersion.api.automation.agents.ApprovalInfo
import com.histmersion.api.automation.agents.Decision
import com.histmersion.api.automation.agents.DecisionRequest
import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.api.automation.autopilot.AutopilotSet
import com.histmersion.api.automation.autopilot.FocusSet
import com.histmersion.api.automation.autopilot.FocusInfo
import com.histmersion.api.automation.agentscheduler.SchedulerStatus
import com.histmersion.api.automation.agentscheduler.SchedulerSet
import com.histmersion.api.automation.agentscheduler.WhyInfo
import com.histmersion.api.automation.autopilot.DrainSet
import com.histmersion.api.automation.Stall
import com.histmersion.api.automation.approvaldigest.Digest
import com.histmersion.api.automation.managers.ManagerDetail
import com.histmersion.api.automation.managers.ManagerUpdate
import com.histmersion.api.automation.managers.ItemPage
import com.histmersion.api.automation.managers.ManagerItem
import com.histmersion.api.automation.managers.ItemPatch
import com.histmersion.api.automation.managers.HintAdd
import com.histmersion.api.automation.managers.ManagerHint
import com.histmersion.api.automation.managers.EventPage
import com.histmersion.api.automation.managers.EnqueueKind
import com.histmersion.api.automation.managers.EnqueueRequest
import com.histmersion.api.automation.managers.EnqueueResult
import com.histmersion.api.automation.managers.ManagerCreate
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.json.RawJson
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.concurrent.Promise
import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import sttp.client4.fetch.FetchBackend
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
  Creation,
  Goal,
  ListFilters,
  Managers,
  ManagersApi,
  ModelChains,
  PlanQuota,
  Queue
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
import com.histmersion.api.instance.{About, BuildId, UiApi, UiConfig, UiNotices}
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
import com.histmersion.api.Api.HttpMethod.equality
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.Http, "sends the web UI's HTTP requests and decodes the answers")
object ApiClient:
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  private val backend = FetchBackend()
  private val interp = SttpClientInterpreter()

  /** THE one place a request is given its `Authorization` header ([[Auth.currentHeader]]): every request, reads too,
    * since a server reached from a network it does not trust gates them all.
    */
  private def authorize[T](req: Request[T]): Request[T] =
    Auth.currentHeader.fold(req)((k, v) => req.header(k, v))

  /** A refused key is dropped and asked for again, wherever the request came from. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def refuse(f: ApiFailure): ApiFailure =
    if f.unauthorized then Auth.rejected()
    f

  def viaWeb[T](req: Request[T], in: Any): Request[T] =
    val asRequested = scala.reflect.classTag[Creation.Requested]
    val requested = asRequested
      .unapply(in)
      .orElse(
        scala.reflect
          .classTag[Product]
          .unapply(in)
          .filter(_.productPrefix.startsWith("Tuple"))
          .flatMap(_.productIterator.flatMap(asRequested.unapply).nextOption())
      )
    if !requested.exists(_.via.isEmpty) then req
    else
      req.body match
        case StringBody(text, _, _) =>
          RawJson
            .parse(text)
            .toOption
            .filter(_.asObject.isDefined)
            .fold(req) { json =>
              val replaced = req
                .withHeaders(req.headers.filterNot(_.is(sttp.model.HeaderNames.ContentLength)))
                .body(json.mapObject(_.updated("via", Creation.Web.asJson)).noSpaces)
              req.header(sttp.model.HeaderNames.ContentType).fold(replaced)(replaced.contentType)
            }
        case _ => req

  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private[web] def call[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Future[O] =
    val req = authorize(viaWeb(interp.toRequest(ep, None).apply(in), in))
    // no answer at all (the fetch itself failed) is a transport failure, never a problem the server sent
    unreachable(req.send(backend)).flatMap { resp =>
      resp.header(BuildId.Header).flatMap(BuildId.parse).foreach(PageBuild.current.check)
      val code = resp.code.code
      resp.body match
        case DecodeResult.Value(Right(o))    => Future.successful(o)
        case DecodeResult.Value(Left(e))     => Future.failed(refuse(ApiFailure(ApiFailure.Status(code), e)))
        case _ if code == 401 || code == 403 => Future.failed(refuse(ApiFailure.of(ApiFailure.Status(code), None)))
        case other                           =>
          Future.failed(
            ApiFailure(
              ApiFailure.Status(code),
              Api.Error(Problem.Unclassified(Legacy.Text(s"could not read the server's answer (HTTP $code): $other")))
            )
          )
    }

  /** The URL template of a layer's tiles (`{z}`, `{x}`, `{y}` for the map to fill in): `q`'s request as the tile
    * endpoint builds it, the tile given as placeholder numbers no tile has, swapped for the template's markers.
    * Absolute: the map's workers fetch it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def tileTemplate(q: TilesApi.TileQuery): Url =
    val req = interp
      .toRequest(TilesApi.tile, None)
      .apply(
        q.modify(_.z).setTo(TilePlaceholder.Z).modify(_.x).setTo(TilePlaceholder.X).modify(_.y).setTo(TilePlaceholder.Y)
      )
    Url(dom.window.location.origin + TilePlaceholder.fill(req.uri.toString))

  def config(): Future[UiConfig] = call(Api.config)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sources(f: Filter): Future[SearchResult] = call(Api.sources)(f)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]] = call(Api.findings)(q)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram] = call(Api.aggregate)(q)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def facets(field: Api.Facets.Field, prefix: Option[Api.Facets.Prefix], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def resolveId(id: Api.Lookup.Text): Future[ResolvedId] = call(Api.resolveId)(id)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def getSource(
      ck: Citekey,
      body: Api.SourceViewQuery.FullText = Api.SourceViewQuery.FullText.FullText
  ): Future[Source.WithFindings] =
    call(Api.getSource)((ck, body))

  /** The articles that cite a source (`GET /api/sources/{citekey}/articles`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sourceArticles(ck: Citekey): Future[List[Backlink]] = call(Api.sourceArticles)(ck)

  /** `POST /api/acquire/ingest`: a DOI or arXiv id brought in ([[library.DoiIngest]] builds the request) — by the legal
    * open-access chain only, whatever the request says: the server may be configured to allow fetchers without the
    * rights holder's authorisation, and the web UI never sets them off.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def ingestId(req: Ingest.Request): Future[Ingest.Report] =
    call(AcquireApi.ingest)(req.modify(_.legalOnly).setTo(Ingest.Request.LegalOnly.LegalOnly))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def neighbors(ck: Citekey, kinds: List[Edge.Kind.Wire] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def articles(q: Api.ArticleQuery): Future[Article.List] = call(Api.articles)(q)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def getArticle(slug: Article.Slug, brief: Article.FullText = Article.FullText.SummaryOnly): Future[Article.View] =
    call(Api.getArticle)((ReviewGoal.Slug(slug.value), brief))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def places(
      q: Option[Api.Search.Text],
      asOf: Option[Api.AggregateQuery.AsOf],
      limit: Option[Paging.Limit]
  ): Future[List[Place.Hit]] =
    call(Api.places)((q.map(t => Place.Kind.Wire(t.value)), asOf, limit.map(l => Api.PlacesGeoQuery.Limit(l.value))))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def place(
      id: Place.Id,
      asOf: Option[Api.AggregateQuery.AsOf],
      counts: Place.Detail = Place.Detail.Brief
  ): Future[PlaceDetail] =
    call(Api.place)((id, asOf, Option.when(counts.detail)(counts)))
  def about(): Future[About] = call(Api.about)(())
  def basemap(): Future[Basemap.Status] = call(BasemapApi.status)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def basemapFetch(q: Basemap.FetchQuery): Future[JobInfo] = call(BasemapApi.fetch)(q)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def basemapRemove(q: Basemap.RemoveQuery): Future[Basemap.Removed] = call(BasemapApi.remove)(q)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def job(id: JobInfo.Id): Future[JobInfo] = call(Api.job)(id)
  def uiCounters(): Future[UiNotices.Counters] = call(UiApi.counters)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def markSeen(board: UiNotices.Board.Wire, at: UiNotices.Counter.Seen): Future[UiNotices.Counters] =
    call(UiApi.markSeen)((board, UiNotices.SeenMark(Some(at))))

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queueList(list: api.automation.queue.Docket.Label, all: Boolean = false): Future[QueueListing] =
    call(OpsApi.queueList)((Queue.Wire(list.value), all, None, None))
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queueForGoal(
      list: api.automation.queue.Docket.Label,
      goal: Goal.Slug,
      all: Boolean = false
  ): Future[QueueListing] =
    call(OpsApi.queueList)((Queue.Wire(list.value), all, Some(Queue.Wire(goal.value)), None))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queueOfJob(list: api.automation.queue.Docket.Label, job: JobInfo.Id): Future[QueueListing] =
    call(OpsApi.queueList)((Queue.Wire(list.value), true, None, Some(Queue.Wire(job.value))))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def activity(
      show: Option[Show.Wire],
      since: Option[Activity.Actor],
      goal: Option[Goal.Slug],
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor]
  ): Future[api.automation.activity.Page] = call(Api.activity)(
    (
      show.map(s => Activity.Kind(s.value)),
      since,
      goal.map(g => Activity.Subject(g.value)),
      limit.map(l => Activity.Limit(l.value)),
      before.map(c => Activity.Cursor(c.value))
    )
  )
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def jobControl(id: SpatialHierarchyResult.Job.Id, action: Api.JobControl.Action): Future[JobInfo] =
    call(Api.jobControl)((id, action))

  // ── a person's bookmarks, notes and read marks ───────────────────────────────────────────────────

  def bookmarks(): Future[List[Bookmark]] =
    call(PersonalApi.bookmarks)((None, None, Some(PersonalIds.MaxLimit)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def addBookmark(b: Bookmark.New): Future[Bookmark] =
    call(PersonalApi.addBookmark)(b)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def editBookmark(id: Bookmark.Id, e: Bookmark.Edit): Future[Bookmark] =
    call(PersonalApi.editBookmark)((id, e))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def removeBookmark(id: Bookmark.Id): Future[Bookmark] = call(PersonalApi.removeBookmark)(id)
  def notes(): Future[List[Note]] =
    call(PersonalApi.notes)((None, None, None, Some(PersonalIds.MaxLimit)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def addNote(n: Note.New): Future[Note] = call(PersonalApi.addNote)(n)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def editNote(id: Note.Id, e: Note.Edit): Future[Note] =
    call(PersonalApi.editNote)((id, e))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def removeNote(id: Note.Id): Future[Note] = call(PersonalApi.removeNote)(id)
  def readMarks(): Future[List[ReadMark]] =
    call(PersonalApi.reading)((None, Some(Show.Wire("all")), Some(PersonalIds.MaxLimit)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setRead(m: ReadMark.Set): Future[ReadMark] =
    call(PersonalApi.setRead)(m)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def opened(o: ReadOpened): Future[ReadMark] = call(PersonalApi.opened)(o)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def unread(kind: ItemKind.Wire, limit: Paging.Limit, offset: Offset): Future[UnreadPage] =
    call(PersonalApi.unread)((Some(kind), Some(limit.value), Some(UnreadPage.Offset(offset.value))))

  // ── Settings and the setup ───────────────────────────────────────────────────────────────────────

  def settings(prefix: Option[ConfigEdit.Page.Selector] = None): Future[ConfigEdit.Page] =
    call(ConfigEditApi.get)(prefix)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def writeSettings(w: ConfigEdit.Write): Future[ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer(): Future[ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backupNow(): Future[Backup.Status] = call(Api.backup)(())
  def backups(): Future[List[Backup.Info]] = call(Api.backups)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def initData(fetch: Api.Fetching): Future[JobInfo] = call(Api.init)(fetch)
  def placeSources(): Future[List[SourceInfo]] = call(Api.placeSources)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def loadPlaces(source: Api.ExternalPlaces.Source): Future[JobInfo] = call(Api.loadPlaces)((source, Api.Apply.Apply))
  def eventCatalogues(): Future[List[CatalogProviderInfo]] = call(EventsApi.catalogues)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def importEvents(provider: EventImportRequest.Provider): Future[JobInfo] =
    call(EventsApi.importEvents)(EventImportRequest(provider, dryRun = EventImportRequest.DryRun.Apply))
  def mediaProviders(): Future[List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def importMedia(provider: MediaItem.Provider): Future[JobInfo] =
    call(Api.mediaImportJob)(MediaImportRequest(provider, dryRun = MediaImportRequest.DryRun.Apply))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setAgentMode(mode: AgentMode): Future[AutopilotStatus] =
    call(AutopilotApi.setMode)(AgentMode.ModeSet(mode.wire, Some(AgentMode.ModeSet.By("web"))))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def acquireRecheck(in: Acquire.Checks.RecheckRequest): Future[Acquire.Checks.RecheckResult] =
    call(AcquisitionWorkApi.acquireRecheck)(in)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queuePatch(id: QueueItem.Id, p: QueuePatch): Future[QueueItem] =
    call(OpsApi.queuePatch)((Queue.Id(id.value), p))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queueAdd(list: api.automation.queue.Docket.Label, in: QueueAdd): Future[QueueItem] =
    call(OpsApi.queueAdd)((Queue.Wire(list.value), in))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queueDelete(id: QueueItem.Id): Future[QueueItem] =
    call(OpsApi.queueDelete)(Queue.Id(id.value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def graph(citekey: Citekey, depth: Option[Source.Graph.DepthCount] = None): Future[Source.Graph] =
    call(Api.graph)((citekey, depth))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def placesGeo(q: Api.PlacesGeoQuery): Future[Api.PlacesGeo.Json] = text(Api.placesGeo)(q).map(Api.PlacesGeo.Json(_))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def mediaGeo(q: Api.MediaGeoQuery): Future[List[MediaGeoHit]] = call(Api.mediaGeo)(q)
  def mediaFacets(): Future[MediaFacets] = call(Api.mediaFacets)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def getEvent(id: Event.Id): Future[EventDetail] = call(Api.getEvent)(Event.Uid(id.value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def eventsGeo(q: Api.EventQuery): Future[EventGeo.List] = call(Api.eventsGeo)(q)

  /** The literature search for `q`, works not in the corpus yet (an event card's lead, #1449). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def discover(q: Api.Search.Text, limit: Paging.Limit): Future[DiscoverResult] =
    call(AcquireApi.discover)((AcquireApi.Query(q.value), None, None, Some(AcquireApi.Limit(limit.value)), true))

  /** One page of the jobs that match `f` ([[Paging]]): `before` = older than that job, `upTo` = down to it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def jobs(
      f: JobFilter,
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def patchSource(ck: Citekey, p: Source.Patch): Future[Source.WithFindings] =
    call(Api.patchSource)((ck, Source.Patch.Request.of(p), Api.Strictness.Lenient))

  // ── embedded agents ──────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def agentRuns(
      f: RunFilter,
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Future[(List[RunInfo], Paging.Page)] =
    call(AgentsApi.runs)(
      (f, limit, before.map(c => RunInfo.Status(c.value)), upTo.map(c => RunInfo.Pipeline(c.value)))
    )
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def startRun(req: RunRequest): Future[RunInfo] = call(AgentsApi.startRun)(req)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def agentRun(id: RunInfo.Id): Future[RunDetail] = call(AgentsApi.run)(id)

  /** pause | resume | cancel; a resume may raise the run's cost cap (the way out of a budget pause). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def agentControl(
      id: RunInfo.Id,
      action: Api.JobControl.Action,
      budgetUsd: Option[RunRequest.BudgetUsd] = None
  ): Future[RunInfo] =
    call(AgentsApi.control)((id, action, budgetUsd, None))
  def agentModels: Future[ModelsInfo] = call(AgentsApi.models)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def setChain(
      role: RoleChain.Role,
      models: List[ChainSet.Models]
  ): Future[RoleChain] =
    call(AgentsApi.setChain)((AgentRole.Wire(role.value), ChainSet(models)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def resetChain(role: RoleChain.Role): Future[RoleChain] =
    call(AgentsApi.resetChain)(AgentRole.Wire(role.value))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setAllowance(in: AllowanceSet): Future[AllowanceStatus] = call(AgentsApi.setAllowance)(in)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def removeAllowance(target: AllowanceStatus.Target): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeAllowance)(ReviewGoal.Slug(target.value))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def agentDoctor(offline: DoctorReport.Online): Future[DoctorReport] =
    call(AgentsApi.doctor)(Some(offline))
  def agentQuota: Future[QuotaInfo] = call(AgentsApi.quota)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def setPool(pool: PoolId.Wire, in: PoolSet): Future[PoolStatus] =
    call(AgentsApi.setPool)((PlanQuota.PoolIdText(pool.value), in))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def refreshQuota(pool: PoolId.Wire): Future[QuotaRefreshed] =
    call(AgentsApi.refreshQuota)(QuotaRefresh(Some(pool)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def approvals(
      f: ApprovalFilter,
      limit: Option[Paging.Limit],
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Future[(List[ApprovalInfo], Paging.Page)] =
    call(AgentsApi.approvals)(
      (f, limit, before.map(c => Approval.State.Wire(c.value)), upTo.map(c => Approval.Kind.Wire(c.value)))
    )
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def decide(
      id: ApprovalInfo.Id,
      d: Decision,
      note: Option[DecisionRequest.Note]
  ): Future[ApprovalInfo] =
    call(AgentsApi.decide)((id, d.wire, DecisionRequest(note)))

  // ── goals: the list, one goal's view, its links and schedules ───────────────────────────────────

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def goals(limit: Option[Paging.Limit]): Future[Goal.List] =
    call(Api.goals)(Api.GoalQuery(None, limit.map(l => Api.GoalQuery.Limit(l.value)), None))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def goalView(slug: Goal.Slug): Future[Goal.Plans.GoalView] = call(AgentsApi.goalView)(ReviewGoal.Slug(slug.value))

  /** A new research area: the goal (never replacing one), its plan run and its managers, all or nothing, on the server.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def createArea(in: Goal.AreaCreate): Future[Goal.AreaCreated] = call(Api.createArea)(in)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def addGoalLink(slug: Goal.Slug, in: Goal.Plans.LinkAdd): Future[Goal.Plans.GoalLink] =
    call(AgentsApi.addGoalLink)((ReviewGoal.Slug(slug.value), in))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def removeGoalLink(
      slug: Goal.Slug,
      kind: Goal.Plans.LinkKind,
      ref: Goal.Plans.GoalLink.Ref
  ): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeGoalLink)((ReviewGoal.Slug(slug.value), kind.wire, Citekey(ref.value)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def setSchedule(
      slug: Goal.Slug,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet
  ): Future[Goal.Plans.GoalSchedule] =
    call(AgentsApi.setSchedule)((ReviewGoal.Slug(slug.value), kind.wire, in))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def removeSchedule(slug: Goal.Slug, kind: Goal.Plans.ScheduleKind): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeSchedule)((ReviewGoal.Slug(slug.value), kind.wire))

  /** `POST /api/ingest`, with upload progress (`fetch` reports none, so this one request goes through
    * `XMLHttpRequest`). The URL and its query parameters still come from the tapir endpoint, and the key from
    * [[authorize]] — the same two places as every other call.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def ingest(params: Ingest.Params, file: dom.File, progress: Double => Unit): Future[Ingest.Accepted] =
    val req = authorize(viaWeb(interp.toRequest(Api.ingest, None).apply((params, file)), (params, file)))
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
        RawJson
          .decode[Ingest.Accepted](body)
          .fold(
            e =>
              result.failure(
                ApiFailure(
                  ApiFailure.Status(xhr.status),
                  Api.Error(Problem.Unclassified(Legacy.Text(s"could not read the server's answer: $e")))
                )
              ),
            ok => result.success(ok)
          )
      else
        val decoded = RawJson.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(ApiFailure.Status(xhr.status), decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(ApiFailure.Status(0), None))
    xhr.send(file)
    result.future

  // ── merging another collection's bundle (the /merge review screen) ──────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def mergePlan(req: MergePlan.Request): Future[MergePlan] = call(Api.mergePlan)(req)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def mergeSession(bundle: Sha256): Future[MergeSession] = call(Api.mergeSession)(Api.Merge.Id(bundle.value))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def putMergeSession(bundle: Sha256, r: MergeResolutions): Future[MergeSession] =
    call(Api.putMergeSession)((Api.Merge.Id(bundle.value), r))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def parseResolutions(yaml: Api.Merge.Id): Future[MergeResolutions] = call(Api.parseResolutions)(yaml)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def mergeApply(req: MergeApplyRequest): Future[MergeApplyJob] = call(Api.mergeApply)(req)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def mergeApplyStatus(job: MergeApplyJob.Job): Future[MergeApplyJob] =
    call(Api.mergeApplyStatus)(Api.Merge.Id(job.value))

  /** `POST /api/bundle/upload`, with upload progress — a bundle can be large; through `XMLHttpRequest` for the same
    * reason as [[ingest]].
    */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
        RawJson
          .decode[ExchangeBundle.Upload](body)
          .fold(
            e =>
              result.failure(
                ApiFailure(
                  ApiFailure.Status(xhr.status),
                  Api.Error(Problem.Unclassified(Legacy.Text(s"could not read the server's answer: $e")))
                )
              ),
            ok => result.success(ok)
          )
      else
        val decoded = RawJson.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(ApiFailure.Status(xhr.status), decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(ApiFailure.Status(0), None))
    xhr.send(file)
    result.future

  // ── process managers (docs/research-process-managers.md) ─────────────────────────────────────────

  def managers: Future[List[ManagerInfo]] = call(ManagersApi.list)(())
  def autopilot: Future[AutopilotStatus] = call(AutopilotApi.autopilot)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setAutopilot(on: AutopilotSet.On): Future[AutopilotStatus] =
    call(AutopilotApi.setAutopilot)(AutopilotSet(on, Some(AutopilotSet.By("web"))))

  /** The autopilot's focus: pin/unpin/priority a goal, or the cap (`max`; no value: the config's). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setFocus(
      action: FocusSet.Action,
      goal: Option[Goal.Slug] = None,
      value: Option[FocusSet.Value] = None
  ): Future[FocusInfo] =
    call(ManagersApi.setFocus)(FocusSet(action, goal, value, Some(FocusSet.By("web"))))

  /** The central scheduler: its status and queue (`limit` waiting items), a live strategy switch, one item's why. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def scheduler(limit: Option[Paging.Limit] = None): Future[SchedulerStatus] =
    call(ManagersApi.scheduler)(limit.map(l => Managers.Scheduler.Limit(l.value)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setScheduler(strategy: SchedulerSet.Strategy): Future[SchedulerStatus] =
    call(ManagersApi.setScheduler)(SchedulerSet(strategy, Some(SchedulerSet.By("web"))))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def schedulerWhy(item: ManagerInfo.Id): Future[WhyInfo] = call(ManagersApi.schedulerWhy)(item)

  /** Drain (nothing new starts while the runs in flight finish) or lift it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def drain(on: DrainSet.On): Future[AutopilotStatus] =
    call(AutopilotApi.drain)(DrainSet(on, Some(DrainSet.By("web"))))

  /** The goals with no progress in `days` days, why, and what unblocks them. */
  def stalls(days: Option[Stall.Limit] = None): Future[Stall.StallReport] = call(ManagersApi.stalls)(days)

  /** Everything waiting for a person, by kind. */
  def approvalDigest: Future[Digest] = call(ManagersApi.digest)(())
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def manager(id: ManagerInfo.Id): Future[ManagerDetail] = call(ManagersApi.get)(id)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def managerUpdate(id: ManagerInfo.Id, in: ManagerUpdate): Future[ManagerInfo] =
    call(ManagersApi.update)((id, in))

  /** pause | resume | stop; `runs` = a pause takes its runs in flight with it. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def managerAction(
      id: ManagerInfo.Id,
      action: ManagersApi.Action,
      runs: Boolean = false
  ): Future[ManagerInfo] =
    call(ManagersApi.action)((id, action, Option.when(runs)(true)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def managerItems(
      id: ManagerInfo.Id,
      status: Option[ItemStatus.Wire],
      offset: Offset,
      limit: Paging.Limit
  ): Future[ItemPage] =
    call(ManagersApi.items)(
      (id, status, Some(ItemPage.Limit(offset.value)), Some(UnreadPage.Offset(limit.value)))
    )
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def managerPatchItem(
      id: ManagerInfo.Id,
      item: ManagerItem.Id,
      p: ItemPatch
  ): Future[ManagerItem] =
    call(ManagersApi.patchItem)((id, ManagerItem.Ref(item.value.toString), p))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def managerAddHint(id: ManagerInfo.Id, in: HintAdd): Future[ManagerHint] =
    call(ManagersApi.addHint)((id, in))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def managerRemoveHint(id: ManagerInfo.Id, hint: ManagerHint.Id): Future[Goal.Plans.Removed] =
    call(ManagersApi.removeHint)((id, hint))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def managerEvents(
      id: ManagerInfo.Id,
      before: Option[EventPage.Before],
      limit: Paging.Limit
  ): Future[EventPage] =
    call(ManagersApi.events)((id, before, Some(EventPage.Limit(limit.value))))

  /** A page button — Review, Extend, Enrich (an article or a goal), Extend a goal (`seed`): the target goes to the
    * manager of its kind, first in line.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def enqueue(kind: EnqueueKind, target: EnqueueRequest.Target): Future[EnqueueResult] =
    call(ManagersApi.enqueue)(EnqueueRequest(kind.wire, target, Some(EnqueueRequest.Priority(10))))

  /** The article window's Review button: the article goes to the review manager, first in line. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def enqueueReview(slug: Article.Slug): Future[EnqueueResult] =
    enqueue(EnqueueKind.Review, EnqueueRequest.Target(slug.value))

  /** A new manager (the New research area form's seeding manager); created paused. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def managerCreate(in: ManagerCreate): Future[ManagerInfo] = call(ManagersApi.create)(in)

  /** A page's HTML fragment, whatever the status (an error page is shown as it is). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
    RawJson.decode[Api.Error](body).toOption

  /** GET `url`: the status and the body, decoded by the browser. */
  private def fetchText(url: String): Future[(Int, String)] =
    import scala.scalajs.js.Thenable.Implicits.*
    unreachable(for
      r <- dom.fetch(url).toFuture
      body <- r.text().toFuture
    yield (r.status, body))

  /** A request's own failure (no response, or its body cut off) as the [[ServerUnreachable]] it is. */
  private def unreachable[A](sent: Future[A]): Future[A] =
    sent.transform(identity, e => ServerUnreachable.of(e))

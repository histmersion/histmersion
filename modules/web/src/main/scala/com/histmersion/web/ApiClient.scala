package com.histmersion.web

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

import com.histmersion.api.{personal, Api, OpsApi, Paging}
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
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Acquire
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.research.Article

object ApiClient:
  type AgentId = AgentId.T
  object AgentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalId = ApprovalId.T
  object ApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleSlug = ArticleSlug.T
  object ArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Board = Board.T
  object Board extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarkId = BookmarkId.T
  object BookmarkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Brief = Brief.T
  object Brief extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetUsd = BudgetUsd.T
  object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bundle = Bundle.T
  object Bundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekey = Citekey.T
  object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Counts = Counts.T
  object Counts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Days = Days.T
  object Days extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Drain = Drain.T
  object Drain extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeKind = EdgeKind.T
  object EdgeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventBefore = EventBefore.T
  object EventBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fetch = Fetch.T
  object Fetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusAction = FocusAction.T
  object FocusAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusLevel = FocusLevel.T
  object FocusLevel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalSlug = GoalSlug.T
  object GoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintId = HintId.T
  object HintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HttpStatus = HttpStatus.T
  object HttpStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IncludeBody = IncludeBody.T
  object IncludeBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemId = ItemId.T
  object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemStatus = ItemStatus.T
  object ItemStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobId = JobId.T
  object JobId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRef = LinkRef.T
  object LinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerAction = ManagerAction.T
  object ManagerAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Model = Model.T
  object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteId = NoteId.T
  object NoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Offline = Offline.T
  object Offline extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type On = On.T
  object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pool = Pool.T
  object Pool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueItemId = QueueItemId.T
  object QueueItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueName = QueueName.T
  object QueueName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunId = RunId.T
  object RunId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Runs = Runs.T
  object Runs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeenAt = SeenAt.T
  object SeenAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowKind = ShowKind.T
  object ShowKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Since = Since.T
  object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Strategy = Strategy.T
  object Strategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YamlText = YamlText.T
  object YamlText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SettingsPrefix = SettingsPrefix.T
  object SettingsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerUpdateId = ManagerUpdateId.T
  object ManagerUpdateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerActionId = ManagerActionId.T
  object ManagerActionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerItemsId = ManagerItemsId.T
  object ManagerItemsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPatchItemId = ManagerPatchItemId.T
  object ManagerPatchItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerAddHintId = ManagerAddHintId.T
  object ManagerAddHintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerRemoveHintId = ManagerRemoveHintId.T
  object ManagerRemoveHintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerEventsId = ManagerEventsId.T
  object ManagerEventsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchHtmlUrl = _root_.com.histmersion.web.ApiClient.Url
  val FetchHtmlUrl: _root_.com.histmersion.web.ApiClient.Url.type = _root_.com.histmersion.web.ApiClient.Url

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
        case _ if code == 401 || code == 403 => Future.failed(refuse(ApiFailure.of(code, None)))
        case DecodeResult.Value(Right(o))    => Future.successful(o)
        case DecodeResult.Value(Left(e))     => Future.failed(refuse(ApiFailure(code, e)))
        case other                           =>
          Future.failed(ApiFailure(code, Api.Error(s"could not read the server's answer (HTTP $code): $other")))
    }

  /** The URL template of a layer's tiles (`{z}`, `{x}`, `{y}` for the map to fill in): `q`'s request as the tile
    * endpoint builds it, the tile given as placeholder numbers no tile has, swapped for the template's markers.
    * Absolute: the map's workers fetch it.
    */
  def tileTemplate(q: TilesApi.TileQuery): com.histmersion.web.ApiClient.Url =
    val req = interp
      .toRequest(TilesApi.tile, None)
      .apply(q.modify(_.z).setTo(TilePlaceholder.Z).modify(_.x).setTo(TilePlaceholder.X).modify(_.y).setTo(TilePlaceholder.Y))
    dom.window.location.origin + TilePlaceholder.fill(req.uri.toString)

  def config(): Future[UiConfig] = call(Api.config)(())
  def sources(f: Filter): Future[SearchResult] = call(Api.sources)(f)
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]] = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram] = call(Api.aggregate)(q)
  def facets(field: com.histmersion.api.Api.Facets.Field, prefix: Option[com.histmersion.api.Api.Facets.Prefix], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def resolveId(id: com.histmersion.web.ApiClient.Id): Future[ResolvedId] = call(Api.resolveId)(id)
  def getSource(ck: com.histmersion.web.ApiClient.Citekey, body: com.histmersion.web.ApiClient.IncludeBody = true): Future[Source.WithFindings] =
    call(Api.getSource)((ck, body))

  /** The articles that cite a source (`GET /api/sources/{citekey}/articles`). */
  def sourceArticles(ck: com.histmersion.web.ApiClient.Citekey): Future[List[Backlink]] = call(Api.sourceArticles)(ck)

  /** `POST /api/acquire/ingest`: a DOI or arXiv id brought in ([[library.DoiIngest]] builds the request) — by the legal
    * open-access chain only, whatever the request says: the server may be configured to allow fetchers without the
    * rights holder's authorisation, and the web UI never sets them off.
    */
  def ingestId(req: Ingest.Request): Future[Ingest.Report] =
    call(AcquireApi.ingest)(req.modify(_.legalOnly).setTo(true))
  def neighbors(ck: com.histmersion.web.ApiClient.Citekey, kinds: List[com.histmersion.web.ApiClient.EdgeKind] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def articles(q: Api.ArticleQuery): Future[Article.List] = call(Api.articles)(q)
  def getArticle(slug: com.histmersion.web.ApiClient.ArticleSlug, brief: com.histmersion.web.ApiClient.Brief = false): Future[Article.View] = call(Api.getArticle)((slug, brief))
  def places(q: Option[com.histmersion.api.Api.Search.Text], asOf: Option[com.histmersion.api.Api.AggregateQuery.AsOf], limit: Option[com.histmersion.api.Paging.Limit]): Future[List[Place.Hit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: com.histmersion.api.catalogue.Place.Id, asOf: Option[com.histmersion.api.Api.AggregateQuery.AsOf], counts: com.histmersion.web.ApiClient.Counts = false): Future[PlaceDetail] =
    call(Api.place)((id, asOf, Option.when(counts)(true)))
  def about(): Future[About] = call(Api.about)(())
  def basemap(): Future[Basemap.Status] = call(BasemapApi.status)(())
  def basemapFetch(q: Basemap.FetchQuery): Future[JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: Basemap.RemoveQuery): Future[Basemap.Removed] = call(BasemapApi.remove)(q)
  def job(id: JobInfo.Id): Future[JobInfo] = call(Api.job)(id.value)
  def uiCounters(): Future[UiNotices.Counters] = call(UiApi.counters)(())
  def markSeen(board: com.histmersion.web.ApiClient.Board, at: com.histmersion.web.ApiClient.SeenAt): Future[UiNotices.Counters] =
    call(UiApi.markSeen)((board, UiNotices.SeenMark(Some(at))))

  def queueList(list: com.histmersion.web.ApiClient.QueueName, all: com.histmersion.web.ApiClient.All = false): Future[QueueListing] =
    call(OpsApi.queueList)((list, all, None, None))
  def queueForGoal(list: com.histmersion.web.ApiClient.QueueName, goal: com.histmersion.web.ApiClient.GoalSlug, all: com.histmersion.web.ApiClient.All = false): Future[QueueListing] =
    call(OpsApi.queueList)((list, all, Some(goal), None))
  def queueOfJob(list: com.histmersion.web.ApiClient.QueueName, job: com.histmersion.web.ApiClient.JobId): Future[QueueListing] =
    call(OpsApi.queueList)((list, true, None, Some(job)))
  def activity(
      show: Option[com.histmersion.web.ApiClient.ShowKind],
      since: Option[com.histmersion.web.ApiClient.Since],
      goal: Option[com.histmersion.web.ApiClient.GoalSlug],
      limit: Option[com.histmersion.api.Paging.Limit],
      before: Option[com.histmersion.api.Paging.Cursor]
  ): Future[Activity.Page] = call(Api.activity)((show, since, goal, limit, before))
  def jobControl(id: com.histmersion.web.ApiClient.JobId, action: com.histmersion.api.Api.JobControl.Action): Future[JobInfo] = call(Api.jobControl)((id, action))

  // ── a person's bookmarks, notes and read marks ───────────────────────────────────────────────────

  def bookmarks(): Future[List[personal.Bookmark]] =
    call(personal.PersonalApi.bookmarks)((None, None, Some(personal.PersonalIds.MaxLimit)))
  def addBookmark(b: com.histmersion.api.personal.Bookmark.New): Future[personal.Bookmark] =
    call(personal.PersonalApi.addBookmark)(b)
  def editBookmark(id: com.histmersion.web.ApiClient.BookmarkId, e: com.histmersion.api.personal.Bookmark.Edit): Future[personal.Bookmark] =
    call(personal.PersonalApi.editBookmark)((id, e))
  def removeBookmark(id: com.histmersion.web.ApiClient.BookmarkId): Future[personal.Bookmark] = call(personal.PersonalApi.removeBookmark)(id)
  def notes(): Future[List[personal.Note]] =
    call(personal.PersonalApi.notes)((None, None, None, Some(personal.PersonalIds.MaxLimit)))
  def addNote(n: com.histmersion.api.personal.Note.New): Future[personal.Note] = call(personal.PersonalApi.addNote)(n)
  def editNote(id: com.histmersion.web.ApiClient.NoteId, e: com.histmersion.api.personal.Note.Edit): Future[personal.Note] =
    call(personal.PersonalApi.editNote)((id, e))
  def removeNote(id: com.histmersion.web.ApiClient.NoteId): Future[personal.Note] = call(personal.PersonalApi.removeNote)(id)
  def readMarks(): Future[List[personal.ReadMark]] =
    call(personal.PersonalApi.reading)((None, Some("all"), Some(personal.PersonalIds.MaxLimit)))
  def setRead(m: com.histmersion.api.personal.ReadMark.Set): Future[personal.ReadMark] =
    call(personal.PersonalApi.setRead)(m)
  def opened(o: personal.ReadOpened): Future[personal.ReadMark] = call(personal.PersonalApi.opened)(o)
  def unread(kind: com.histmersion.web.ApiClient.Kind, limit: com.histmersion.api.Paging.Limit, offset: com.histmersion.api.Offset): Future[personal.UnreadPage] =
    call(personal.PersonalApi.unread)((Some(kind), Some(limit), Some(offset)))

  // ── Settings and the setup ───────────────────────────────────────────────────────────────────────

  def settings(prefix: Option[com.histmersion.web.ApiClient.SettingsPrefix] = None): Future[ConfigEdit.Page] = call(ConfigEditApi.get)(prefix)
  def writeSettings(w: ConfigEdit.Write): Future[ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer(): Future[ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backupNow(): Future[Backup.Status] = call(Api.backup)(())
  def backups(): Future[List[Backup.Info]] = call(Api.backups)(())
  def initData(fetch: com.histmersion.web.ApiClient.Fetch): Future[JobInfo] = call(Api.init)(fetch)
  def placeSources(): Future[List[Gazetteer.SourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: com.histmersion.api.Api.ExternalPlaces.Source): Future[JobInfo] = call(Api.loadPlaces)((source, true))
  def eventCatalogues(): Future[List[CatalogProviderInfo]] = call(EventsApi.catalogues)(())
  def importEvents(provider: com.histmersion.web.ApiClient.Provider): Future[JobInfo] =
    call(EventsApi.importEvents)(EventImportRequest(provider, dryRun = false))
  def mediaProviders(): Future[List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  def importMedia(provider: com.histmersion.web.ApiClient.Provider): Future[JobInfo] =
    call(Api.mediaImportJob)(MediaImportRequest(provider, dryRun = false))
  def setAgentMode(mode: AgentMode): Future[AutopilotStatus] =
    call(AutopilotApi.setMode)(AgentMode.ModeSet(mode.wire, Some("web")))
  def acquireRecheck(in: Acquire.Checks.RecheckRequest): Future[Acquire.Checks.RecheckResult] =
    call(com.histmersion.api.library.AcquisitionWorkApi.acquireRecheck)(in)
  def queuePatch(id: com.histmersion.web.ApiClient.QueueItemId, p: Queue.QueuePatch): Future[Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueAdd(list: com.histmersion.web.ApiClient.QueueName, in: Queue.QueueAdd): Future[Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queueDelete(id: com.histmersion.web.ApiClient.QueueItemId): Future[Queue.QueueItem] =
    call(OpsApi.queueDelete)(id)

  def graph(citekey: com.histmersion.web.ApiClient.Citekey, depth: Option[com.histmersion.web.ApiClient.Depth] = None): Future[Source.Graph] =
    call(Api.graph)((citekey, depth))

  def periods(
      q: Option[com.histmersion.api.Api.Search.Text] = None,
      region: Option[com.histmersion.web.ApiClient.Region] = None,
      from: Option[com.histmersion.api.catalogue.Year] = None,
      to: Option[com.histmersion.api.catalogue.Year] = None
  ): Future[List[HistoricalPeriod]] =
    call(Api.periods)((q, region, from, to))

  /** The places' GeoJSON — megabytes over a whole range, read as text by the browser ([[text]]). */
  def placesGeo(q: Api.PlacesGeoQuery): Future[com.histmersion.api.Api.PlacesGeo.Json] = text(Api.placesGeo)(q)

  def mediaGeo(q: Api.MediaGeoQuery): Future[List[MediaGeoHit]] = call(Api.mediaGeo)(q)
  def mediaFacets(): Future[MediaFacets] = call(Api.mediaFacets)(())
  def getEvent(id: com.histmersion.api.catalogue.Event.Id): Future[EventDetail] = call(Api.getEvent)(id)

  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)
  def eventsGeo(q: Api.EventQuery): Future[EventGeo.List] = call(Api.eventsGeo)(q)

  /** The literature search for `q`, works not in the corpus yet (an event card's lead, #1449). */
  def discover(q: com.histmersion.api.Api.Search.Text, limit: com.histmersion.api.Paging.Limit): Future[DiscoverResult] =
    call(AcquireApi.discover)((q, None, None, Some(limit), true))

  /** One page of the jobs that match `f` ([[Paging]]): `before` = older than that job, `upTo` = down to it. */
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[com.histmersion.api.Paging.Limit],
      before: Option[com.histmersion.api.Paging.Cursor] = None,
      upTo: Option[com.histmersion.api.Paging.Cursor] = None
  ): Future[(List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit, before, upTo))

  def patchSource(ck: com.histmersion.web.ApiClient.Citekey, p: Source.Patch): Future[Source.WithFindings] = call(Api.patchSource)((ck, p, false))

  // ── embedded agents ──────────────────────────────────────────────────────────────────────────────

  def agentRuns(
      f: ListFilters.RunFilter,
      limit: Option[com.histmersion.api.Paging.Limit],
      before: Option[com.histmersion.api.Paging.Cursor] = None,
      upTo: Option[com.histmersion.api.Paging.Cursor] = None
  ): Future[(List[Agents.RunInfo], Paging.Page)] =
    call(AgentsApi.runs)((f, limit, before, upTo))
  def startRun(req: Agents.RunRequest): Future[Agents.RunInfo] = call(AgentsApi.startRun)(req)
  def agentRun(id: com.histmersion.web.ApiClient.RunId): Future[Agents.RunDetail] = call(AgentsApi.run)(id)

  /** pause | resume | cancel; a resume may raise the run's cost cap (the way out of a budget pause). */
  def agentControl(id: com.histmersion.web.ApiClient.AgentId, action: com.histmersion.api.Api.JobControl.Action, budgetUsd: Option[com.histmersion.web.ApiClient.BudgetUsd] = None): Future[Agents.RunInfo] =
    call(AgentsApi.control)((id, action, budgetUsd, None))
  def agentModels: Future[Agents.ModelsInfo] = call(AgentsApi.models)(())
  def setChain(role: com.histmersion.web.ApiClient.Role, models: List[com.histmersion.web.ApiClient.Model]): Future[ModelChains.RoleChain] =
    call(AgentsApi.setChain)((role, ModelChains.ChainSet(models)))
  def resetChain(role: com.histmersion.web.ApiClient.Role): Future[ModelChains.RoleChain] = call(AgentsApi.resetChain)(role)
  def setAllowance(in: ModelChains.AllowanceSet): Future[ModelChains.AllowanceStatus] = call(AgentsApi.setAllowance)(in)
  def removeAllowance(target: com.histmersion.web.ApiClient.Target): Future[Goal.Plans.Removed] = call(AgentsApi.removeAllowance)(target)
  def agentDoctor(offline: com.histmersion.web.ApiClient.Offline): Future[Agents.DoctorReport] = call(AgentsApi.doctor)(Some(offline))
  def agentQuota: Future[PlanQuota.QuotaInfo] = call(AgentsApi.quota)(())
  def setPool(pool: com.histmersion.web.ApiClient.Pool, in: PlanQuota.PoolSet): Future[PlanQuota.PoolStatus] = call(AgentsApi.setPool)((pool, in))
  def refreshQuota(pool: com.histmersion.web.ApiClient.Pool): Future[PlanQuota.QuotaRefreshed] =
    call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(Some(pool)))
  def approvals(
      f: ListFilters.ApprovalFilter,
      limit: Option[com.histmersion.api.Paging.Limit],
      before: Option[com.histmersion.api.Paging.Cursor] = None,
      upTo: Option[com.histmersion.api.Paging.Cursor] = None
  ): Future[(List[Agents.ApprovalInfo], Paging.Page)] =
    call(AgentsApi.approvals)((f, limit, before, upTo))
  def decide(id: com.histmersion.web.ApiClient.ApprovalId, d: Agents.Decision, note: Option[com.histmersion.web.ApiClient.Note]): Future[Agents.ApprovalInfo] =
    call(AgentsApi.decide)((id, d.wire, Agents.DecisionRequest(note)))

  // ── goals: the list, one goal's view, its links and schedules ───────────────────────────────────

  def goals(limit: Option[com.histmersion.api.Paging.Limit]): Future[Goal.List] = call(Api.goals)(Api.GoalQuery(None, limit, None))
  def goalView(slug: com.histmersion.web.ApiClient.GoalSlug): Future[Goal.Plans.GoalView] = call(AgentsApi.goalView)(slug)
  def getGoal(slug: com.histmersion.web.ApiClient.GoalSlug): Future[Goal] = call(Api.getGoal)(slug)

  /** Create (or REPLACE) a goal — the New research area form checks the slug is free first. */
  def putGoal(slug: com.histmersion.web.ApiClient.GoalSlug, in: Goal.Put): Future[Goal] = call(Api.putGoal)((slug, in))
  def addGoalLink(slug: com.histmersion.web.ApiClient.GoalSlug, in: Goal.Plans.LinkAdd): Future[Goal.Plans.GoalLink] =
    call(AgentsApi.addGoalLink)((slug, in))
  def removeGoalLink(slug: com.histmersion.web.ApiClient.GoalSlug, kind: Goal.Plans.LinkKind, ref: com.histmersion.web.ApiClient.LinkRef): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeGoalLink)((slug, kind.wire, ref))
  def setSchedule(
      slug: com.histmersion.web.ApiClient.GoalSlug,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet
  ): Future[Goal.Plans.GoalSchedule] =
    call(AgentsApi.setSchedule)((slug, kind.wire, in))
  def removeSchedule(slug: com.histmersion.web.ApiClient.GoalSlug, kind: Goal.Plans.ScheduleKind): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeSchedule)((slug, kind.wire))

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
              result.failure(ApiFailure(xhr.status, Api.Error(s"could not read the server's answer: ${e.getMessage}"))),
            ok => result.success(ok)
          )
      else
        val decoded = io.circe.parser.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  // ── merging another collection's bundle (the /merge review screen) ──────────────────────────────

  def mergePlan(req: MergePlan.Request): Future[MergePlan] = call(Api.mergePlan)(req)
  def mergeSession(bundle: com.histmersion.web.ApiClient.Bundle): Future[MergeSession] = call(Api.mergeSession)(bundle)
  def putMergeSession(bundle: com.histmersion.web.ApiClient.Bundle, r: MergeResolutions): Future[MergeSession] =
    call(Api.putMergeSession)((bundle, r))
  def parseResolutions(yaml: com.histmersion.web.ApiClient.YamlText): Future[MergeResolutions] = call(Api.parseResolutions)(yaml)
  def mergeApply(req: MergeApplyRequest): Future[MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: com.histmersion.web.ApiClient.JobId): Future[MergeApplyJob] = call(Api.mergeApplyStatus)(job)

  /** `POST /api/bundle/upload`, with upload progress — a bundle can be large; through `XMLHttpRequest` for the same
    * reason as [[ingest]].
    */
  def uploadBundle(file: dom.File, progress: Double => Unit): Future[Bundle.Upload] =
    val req = authorize(interp.toRequest(Api.uploadBundle, None).apply(file))
    val result = Promise[Bundle.Upload]()
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
          .decode[Bundle.Upload](body)
          .fold(
            e =>
              result.failure(ApiFailure(xhr.status, Api.Error(s"could not read the server's answer: ${e.getMessage}"))),
            ok => result.success(ok)
          )
      else
        val decoded = io.circe.parser.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  // ── process managers (docs/research-process-managers.md) ─────────────────────────────────────────

  def managers: Future[List[Managers.ManagerInfo]] = call(ManagersApi.list)(())
  def autopilot: Future[AutopilotStatus] = call(AutopilotApi.autopilot)(())
  def setAutopilot(on: com.histmersion.web.ApiClient.On): Future[AutopilotStatus] =
    call(AutopilotApi.setAutopilot)(Autopilot.AutopilotSet(on, Some("web")))

  /** The autopilot's focus: pin/unpin/priority a goal, or the cap (`max`; no value: the config's). */
  def setFocus(action: com.histmersion.web.ApiClient.FocusAction, goal: Option[com.histmersion.web.ApiClient.GoalSlug] = None, value: Option[com.histmersion.web.ApiClient.FocusLevel] = None): Future[Autopilot.FocusInfo] =
    call(ManagersApi.setFocus)(Autopilot.FocusSet(action, goal, value, Some("web")))

  /** The central scheduler: its status and queue (`limit` waiting items), a live strategy switch, one item's why. */
  def scheduler(limit: Option[com.histmersion.api.Paging.Limit] = None): Future[AgentScheduler.SchedulerStatus] = call(ManagersApi.scheduler)(limit)
  def setScheduler(strategy: com.histmersion.web.ApiClient.Strategy): Future[AgentScheduler.SchedulerStatus] =
    call(ManagersApi.setScheduler)(AgentScheduler.SchedulerSet(strategy, Some("web")))
  def schedulerWhy(item: com.histmersion.web.ApiClient.ItemId): Future[AgentScheduler.WhyInfo] = call(ManagersApi.schedulerWhy)(item)

  /** Drain (nothing new starts while the runs in flight finish) or lift it. */
  def drain(on: com.histmersion.web.ApiClient.Drain): Future[AutopilotStatus] =
    call(AutopilotApi.drain)(Autopilot.DrainSet(on, Some("web")))

  /** The goals with no progress in `days` days, why, and what unblocks them. */
  def stalls(days: Option[com.histmersion.web.ApiClient.Days] = None): Future[Stalls.StallReport] = call(ManagersApi.stalls)(days)

  /** Everything waiting for a person, by kind. */
  def approvalDigest: Future[ApprovalDigest.Digest] = call(ManagersApi.digest)(())
  def manager(id: com.histmersion.web.ApiClient.ManagerId): Future[Managers.ManagerDetail] = call(ManagersApi.get)(id)
  def managerUpdate(id: com.histmersion.web.ApiClient.ManagerUpdateId, in: Managers.ManagerUpdate): Future[Managers.ManagerInfo] =
    call(ManagersApi.update)((id, in))

  /** pause | resume | stop; `runs` = a pause takes its runs in flight with it. */
  def managerAction(id: com.histmersion.web.ApiClient.ManagerActionId, action: com.histmersion.web.ApiClient.ManagerAction, runs: com.histmersion.web.ApiClient.Runs = false): Future[Managers.ManagerInfo] =
    call(ManagersApi.action)((id, action, Option.when(runs)(true)))
  def managerItems(id: com.histmersion.web.ApiClient.ManagerItemsId, status: Option[com.histmersion.web.ApiClient.ItemStatus], offset: com.histmersion.api.Offset, limit: com.histmersion.api.Paging.Limit): Future[Managers.ItemPage] =
    call(ManagersApi.items)((id, status, Some(offset), Some(limit)))
  def managerPatchItem(id: com.histmersion.web.ApiClient.ManagerPatchItemId, item: com.histmersion.web.ApiClient.ItemId, p: Managers.ItemPatch): Future[Managers.ManagerItem] =
    call(ManagersApi.patchItem)((id, item, p))
  def managerAddHint(id: com.histmersion.web.ApiClient.ManagerAddHintId, in: Managers.HintAdd): Future[Managers.ManagerHint] =
    call(ManagersApi.addHint)((id, in))
  def managerRemoveHint(id: com.histmersion.web.ApiClient.ManagerRemoveHintId, hint: com.histmersion.web.ApiClient.HintId): Future[Goal.Plans.Removed] = call(ManagersApi.removeHint)((id, hint))
  def managerEvents(id: com.histmersion.web.ApiClient.ManagerEventsId, before: Option[com.histmersion.web.ApiClient.EventBefore], limit: com.histmersion.api.Paging.Limit): Future[Managers.EventPage] =
    call(ManagersApi.events)((id, before, Some(limit)))

  /** A page button — Review, Extend, Enrich (an article or a goal), Extend a goal (`seed`): the target goes to the
    * manager of its kind, first in line.
    */
  def enqueue(kind: Managers.EnqueueKind, target: com.histmersion.web.ApiClient.Target): Future[Managers.EnqueueResult] =
    call(ManagersApi.enqueue)(Managers.EnqueueRequest(kind.wire, target, Some(10)))

  /** The article window's Review button: the article goes to the review manager, first in line. */
  def enqueueReview(slug: com.histmersion.web.ApiClient.ArticleSlug): Future[Managers.EnqueueResult] = enqueue(Managers.EnqueueKind.Review, slug)

  /** A new manager (the New research area form's seeding manager); created paused. */
  def managerCreate(in: Managers.ManagerCreate): Future[Managers.ManagerInfo] = call(ManagersApi.create)(in)

  /** A page's HTML fragment, whatever the status (an error page is shown as it is). */
  def fetchHtml(url: com.histmersion.web.ApiClient.FetchHtmlUrl): Future[String] = fetchText(url).map(_._2)

  /** A text answer of `ep` read by the BROWSER (#1949): sttp's fetch backend reads a body as bytes and decodes them in
    * Scala.js, a byte at a time — tens of ms per MB, on the page's one thread, for a whole range's 7 MB of boundaries.
    * `Response.text()` decodes natively. The request is still the endpoint's own (its path and query, from the same
    * Tapir definition); a GET, so no key.
    */
  private def text[I](ep: PublicEndpoint[I, Api.Error, String, Any])(in: I): Future[String] =
    fetchText(interp.toRequest(ep, None).apply(in).uri.toString).flatMap { (status, body) =>
      if status >= 200 && status < 300 then Future.successful(body)
      else Future.failed(ApiFailure.of(status, decodeError(body)))
    }

  private def decodeError(body: String): Option[Api.Error] =
    io.circe.parser.decode[Api.Error](body).toOption

  /** GET `url`: the status and the body, decoded by the browser. */
  private def fetchText(url: com.histmersion.web.ApiClient.Url): Future[(com.histmersion.web.ApiClient.HttpStatus, String)] =
    import scala.scalajs.js.Thenable.Implicits.*
    for
      r <- dom.fetch(url).toFuture
      body <- r.text().toFuture
    yield (r.status, body)

package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.airstream.core.Transaction

import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, GeoPick}
import com.histmersion.web.map.{MapMotion, MotionPref}
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import scala.util.Try
import com.histmersion.api.automation.Agents
import com.histmersion.api.catalogue.Period
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.Filter

object UiState:
  type BboxCoordinate = BboxCoordinate.T
  object BboxCoordinate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangeSeq = ChangeSeq.T
  object ChangeSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterTerm = FilterTerm.T
  object FilterTerm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HashParamName = HashParamName.T
  object HashParamName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HashParamText = HashParamText.T
  object HashParamText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceIdList = PlaceIdList.T
  object PlaceIdList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReloadDelay = ReloadDelay.T
  object ReloadDelay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlHash = UrlHash.T
  object UrlHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A pending write, built with `field := value` and committed by [[set]]. */
  type RestoreLocalOnName = RestoreLocalOnName.T
  object RestoreLocalOnName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SetLayerOn = SetLayerOn.T
  object SetLayerOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviewChange = PreviewChange.T
  object PreviewChange extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviewMs = PreviewMs.T
  object PreviewMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReloadDelayNormalMs = ReloadDelayNormalMs.T
  object ReloadDelayNormalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithPreviewKey = WithPreviewKey.T
  object WithPreviewKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type High = High.T
  object High extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangeSignal = ChangeSignal.T
  object ChangeSignal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignalResult = SignalResult.T
  object SignalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BumpByPreview = BumpByPreview.T
  object BumpByPreview extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MirrorHash = MirrorHash.T
  object MirrorHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPush = SetPush.T
  object SetPush extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggledPlaceIds = ToggledPlaceIds.T
  object ToggledPlaceIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggledPlaceId = ToggledPlaceId.T
  object ToggledPlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TogglePlaceId = _root_.com.histmersion.web.UiState.ToggledPlaceId
  val TogglePlaceId: _root_.com.histmersion.web.UiState.ToggledPlaceId.type = _root_.com.histmersion.web.UiState.ToggledPlaceId
  type ToFilterExcept = ToFilterExcept.T
  object ToFilterExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveChipKey = RemoveChipKey.T
  object RemoveChipKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteHashPush = _root_.com.histmersion.web.UiState.NavigatePush
  val WriteHashPush: _root_.com.histmersion.web.UiState.NavigatePush.type = _root_.com.histmersion.web.UiState.NavigatePush
  type NavigatePathAndSearch = NavigatePathAndSearch.T
  object NavigatePathAndSearch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NavigatePush = NavigatePush.T
  object NavigatePush extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadHashHash = _root_.com.histmersion.web.UiState.UrlHash
  val LoadHashHash: _root_.com.histmersion.web.UiState.UrlHash.type = _root_.com.histmersion.web.UiState.UrlHash
  type AddHashHash = _root_.com.histmersion.web.UiState.UrlHash
  val AddHashHash: _root_.com.histmersion.web.UiState.UrlHash.type = _root_.com.histmersion.web.UiState.UrlHash

  final class Assign(private[web] val run: () => Unit)

  import FieldCodec.given

  // ── the fields ────────────────────────────────────────────────────────────────────────────────
  val q = Field[Option[String]]("q", "q", None, Persist.Hash, Role.Filter, Some("search"))
  val tags = Field[List[String]]("tags", "tag", Nil, Persist.Hash, Role.Filter, Some("tag"))
  val authors = Field[List[String]]("authors", "author", Nil, Persist.Hash, Role.Filter, Some("author"))
  val places = Field[List[String]]("places", "place", Nil, Persist.Hash, Role.Filter, Some("place"))
  val placeIds = Field[List[String]]("placeIds", "placeId", Nil, Persist.Hash, Role.Filter, Some("place"))
  val topics = Field[List[String]]("topics", "topic", Nil, Persist.Hash, Role.Filter, Some("topic"))
  val status = Field[List[String]]("status", "status", Nil, Persist.Hash, Role.Filter, Some("status"))
  val yearFrom = Field[Option[Long]]("yearFrom", "yearFrom", None, Persist.Hash, Role.Filter, Some("published from"))
  val yearTo = Field[Option[Long]]("yearTo", "yearTo", None, Persist.Hash, Role.Filter, Some("published to"))
  val sort = Field[Option[String]]("sort", "sort", None, Persist.Hash, Role.Filter, Some("sort"), chip = false)

  /** The title quick-filter above the tabs: narrows every list by its items' own title (a source's title, an article's
    * title, an event's label). A filter the user typed, so "clear all" clears it; not a chip, because the input showing
    * it is always on screen.
    */
  val title = Field[Option[String]]("title", "title", None, Persist.Hash, Role.Filter, Some("title"), chip = false)

  /** A research goal's sources only (its goal page's "sources brought in", in Library or on the map). */
  val goal = Field[Option[String]]("goal", "goal", None, Persist.Hash, Role.Filter, Some("goal"))
  val from = Field[Option[Long]]("from", "from", None, Persist.Hash, Role.Span)
  val to = Field[Option[Long]]("to", "to", None, Persist.Hash, Role.Span)
  val bbox = Field[Option[String]]("bbox", "bbox", None, Persist.Hash, Role.Span)
  val inView = Field[Boolean]("inView", "inView", false, Persist.Hash, Role.Span)

  /** The open windows — written by [[WindowManager]]. */
  val open = Field[List[WindowKey]]("open", "open", Nil, Persist.Hash, Role.View)

  /** Library's order of its sources ([[library.LibrarySort]]): a view of the list, not a filter. */
  val librarySort = Field[Option[String]]("librarySort", "lsort", None, Persist.Hash, Role.View)
  val tab = Field[Option[String]]("tab", "tab", None, Persist.Hash, Role.View)
  val layers = Field[Set[MapLayer]]("layers", "layers", Set.empty, Persist.Local, Role.View)

  /** The finding markers ("sources") on the map. Not a [[MapLayer]]: it is ON by default, and a browser that already
    * remembers a `layers` set (every one saved before this switch existed) would restore it without the markers. A key
    * of its own that is absent until first toggled keeps them on.
    */
  val sources = Field[Boolean]("sources", "sources", true, Persist.Local, Role.View)
  val baseMap = Field[BaseMap]("baseMap", "baseMap", BaseMap.Standard, Persist.Local, Role.View)

  /** The viewer's options over the vector styles ([[BaseMaps]]): names, their language, contours. Per device. */
  val mapOptions = Field[MapPrefs]("mapOptions", "mapOptions", MapPrefs(), Persist.Local, Role.View)

  /** The media layer's provider and licence filters (#1446): `None` = every one. Per device. */
  val mediaProvider = Field[Option[String]]("mediaProvider", "mediaProvider", None, Persist.Local, Role.View)
  val mediaLicense = Field[Option[String]]("mediaLicense", "mediaLicense", None, Persist.Local, Role.View)

  /** The events layer's filters (#1449): a kind or a group (`natural` | `human`), and the tier (`attested` |
    * `catalogue`); `None` = every one. The sites layer's: one site type. Per device.
    */
  val eventKind = Field[Option[String]]("eventKind", "eventKind", None, Persist.Local, Role.View)
  val eventTier = Field[Option[String]]("eventTier", "eventTier", None, Persist.Local, Role.View)
  val siteType = Field[Option[String]]("siteType", "siteType", None, Persist.Local, Role.View)

  /** "Also show sites (events) with no known date": drawn beneath the dated ones, outlined dashed. Off by default; per
    * device.
    */
  val sitesUndated = Field[Boolean]("sitesUndated", "sitesUndated", false, Persist.Local, Role.View)
  val eventsUndated = Field[Boolean]("eventsUndated", "eventsUndated", false, Persist.Local, Role.View)

  /** How the boundaries layer reads the period ([[GeoPick]]): a snapshot at its middle (the default), start or end, or
    * every state in it with its earliest or latest shape. Sites follow a snapshot's year and the middle otherwise;
    * playback always draws one year. Per device.
    */
  val areaPick = Field[GeoPick]("areaPick", "areaPick", GeoPick.Mid, Persist.Local, Role.View)

  /** The map's motion (#4718): follow the system's reduced-motion setting (the default), always animate, or never. Per
    * device; [[MotionPref]] answers "does the map animate now?" from it.
    */
  val mapMotion = Field[MapMotion]("mapMotion", "mapMotion", MapMotion.System, Persist.Local, Role.View)

  /** The sidebar's bottom sheet in the narrow layout (a per-device preference, never in a link). */
  val sheet = Field[Sheet]("sheet", "sheet", Sheet.Peek, Persist.Local, Role.View)

  /** The jobs window's filters (core `ListFilters.JobFilter`): the state (`None` = every job), the kinds (any of), the
    * start dates and the subject text.
    */
  val jobStatus = Field[Option[JobState]]("jobStatus", "jobs", None, Persist.Hash, Role.View)
  val jobKinds = Field[List[String]]("jobKinds", "jobKind", Nil, Persist.Hash, Role.View)
  val jobSince = Field[Option[String]]("jobSince", "jobSince", None, Persist.Hash, Role.View)
  val jobUntil = Field[Option[String]]("jobUntil", "jobUntil", None, Persist.Hash, Role.View)
  val jobSubject = Field[Option[String]]("jobSubject", "jobSubject", None, Persist.Hash, Role.View)

  /** The agent runs window's filters (`ListFilters.RunFilter`): the state (`None` = every run), the pipelines and
    * origins (any of), one manager, provider, model and goal, the start dates, the target text.
    */
  val runStatus = Field[Option[Agents.RunState]]("runStatus", "runs", None, Persist.Hash, Role.View)
  val runPipelines = Field[List[String]]("runPipelines", "runPipeline", Nil, Persist.Hash, Role.View)
  val runOrigins = Field[List[String]]("runOrigins", "runOrigin", Nil, Persist.Hash, Role.View)
  val runManager = Field[Option[String]]("runManager", "runManager", None, Persist.Hash, Role.View)
  val runProvider = Field[Option[String]]("runProvider", "runProvider", None, Persist.Hash, Role.View)
  val runModel = Field[Option[String]]("runModel", "runModel", None, Persist.Hash, Role.View)
  val runGoal = Field[Option[String]]("runGoal", "runGoal", None, Persist.Hash, Role.View)
  val runSince = Field[Option[String]]("runSince", "runSince", None, Persist.Hash, Role.View)
  val runUntil = Field[Option[String]]("runUntil", "runUntil", None, Persist.Hash, Role.View)
  val runSubject = Field[Option[String]]("runSubject", "runSubject", None, Persist.Hash, Role.View)

  /** The approvals window's filters (`ListFilters.ApprovalFilter`): the state (`None` = the open ones, `all` = every
    * one), the kinds and deciders (any of), the subject text.
    */
  val approvalState = Field[Option[String]]("approvalState", "apprState", None, Persist.Hash, Role.View)
  val approvalKinds = Field[List[String]]("approvalKinds", "apprKind", Nil, Persist.Hash, Role.View)
  val approvalBy = Field[List[String]]("approvalBy", "apprBy", Nil, Persist.Hash, Role.View)
  val approvalSubject = Field[Option[String]]("approvalSubject", "apprSubject", None, Persist.Hash, Role.View)

  /** The scheduler panel's filters over its queue: the job types and the reasons (any of), the target text. */
  val schedTypes = Field[List[String]]("schedTypes", "schedType", Nil, Persist.Hash, Role.View)
  val schedCodes = Field[List[String]]("schedCodes", "schedWhy", Nil, Persist.Hash, Role.View)
  val schedSubject = Field[Option[String]]("schedSubject", "schedSubject", None, Persist.Hash, Role.View)

  /** The acquisition window also lists the items already done. */
  val acquireDone = Field[Boolean]("acquireDone", "acqDone", false, Persist.Hash, Role.View)

  /** The backlog window also lists the tasks already done. */
  val tasksDone = Field[Boolean]("tasksDone", "tasksDone", false, Persist.Hash, Role.View)

  /** The bundle the merge review screen is reviewing — its sha256 on the server, so a reload (or a link) reopens the
    * same review.
    */
  val mergeBundle = Field[Option[String]]("mergeBundle", "bundle", None, Persist.Hash, Role.View)

  /** The API key writes are sent with, when the server asks for one ([[Auth]]). In this browser's storage only — never
    * in the URL, so a shared link cannot carry it.
    */
  val apiKey = Field[Option[String]]("apiKey", "apiKey", None, Persist.Local, Role.View)

  private val fields: List[Field[?]] =
    List(
      q,
      tags,
      authors,
      places,
      placeIds,
      topics,
      status,
      yearFrom,
      yearTo,
      sort,
      title,
      goal,
      from,
      to,
      bbox,
      inView,
      open,
      librarySort,
      tab,
      layers,
      sources,
      baseMap,
      mapOptions,
      mediaProvider,
      mediaLicense,
      eventKind,
      eventTier,
      siteType,
      sitesUndated,
      eventsUndated,
      areaPick,
      mapMotion,
      sheet,
      jobStatus,
      jobKinds,
      jobSince,
      jobUntil,
      jobSubject,
      runStatus,
      runPipelines,
      runOrigins,
      runManager,
      runProvider,
      runModel,
      runGoal,
      runSince,
      runUntil,
      runSubject,
      approvalState,
      approvalKinds,
      approvalBy,
      approvalSubject,
      schedTypes,
      schedCodes,
      schedSubject,
      acquireDone,
      tasksDone,
      mergeBundle,
      apiKey
    )

  private def hashFields = fields.filter(_.persist == Persist.Hash)
  private def localFields = fields.filter(_.persist == Persist.Local)

  /** One overlay's state, as its toggle shows it. */
  def layerOn(l: MapLayer): Signal[Boolean] = layers.signal.map(_.contains(l))

  def setLayer(l: MapLayer, on: com.histmersion.web.UiState.SetLayerOn): Unit =
    set(layers := (if on then layers.now() + l else layers.now() - l))()

  // ── the hovered suggestion (never a chip, never in the URL) ────────────────────────────────────

  /** A hovered filter suggestion, applied to every query but never to the chips or the URL. */
  case class Preview(key: Preview.Key, value: Preview.Value)

  object Preview:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = com.histmersion.web.UiState.FilterTerm
    val Value: _root_.com.histmersion.web.UiState.FilterTerm.type = _root_.com.histmersion.web.UiState.FilterTerm
  val preview: Var[Option[Preview]] = Var(None)

  def setPreview(p: Option[Preview]): Unit =
    if preview.now() != p then
      preview.set(p)
      bump(byPreview = true)

  // `var`s: whether the newest change was only a hovered suggestion's preview, and how long a reload
  // waits after one (`ui.debounce.preview-ms`, known once `/api/config` has answered)
  private var previewChange: com.histmersion.web.UiState.PreviewChange = false
  private[web] var previewMs: com.histmersion.web.UiState.PreviewMs = 0

  /** How long a reload scheduled NOW waits: `normalMs`, or less after a preview — the pointer resting on a suggestion
    * should see its lists at once, not after the typing debounce.
    */
  def reloadDelay(normalMs: com.histmersion.web.UiState.ReloadDelayNormalMs): com.histmersion.web.UiState.ReloadDelay = if previewChange then math.min(normalMs, previewMs) else normalMs

  private def withPreview(key: com.histmersion.web.UiState.WithPreviewKey, vs: List[com.histmersion.web.UiState.FilterTerm]): List[com.histmersion.web.UiState.FilterTerm] =
    preview.now() match
      case Some(Preview(`key`, v)) if !vs.contains(v) => vs :+ v
      case _                                          => vs

  // ── history sequence (the timeline's back/forward buttons read it) ─────────────────────────────

  private var seq: com.histmersion.web.UiState.ChangeSeq = 0
  private var high: com.histmersion.web.UiState.High = 0

  def currentSeq: com.histmersion.web.UiState.ChangeSeq = seq
  def maxSeq: com.histmersion.web.UiState.ChangeSeq = high

  // ── committing ────────────────────────────────────────────────────────────────────────────────

  private val changeSignal: Var[com.histmersion.web.UiState.ChangeSignal] = Var(0)

  /** Ticks after every committed change: the app reloads what depends on the filter. */
  def signal: Signal[com.histmersion.web.UiState.SignalResult] = changeSignal.signal

  private def bump(byPreview: com.histmersion.web.UiState.BumpByPreview = false): Unit =
    previewChange = byPreview
    changeSignal.update(_ + 1)

  // `var`: tests swap the browser's storage for a Store.Memory
  private[web] var store: Store = Store.Browser
  // `var`: tests run in Node, which has no location/history to mirror the hash into
  private[web] var mirrorHash: com.histmersion.web.UiState.MirrorHash = true

  private val LocalPrefix = "histmersion.ui."

  /** Commit writes: apply them, mirror the hash and the local store, notify.
    *
    * Called from an event handler, `set` runs inside an Airstream transaction, where `Var.set` only SCHEDULES the
    * update: reading the fields right after would mirror the OLD values (a clicked layer was stored as off, and on
    * reload its button and layer came back off). The mirroring is therefore a transaction of its own, queued after the
    * assignments; outside any transaction (tests, start-up) it runs at once, as before.
    */
  def set(assigns: Assign*)(push: com.histmersion.web.UiState.SetPush = false): Unit =
    assigns.foreach(_.run())
    Transaction { _ =>
      writeHash(push)
      writeLocal()
      bump()
    }

  /** Pure: the gazetteer place filter after place `id` is clicked on the map. It becomes THE place (places are any-of,
    * so adding it beside another would widen the lists, not narrow them); when it is already chosen, it is removed and
    * the other chosen places stay.
    */
  def toggledPlace(ids: List[com.histmersion.web.UiState.ToggledPlaceIds], id: com.histmersion.web.UiState.ToggledPlaceId): List[com.histmersion.web.UiState.PlaceIdList] =
    if ids.contains(id) then ids.filterNot(_ == id) else List(id)

  /** Choose or unchoose place `id` (see [[toggledPlace]]); a step in the history, so Back undoes a click on the map.
    * Returns whether it is chosen now.
    */
  def togglePlace(id: com.histmersion.web.UiState.TogglePlaceId): Boolean =
    val next = toggledPlace(placeIds.now(), id)
    set(placeIds := next)(push = true)
    next.contains(id)

  /** Both search boxes share the raw draft, including a space just typed between words. Filter.of normalizes it at the
    * request boundary, not while the controlled input is editing.
    */
  def setSearch(text: com.histmersion.api.Api.Search.Text): Unit = set(q := Option.when(text.nonEmpty)(text))()

  def toFilter(except: Set[com.histmersion.web.UiState.ToFilterExcept] = Set.empty): Filter =
    def keep(keys: String*) = !keys.exists(except)
    val box = if keep("bbox") && inView.now() then bbox.now().flatMap(parseBbox) else None
    val f = Filter.of(
      q = if keep("q") then q.now() else None,
      tags = if keep("tags", "tag") then withPreview("tags", tags.now()) else Nil,
      authors = if keep("authors", "author") then withPreview("authors", authors.now()) else Nil,
      places = (if keep("places", "place") then withPreview("places", places.now()) else Nil) ++
        (if keep("placeIds", "place") then placeIds.now() else Nil),
      topics = if keep("topics", "topic") then withPreview("topics", topics.now()) else Nil,
      status = if keep("status") then withPreview("status", status.now()) else Nil,
      yearFrom = if keep("yearFrom") then yearFrom.now() else None,
      yearTo = if keep("yearTo") then yearTo.now() else None,
      from = if keep("from") then from.now() else None,
      to = if keep("to") then to.now() else None,
      minLon = box.map(_._1),
      minLat = box.map(_._2),
      maxLon = box.map(_._3),
      maxLat = box.map(_._4),
      sort = sort.now(),
      limit = None,
      offset = None,
      title = if keep("title") then title.now() else None,
      goal = if keep("goal") then goal.now() else None
    )
    val p = f.period
    val corrected =
      if p.exists(pp => pp.start > pp.end) then f.modify(_.period).setTo(p.map(pp => Period(pp.end, pp.start)))
      else f
    val y = corrected.years
    if y.exists(yy => yy.start > yy.end) then corrected.modify(_.years).setTo(y.map(yy => Period(yy.end, yy.start)))
    else corrected

  /** What the Articles tab asks for: the global filter, title included — the server lists the articles citing the
    * sources it selects.
    */
  def articleQuery(): Api.ArticleQuery = Api.ArticleQuery(toFilter().modify(_.limit).setTo(ui.Layout.tabFetch))

  /** What the Events tab asks for: the global filter, title included — the server lists the events whose source it
    * selects, in its period.
    */
  def eventQuery(): Api.EventQuery = Api.EventQuery(toFilter().modify(_.limit).setTo(ui.Layout.tabFetch))

  private[web] def parseBbox(s: com.histmersion.api.BBox.Text): Option[(com.histmersion.web.UiState.BboxCoordinate, com.histmersion.web.UiState.BboxCoordinate, com.histmersion.web.UiState.BboxCoordinate, com.histmersion.web.UiState.BboxCoordinate)] =
    s.split(",").flatMap(_.trim.toDoubleOption).toList match
      case a :: b :: c :: d :: Nil => Some((a, b, c, d))
      case _                       => None

  def clearFilters(): Unit =
    fields.filter(_.role == Role.Filter).foreach(_.clear())
    writeHash(push = false)
    bump()

  case class ActiveChip(key: ActiveChip.Key, label: ActiveChip.Label, value: ActiveChip.Value, shown: Option[ActiveChip.Shown] = None)

  object ActiveChip:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = com.histmersion.web.UiState.FilterTerm
    val Value: _root_.com.histmersion.web.UiState.FilterTerm.type = _root_.com.histmersion.web.UiState.FilterTerm
    type Shown = Shown.T
    object Shown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def activeChips(): List[ActiveChip] =
    fields
      .filter(f => f.role == Role.Filter && f.chip)
      .flatMap(f => f.encoded.map(v => ActiveChip(f.key, f.label.getOrElse(f.key), v)))

  def removeChip(key: com.histmersion.web.UiState.RemoveChipKey, value: com.histmersion.web.UiState.FilterTerm): Unit =
    fields.find(_.key == key).foreach(_.remove(value))
    writeHash(push = false)
    bump()

  // ── the URL hash ──────────────────────────────────────────────────────────────────────────────

  private def pairsOf(hash: com.histmersion.web.UiState.UrlHash): Map[com.histmersion.web.UiState.HashParamName, List[com.histmersion.web.UiState.HashParamText]] =
    val params = new dom.URLSearchParams(hash.stripPrefix("#"))
    val out = List.newBuilder[(String, String)]
    params.forEach((value, key) => out += key -> value)
    out.result().groupMap(_._1)(_._2)

  /** The hash for the current state. */
  private[web] def hashString: com.histmersion.web.UiState.UrlHash =
    val params = new dom.URLSearchParams("")
    hashFields.foreach(f => f.encoded.foreach(params.append(f.hashKey, _)))
    params.toString

  private def writeHash(push: com.histmersion.web.UiState.WriteHashPush): Unit =
    if mirrorHash then navigate(window.location.pathname + window.location.search, push)

  /** Go to another page of the web UI (`pathAndSearch`, e.g. `/research/backlog?item=3`): the address changes, the hash
    * state goes along (the filters, the open windows), and with `push` it is a step in the history that Back undoes.
    */
  def navigate(pathAndSearch: com.histmersion.web.UiState.NavigatePathAndSearch, push: com.histmersion.web.UiState.NavigatePush): Unit =
    if mirrorHash then
      val qs = hashString
      val url = pathAndSearch + (if qs.nonEmpty then "#" + qs else "")
      if push then
        seq = { high += 1; high }
        window.history.pushState(js.Dynamic.literal(histmersionSeq = seq), "", url)
      else window.history.replaceState(js.Dynamic.literal(histmersionSeq = seq), "", url)

  /** Load every HASH field from `hash`: a field the hash does not mention goes back to its default. */
  private[web] def loadHash(hash: com.histmersion.web.UiState.LoadHashHash): Unit =
    val m = pairsOf(hash)
    hashFields.foreach(f => f.load(m.getOrElse(f.hashKey, Nil)))

  /** Merge a hash fragment INTO the state — a `/#tag=x` link inside a window adds the tag and keeps every other filter
    * (following it as a navigation replaced them all).
    */
  def addHash(hash: com.histmersion.web.UiState.AddHashHash): Unit =
    val m = pairsOf(hash)
    val touched = hashFields.filter(f => m.contains(f.hashKey))
    if touched.nonEmpty then set(touched.map(f => Assign(() => f.add(m(f.hashKey))))*)()

  // ── local persistence ─────────────────────────────────────────────────────────────────────────

  private def writeLocal(): Unit =
    localFields.foreach(f => store.put(LocalPrefix + f.key, js.JSON.stringify(js.Array(f.encoded*))))

  /** The pre-UiState layer memory (`{overlay, media, sites}` under one key): migrated once, removed. */
  private val LegacyLayersKey = "histmersion.layers"

  /** Whether this browser keeps a value of the [[Persist.Local]] field `f` (a choice once made). */
  private[web] def stored(f: Field[?]): Boolean = store.get(LocalPrefix + f.key).isDefined

  /** Load the [[Persist.Local]] fields from the store. */
  private[web] def restoreLocal(): Unit =
    localFields.foreach { f =>
      store
        .get(LocalPrefix + f.key)
        .foreach(raw => Try(js.JSON.parse(raw).asInstanceOf[js.Array[String]].toList).foreach(f.load))
    }
    store.get(LegacyLayersKey).foreach { raw =>
      if store.get(LocalPrefix + layers.key).isEmpty then
        Try(js.JSON.parse(raw)).foreach { d =>
          def on(name: com.histmersion.web.UiState.RestoreLocalOnName) =
            Try(d.selectDynamic(name).asInstanceOf[Any]).toOption.contains(true)
          layers.set(
            List(MapLayer.Boundaries -> "overlay", MapLayer.Media -> "media", MapLayer.Sites -> "sites").collect {
              case (l, n) if on(n) => l
            }.toSet
          )
          writeLocal()
        }
      store.remove(LegacyLayersKey)
    }

  // ── start-up ──────────────────────────────────────────────────────────────────────────────────

  def init(config: UiConfig): Unit =
    previewMs = config.debounce.previewMs
    ListFilterBar.typingMs = config.debounce.searchMs
    from.withDefault(Some(config.time.defaultFrom))
    to.withDefault(Some(config.time.defaultTo))
    restoreLocal()
    // the map's motion follows the kept choice, now and whenever it changes
    mapMotion.signal.foreach(MotionPref.choose)(using unsafeWindowOwner)
    loadHash(window.location.hash)
    // back/forward: the HASH fields follow the history entry; local preferences (layers, base map)
    // are not history and stay as they are
    window.addEventListener(
      "popstate",
      { (e: dom.PopStateEvent) =>
        val state = e.state
        seq = if state != null then Try(state.asInstanceOf[js.Dynamic].histmersionSeq.asInstanceOf[Int]).getOrElse(0)
        else 0
        loadHash(window.location.hash)
        bump()
      }
    )

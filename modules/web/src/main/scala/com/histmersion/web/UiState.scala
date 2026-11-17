package com.histmersion
package web

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
import com.histmersion.api.catalogue.{Latitude, Longitude, Period, Year}
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.{Filter, Sort}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.instance.DebounceConfig
import com.histmersion.web.ui.Layout

object UiState:
  type ChangeSeq = ChangeSeq.T
  object ChangeSeq extends Newtype[Int, Pure]
  type FilterTerm = FilterTerm.T
  object FilterTerm extends Newtype[String, Pure]
  type ReloadDelay = ReloadDelay.T
  object ReloadDelay extends Newtype[Int, Pure]
  type UrlHash = UrlHash.T
  object UrlHash extends Newtype[String, Pure]

  type ReloadDelayNormalMs = ReloadDelayNormalMs.T
  object ReloadDelayNormalMs extends Newtype[Int, Pure]
  type ToggledPlaceId = ToggledPlaceId.T
  object ToggledPlaceId extends Newtype[String, Pure]
  type TogglePlaceId = ToggledPlaceId
  val TogglePlaceId: ToggledPlaceId.type = ToggledPlaceId
  type ToFilterExcept = ToFilterExcept.T
  object ToFilterExcept extends Newtype[String, Pure]
  type RemoveChipKey = RemoveChipKey.T
  object RemoveChipKey extends Newtype[String, Pure]
  type NavigatePathAndSearch = NavigatePathAndSearch.T
  object NavigatePathAndSearch extends Newtype[String, Pure]
  type NavigatePush = NavigatePush.T
  object NavigatePush extends Newtype[Boolean, Pure]
  type SetPush = NavigatePush
  val SetPush: NavigatePush.type = NavigatePush

  /** A pending write, built with `field := value` and committed by [[set]]. */
  final class Assign(private[web] val run: () => Unit)

  import FieldCodec.given

  // ── the fields ────────────────────────────────────────────────────────────────────────────────
  val q = Field[Option[String]](
    Field.Key("q"),
    Field.HashKey("q"),
    None,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("search"))
  )
  val tags = Field[List[String]](
    Field.Key("tags"),
    Field.HashKey("tag"),
    Nil,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("tag"))
  )
  val authors = Field[List[String]](
    Field.Key("authors"),
    Field.HashKey("author"),
    Nil,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("author"))
  )
  val places = Field[List[String]](
    Field.Key("places"),
    Field.HashKey("place"),
    Nil,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("place"))
  )
  val placeIds = Field[List[String]](
    Field.Key("placeIds"),
    Field.HashKey("placeId"),
    Nil,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("place"))
  )
  val topics = Field[List[String]](
    Field.Key("topics"),
    Field.HashKey("topic"),
    Nil,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("topic"))
  )
  val status = Field[List[String]](
    Field.Key("status"),
    Field.HashKey("status"),
    Nil,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("status"))
  )
  val yearFrom = Field[Option[Long]](
    Field.Key("yearFrom"),
    Field.HashKey("yearFrom"),
    None,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("published from"))
  )
  val yearTo = Field[Option[Long]](
    Field.Key("yearTo"),
    Field.HashKey("yearTo"),
    None,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("published to"))
  )
  val sort = Field[Option[String]](
    Field.Key("sort"),
    Field.HashKey("sort"),
    None,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("sort")),
    chip = Field.Chip(false)
  )

  /** The title quick-filter above the tabs: narrows every list by its items' own title (a source's title, an article's
    * title, an event's label). A filter the user typed, so "clear all" clears it; not a chip, because the input showing
    * it is always on screen.
    */
  val title = Field[Option[String]](
    Field.Key("title"),
    Field.HashKey("title"),
    None,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("title")),
    chip = Field.Chip(false)
  )

  /** A research goal's sources only (its goal page's "sources brought in", in Library or on the map). */
  val goal = Field[Option[String]](
    Field.Key("goal"),
    Field.HashKey("goal"),
    None,
    Persist.Hash,
    Role.Filter,
    Some(Field.Label("goal"))
  )
  val from = Field[Option[Long]](Field.Key("from"), Field.HashKey("from"), None, Persist.Hash, Role.Span)
  val to = Field[Option[Long]](Field.Key("to"), Field.HashKey("to"), None, Persist.Hash, Role.Span)
  val bbox = Field[Option[String]](Field.Key("bbox"), Field.HashKey("bbox"), None, Persist.Hash, Role.Span)
  val inView = Field[Boolean](Field.Key("inView"), Field.HashKey("inView"), false, Persist.Hash, Role.Span)

  /** The open windows — written by [[WindowManager]]. */
  val open = Field[List[WindowKey]](Field.Key("open"), Field.HashKey("open"), Nil, Persist.Hash, Role.View)

  /** Library's order of its sources ([[library.LibrarySort]]): a view of the list, not a filter. */
  val librarySort =
    Field[Option[String]](Field.Key("librarySort"), Field.HashKey("lsort"), None, Persist.Hash, Role.View)
  val tab = Field[Option[String]](Field.Key("tab"), Field.HashKey("tab"), None, Persist.Hash, Role.View)
  val layers = Field[Set[MapLayer]](Field.Key("layers"), Field.HashKey("layers"), Set.empty, Persist.Local, Role.View)

  /** The finding markers ("sources") on the map. Not a [[MapLayer]]: it is ON by default, and a browser that already
    * remembers a `layers` set (every one saved before this switch existed) would restore it without the markers. A key
    * of its own that is absent until first toggled keeps them on.
    */
  val sources = Field[Boolean](Field.Key("sources"), Field.HashKey("sources"), true, Persist.Local, Role.View)
  val baseMap =
    Field[BaseMap](Field.Key("baseMap"), Field.HashKey("baseMap"), BaseMap.Standard, Persist.Local, Role.View)

  /** The viewer's options over the vector styles ([[BaseMaps]]): names, their language, contours. Per device. */
  val mapOptions =
    Field[MapPrefs](Field.Key("mapOptions"), Field.HashKey("mapOptions"), MapPrefs(), Persist.Local, Role.View)

  /** The media layer's provider and licence filters (#1446): `None` = every one. Per device. */
  val mediaProvider =
    Field[Option[String]](Field.Key("mediaProvider"), Field.HashKey("mediaProvider"), None, Persist.Local, Role.View)
  val mediaLicense =
    Field[Option[String]](Field.Key("mediaLicense"), Field.HashKey("mediaLicense"), None, Persist.Local, Role.View)

  /** The events layer's filters (#1449): a kind or a group (`natural` | `human`), and the tier (`attested` |
    * `catalogue`); `None` = every one. The sites layer's: one site type. Per device.
    */
  val eventKind =
    Field[Option[String]](Field.Key("eventKind"), Field.HashKey("eventKind"), None, Persist.Local, Role.View)
  val eventTier =
    Field[Option[String]](Field.Key("eventTier"), Field.HashKey("eventTier"), None, Persist.Local, Role.View)
  val siteType = Field[Option[String]](Field.Key("siteType"), Field.HashKey("siteType"), None, Persist.Local, Role.View)

  /** "Also show sites (events) with no known date": drawn beneath the dated ones, outlined dashed. Off by default; per
    * device.
    */
  val sitesUndated =
    Field[Boolean](Field.Key("sitesUndated"), Field.HashKey("sitesUndated"), false, Persist.Local, Role.View)
  val eventsUndated =
    Field[Boolean](Field.Key("eventsUndated"), Field.HashKey("eventsUndated"), false, Persist.Local, Role.View)

  /** How the boundaries layer reads the period ([[GeoPick]]): a snapshot at its middle (the default), start or end, or
    * every state in it with its earliest or latest shape. Sites follow a snapshot's year and the middle otherwise;
    * playback always draws one year. Per device.
    */
  val areaPick = Field[GeoPick](Field.Key("areaPick"), Field.HashKey("areaPick"), GeoPick.Mid, Persist.Local, Role.View)

  /** The map's motion (#4718): follow the system's reduced-motion setting (the default), always animate, or never. Per
    * device; [[MotionPref]] answers "does the map animate now?" from it.
    */
  val mapMotion =
    Field[MapMotion](Field.Key("mapMotion"), Field.HashKey("mapMotion"), MapMotion.System, Persist.Local, Role.View)

  /** The sidebar's bottom sheet in the narrow layout (a per-device preference, never in a link). */
  val sheet = Field[Sheet](Field.Key("sheet"), Field.HashKey("sheet"), Sheet.Peek, Persist.Local, Role.View)

  /** The jobs window's filters (core `ListFilters.JobFilter`): the state (`None` = every job), the kinds (any of), the
    * start dates and the subject text.
    */
  val jobStatus = Field[Option[JobState]](Field.Key("jobStatus"), Field.HashKey("jobs"), None, Persist.Hash, Role.View)
  val jobKinds = Field[List[String]](Field.Key("jobKinds"), Field.HashKey("jobKind"), Nil, Persist.Hash, Role.View)
  val jobSince = Field[Option[String]](Field.Key("jobSince"), Field.HashKey("jobSince"), None, Persist.Hash, Role.View)
  val jobUntil = Field[Option[String]](Field.Key("jobUntil"), Field.HashKey("jobUntil"), None, Persist.Hash, Role.View)
  val jobSubject =
    Field[Option[String]](Field.Key("jobSubject"), Field.HashKey("jobSubject"), None, Persist.Hash, Role.View)

  /** The agent runs window's filters (`ListFilters.RunFilter`): the state (`None` = every run), the pipelines and
    * origins (any of), one manager, provider, model and goal, the start dates, the target text.
    */
  val runStatus =
    Field[Option[Agents.RunState]](Field.Key("runStatus"), Field.HashKey("runs"), None, Persist.Hash, Role.View)
  val runPipelines =
    Field[List[String]](Field.Key("runPipelines"), Field.HashKey("runPipeline"), Nil, Persist.Hash, Role.View)
  val runOrigins =
    Field[List[String]](Field.Key("runOrigins"), Field.HashKey("runOrigin"), Nil, Persist.Hash, Role.View)
  val runManager =
    Field[Option[String]](Field.Key("runManager"), Field.HashKey("runManager"), None, Persist.Hash, Role.View)
  val runProvider =
    Field[Option[String]](Field.Key("runProvider"), Field.HashKey("runProvider"), None, Persist.Hash, Role.View)
  val runModel = Field[Option[String]](Field.Key("runModel"), Field.HashKey("runModel"), None, Persist.Hash, Role.View)
  val runGoal = Field[Option[String]](Field.Key("runGoal"), Field.HashKey("runGoal"), None, Persist.Hash, Role.View)
  val runSince = Field[Option[String]](Field.Key("runSince"), Field.HashKey("runSince"), None, Persist.Hash, Role.View)
  val runUntil = Field[Option[String]](Field.Key("runUntil"), Field.HashKey("runUntil"), None, Persist.Hash, Role.View)
  val runSubject =
    Field[Option[String]](Field.Key("runSubject"), Field.HashKey("runSubject"), None, Persist.Hash, Role.View)

  /** The approvals window's filters (`ListFilters.ApprovalFilter`): the state (`None` = the open ones, `all` = every
    * one), the kinds and deciders (any of), the subject text.
    */
  val approvalState =
    Field[Option[String]](Field.Key("approvalState"), Field.HashKey("apprState"), None, Persist.Hash, Role.View)
  val approvalKinds =
    Field[List[String]](Field.Key("approvalKinds"), Field.HashKey("apprKind"), Nil, Persist.Hash, Role.View)
  val approvalBy = Field[List[String]](Field.Key("approvalBy"), Field.HashKey("apprBy"), Nil, Persist.Hash, Role.View)
  val approvalSubject =
    Field[Option[String]](Field.Key("approvalSubject"), Field.HashKey("apprSubject"), None, Persist.Hash, Role.View)

  /** The scheduler panel's filters over its queue: the job types and the reasons (any of), the target text. */
  val schedTypes =
    Field[List[String]](Field.Key("schedTypes"), Field.HashKey("schedType"), Nil, Persist.Hash, Role.View)
  val schedCodes = Field[List[String]](Field.Key("schedCodes"), Field.HashKey("schedWhy"), Nil, Persist.Hash, Role.View)
  val schedSubject =
    Field[Option[String]](Field.Key("schedSubject"), Field.HashKey("schedSubject"), None, Persist.Hash, Role.View)

  /** The acquisition window also lists the items already done. */
  val acquireDone = Field[Boolean](Field.Key("acquireDone"), Field.HashKey("acqDone"), false, Persist.Hash, Role.View)

  /** The backlog window also lists the tasks already done. */
  val tasksDone = Field[Boolean](Field.Key("tasksDone"), Field.HashKey("tasksDone"), false, Persist.Hash, Role.View)

  /** The bundle the merge review screen is reviewing — its sha256 on the server, so a reload (or a link) reopens the
    * same review.
    */
  val mergeBundle =
    Field[Option[String]](Field.Key("mergeBundle"), Field.HashKey("bundle"), None, Persist.Hash, Role.View)

  /** The API key writes are sent with, when the server asks for one ([[Auth]]). In this browser's storage only — never
    * in the URL, so a shared link cannot carry it.
    */
  val apiKey = Field[Option[String]](Field.Key("apiKey"), Field.HashKey("apiKey"), None, Persist.Local, Role.View)

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

  def setLayer(l: MapLayer, on: Boolean): Unit =
    set(layers := (if on then layers.now() + l else layers.now() - l))()

  // ── the hovered suggestion (never a chip, never in the URL) ────────────────────────────────────

  /** A hovered filter suggestion, applied to every query but never to the chips or the URL. */
  case class Preview(key: Preview.Key, value: Preview.Value) derives CanEqual

  object Preview:
    type Key = Key.T
    object Key extends Newtype[String, Pure]
    type Value = FilterTerm
    val Value: FilterTerm.type = FilterTerm
  val preview: Var[Option[Preview]] = Var(None)

  def setPreview(p: Option[Preview]): Unit =
    if preview.now() != p then
      preview.set(p)
      bump(byPreview = true)

  // `var`s: whether the newest change was only a hovered suggestion's preview, and how long a reload
  // waits after one (`ui.debounce.preview-ms`, known once `/api/config` has answered)
  private var previewChange: Boolean = false
  private[web] var previewMs: DebounceConfig.PreviewMs = DebounceConfig.PreviewMs(0)

  /** How long a reload scheduled NOW waits: `normalMs`, or less after a preview — the pointer resting on a suggestion
    * should see its lists at once, not after the typing debounce.
    */
  def reloadDelay(normalMs: ReloadDelayNormalMs): ReloadDelay =
    if previewChange then ReloadDelay(math.min(normalMs.value, previewMs.value)) else ReloadDelay(normalMs.value)

  private def withPreview(key: String, vs: List[String]): List[String] =
    preview.now() match
      case Some(p) if p.key.value == key && !vs.contains(p.value.value) => vs :+ p.value.value
      case _                                                            => vs

  // ── history sequence (the timeline's back/forward buttons read it) ─────────────────────────────

  private var seq: ChangeSeq = ChangeSeq(0)
  private var high: ChangeSeq = ChangeSeq(0)

  def currentSeq: ChangeSeq = seq
  def maxSeq: ChangeSeq = high

  // ── committing ────────────────────────────────────────────────────────────────────────────────

  private val changeSignal: Var[Int] = Var(0)

  /** Ticks after every committed change: the app reloads what depends on the filter. */
  def signal: Signal[Int] = changeSignal.signal

  private def bump(byPreview: Boolean = false): Unit =
    previewChange = byPreview
    changeSignal.update(_ + 1)

  // `var`: tests swap the browser's storage for a Store.Memory
  private[web] var store: Store = Store.Browser
  // `var`: tests run in Node, which has no location/history to mirror the hash into
  private[web] var mirrorHash: Boolean = true

  private val LocalPrefix = "histmersion.ui."

  /** Commit writes: apply them, mirror the hash and the local store, notify.
    *
    * Called from an event handler, `set` runs inside an Airstream transaction, where `Var.set` only SCHEDULES the
    * update: reading the fields right after would mirror the OLD values (a clicked layer was stored as off, and on
    * reload its button and layer came back off). The mirroring is therefore a transaction of its own, queued after the
    * assignments; outside any transaction (tests, start-up) it runs at once, as before.
    */
  def set(assigns: Assign*)(push: NavigatePush = NavigatePush(false)): Unit =
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
  def toggledPlace(ids: List[String], id: ToggledPlaceId): List[String] =
    if ids.contains(id.value) then ids.filterNot(_ == id.value) else List(id.value)

  /** Choose or unchoose place `id` (see [[toggledPlace]]); a step in the history, so Back undoes a click on the map.
    * Returns whether it is chosen now.
    */
  def togglePlace(id: TogglePlaceId): Boolean =
    val next = toggledPlace(placeIds.now(), id)
    set(placeIds := next)(push = NavigatePush(true))
    next.contains(id.value)

  /** Both search boxes share the raw draft, including a space just typed between words. Filter.of normalizes it at the
    * request boundary, not while the controlled input is editing.
    */
  def setSearch(text: Api.Search.Text): Unit = set(q := Option.when(text.value.nonEmpty)(text.value))()

  def toFilter(except: Set[ToFilterExcept] = Set.empty): Filter =
    def keep(keys: String*) = !keys.exists(k => except.contains(ToFilterExcept(k)))
    val box = if keep("bbox") && inView.now() then bbox.now().flatMap(parseBbox) else None
    val f = Filter.of(
      q = (if keep("q") then q.now() else None).map(Filter.Q(_)),
      tags = (if keep("tags", "tag") then withPreview("tags", tags.now()) else Nil).map(Filter.Tags(_)),
      authors =
        (if keep("authors", "author") then withPreview("authors", authors.now()) else Nil).map(Filter.Authors(_)),
      places = ((if keep("places", "place") then withPreview("places", places.now()) else Nil) ++
        (if keep("placeIds", "place") then placeIds.now() else Nil)).map(Filter.Places(_)),
      topics = (if keep("topics", "topic") then withPreview("topics", topics.now()) else Nil).map(Filter.Topics(_)),
      status = (if keep("status") then withPreview("status", status.now()) else Nil).map(Filter.Status(_)),
      yearFrom = (if keep("yearFrom") then yearFrom.now() else None).map(Year(_)),
      yearTo = (if keep("yearTo") then yearTo.now() else None).map(Year(_)),
      from = (if keep("from") then from.now() else None).map(Year(_)),
      to = (if keep("to") then to.now() else None).map(Year(_)),
      minLon = box.map(b => Longitude(b._1)),
      minLat = box.map(b => Latitude(b._2)),
      maxLon = box.map(b => Longitude(b._3)),
      maxLat = box.map(b => Latitude(b._4)),
      sort = sort.now().map(Sort.Wire(_)),
      limit = None,
      offset = None,
      title = (if keep("title") then title.now() else None).map(Filter.Title(_)),
      goal = (if keep("goal") then goal.now() else None).map(Filter.Goal(_))
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
  def articleQuery(): Api.ArticleQuery =
    Api.ArticleQuery(toFilter().modify(_.limit).setTo(Filter.Limit(Layout.tabFetch.value)))

  /** What the Events tab asks for: the global filter, title included — the server lists the events whose source it
    * selects, in its period.
    */
  def eventQuery(): Api.EventQuery =
    Api.EventQuery(toFilter().modify(_.limit).setTo(Filter.Limit(Layout.tabFetch.value)))

  private[web] def parseBbox(s: String): Option[(Double, Double, Double, Double)] =
    s.split(",").flatMap(_.trim.toDoubleOption).toList match
      case a :: b :: c :: d :: Nil => Some((a, b, c, d))
      case _                       => None

  def clearFilters(): Unit =
    fields.filter(_.role == Role.Filter).foreach(_.clear())
    writeHash(push = NavigatePush(false))
    bump()

  case class ActiveChip(
      key: Field.Key,
      label: ActiveChip.Label,
      value: ActiveChip.Value,
      shown: Option[ActiveChip.Shown] = None
  )

  object ActiveChip:
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Value = FilterTerm
    val Value: FilterTerm.type = FilterTerm
    type Shown = Shown.T
    object Shown extends Newtype[String, Pure]

  def activeChips(): List[ActiveChip] =
    fields
      .filter(f => f.role == Role.Filter && f.chip.value)
      .flatMap(f =>
        f.encoded.map(v =>
          ActiveChip(
            f.key,
            ActiveChip.Label(f.label.map(_.value).getOrElse(f.key.value)),
            ActiveChip.Value(v.value)
          )
        )
      )

  def removeChip(key: RemoveChipKey, value: FilterTerm): Unit =
    fields.find(_.key.value == key.value).foreach(_.remove(Field.Token(value.value)))
    writeHash(push = NavigatePush(false))
    bump()

  // ── the URL hash ──────────────────────────────────────────────────────────────────────────────

  private def pairsOf(hash: UrlHash): Map[String, List[String]] =
    val params = new dom.URLSearchParams(hash.value.stripPrefix("#"))
    val out = List.newBuilder[(String, String)]
    params.forEach((value, key) => out += key -> value)
    out.result().groupMap(_._1)(_._2)

  /** The hash for the current state. */
  private[web] def hashString: UrlHash =
    val params = new dom.URLSearchParams("")
    hashFields.foreach(f => f.encoded.foreach(t => params.append(f.hashKey.value, t.value)))
    UrlHash(params.toString)

  private def writeHash(push: NavigatePush): Unit =
    if mirrorHash then navigate(NavigatePathAndSearch(window.location.pathname + window.location.search), push)

  /** Go to another page of the web UI (`pathAndSearch`, e.g. `/research/backlog?item=3`): the address changes, the hash
    * state goes along (the filters, the open windows), and with `push` it is a step in the history that Back undoes.
    */
  def navigate(pathAndSearch: NavigatePathAndSearch, push: NavigatePush): Unit =
    if mirrorHash then
      val qs = hashString.value
      val url = pathAndSearch.value + (if qs.nonEmpty then "#" + qs else "")
      if push.value then
        seq = { high = ChangeSeq(high.value + 1); high }
        window.history.pushState(js.Dynamic.literal(histmersionSeq = seq.value), "", url)
      else window.history.replaceState(js.Dynamic.literal(histmersionSeq = seq.value), "", url)

  /** Load every HASH field from `hash`: a field the hash does not mention goes back to its default. */
  private[web] def loadHash(hash: UrlHash): Unit =
    val m = pairsOf(hash)
    hashFields.foreach(f => f.load(m.getOrElse(f.hashKey.value, Nil).map(Field.Token(_))))

  /** Merge a hash fragment INTO the state — a `/#tag=x` link inside a window adds the tag and keeps every other filter
    * (following it as a navigation replaced them all).
    */
  def addHash(hash: UrlHash): Unit =
    val m = pairsOf(hash)
    val touched = hashFields.filter(f => m.contains(f.hashKey.value))
    if touched.nonEmpty then set(touched.map(f => Assign(() => f.add(m(f.hashKey.value).map(Field.Token(_)))))*)()

  // ── local persistence ─────────────────────────────────────────────────────────────────────────

  private def writeLocal(): Unit =
    localFields.foreach(f => store.put(LocalPrefix + f.key, js.JSON.stringify(js.Array(f.encoded*))))

  /** The pre-UiState layer memory (`{overlay, media, sites}` under one key): migrated once, removed. */
  private val LegacyLayersKey = "histmersion.layers"

  /** Whether this browser keeps a value of the [[Persist.Local]] field `f` (a choice once made). */
  private[web] def stored(f: Field[?]): Boolean = store.get(LocalPrefix + f.key).isDefined

  /** Load the [[Persist.Local]] fields from the store. */
  private[web] def restoreLocal(): Unit =
    localFields.foreach(restore)

  /** Load the kept API key alone: the first request, `/api/config`, may need it before anything else is set up. */
  private[web] def restoreKey(): Unit = restore(apiKey)

  /** Keep `key` (or none) before the page has started: only the key is written, never the address or the other kept
    * choices, which are not loaded yet.
    */
  private[web] def keepKey(key: Option[String]): Unit =
    apiKey.set(key)
    key match
      case Some(_) => store.put(LocalPrefix + apiKey.key, js.JSON.stringify(js.Array(apiKey.encoded*)))
      case None    => store.remove(LocalPrefix + apiKey.key)

  private def restore(f: Field[?]): Unit =
    store
      .get(LocalPrefix + f.key)
      .foreach(raw =>
        Try(js.JSON.parse(raw).asInstanceOf[js.Array[String]].toList).foreach(l => f.load(l.map(Field.Token(_))))
      )
    store.get(LegacyLayersKey).foreach { raw =>
      if store.get(LocalPrefix + layers.key).isEmpty then
        Try(js.JSON.parse(raw)).foreach { d =>
          def on(name: String) =
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
    ListFilterBar.typingMs = config.debounce.searchMs.value
    from.withDefault(Some(config.time.defaultFrom.value))
    to.withDefault(Some(config.time.defaultTo.value))
    restoreLocal()
    // the map's motion follows the kept choice, now and whenever it changes
    mapMotion.signal.foreach(MotionPref.choose)(using unsafeWindowOwner)
    loadHash(UrlHash(window.location.hash))
    // back/forward: the HASH fields follow the history entry; local preferences (layers, base map)
    // are not history and stay as they are
    window.addEventListener(
      "popstate",
      { (e: dom.PopStateEvent) =>
        val state = e.state
        seq = if state != null then
          ChangeSeq(Try(state.asInstanceOf[js.Dynamic].histmersionSeq.asInstanceOf[Int]).getOrElse(0))
        else ChangeSeq(0)
        loadHash(UrlHash(window.location.hash))
        bump()
      }
    )

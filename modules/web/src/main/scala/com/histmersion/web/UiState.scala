package com.histmersion.web

import com.raquo.airstream.core.Transaction

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.map.{MapMotion, MotionPref, VectorStyle}
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import scala.util.Try

/** How a field's value crosses a string boundary — the URL hash (repeated keys for a list) or the
  * browser's storage. `decode` of nothing, or of garbage, is `None`: the field falls back to its
  * default rather than to a half-parsed value. */
trait FieldCodec[A]:
  def encode(a: A): List[String]
  def decode(vs: List[String]): Option[A]
  /** A list field MERGES an added hash value (`/#tag=x` adds a tag); a scalar one replaces. */
  def multi: Boolean = false

object FieldCodec:
  private def clean(vs: List[String]) = vs.map(_.trim).filter(_.nonEmpty)

  given optString: FieldCodec[Option[String]]:
    def encode(a: Option[String]) = a.toList.map(_.trim).filter(_.nonEmpty)
    def decode(vs: List[String])  = Some(clean(vs).headOption)

  given optLong: FieldCodec[Option[Long]]:
    def encode(a: Option[Long]) = a.toList.map(_.toString)
    def decode(vs: List[String])  = Some(clean(vs).headOption.flatMap(_.toLongOption))

  given strings: FieldCodec[List[String]]:
    def encode(a: List[String]) = a
    def decode(vs: List[String])  = Some(clean(vs).distinct)
    override def multi            = true

  given bool: FieldCodec[Boolean]:
    def encode(a: Boolean)        = if a then List("true") else Nil
    def decode(vs: List[String])  = Some(clean(vs).headOption.contains("true"))

  given mapLayers: FieldCodec[Set[MapLayer]]:
    def encode(a: Set[MapLayer]) = MapLayer.values.toList.filter(a).map(_.wire)
    def decode(vs: List[String])  = Some(clean(vs).flatMap(MapLayer.fromWire).toSet)
    override def multi            = true

  given windowKeys: FieldCodec[List[WindowKey]]:
    def encode(a: List[WindowKey]) = a.map(_.wire)
    def decode(vs: List[String])    = Some(clean(vs).flatMap(WindowKey.parse).distinct)
    override def multi              = true

  given optJobState: FieldCodec[Option[JobState]]:
    def encode(a: Option[JobState]) = a.toList.map(_.wire)
    def decode(vs: List[String])     = Some(clean(vs).headOption.flatMap(JobState.parse))

  given optRunState: FieldCodec[Option[Agents.RunState]]:
    def encode(a: Option[Agents.RunState]) = a.toList.map(_.wire)
    def decode(vs: List[String])            = Some(clean(vs).headOption.flatMap(Agents.RunState.parse))

  given sheet: FieldCodec[Sheet]:
    def encode(a: Sheet)         = List(a.wire)
    def decode(vs: List[String])  = clean(vs).headOption.flatMap(Sheet.fromWire)

  given baseMap: FieldCodec[BaseMap]:
    def encode(a: BaseMap)       = List(a.wire)
    def decode(vs: List[String])  = clean(vs).headOption.flatMap(BaseMap.fromWire)

  given mapMotion: FieldCodec[MapMotion]:
    def encode(a: MapMotion) = List(a.wire)
    def decode(vs: List[String])  = clean(vs).headOption.flatMap(MapMotion.fromWire)

  given geoPick: FieldCodec[GeoPick]:
    def encode(a: GeoPick)       = List(a.wire)
    def decode(vs: List[String])  = clean(vs).headOption.flatMap(GeoPick.parse)

/** Where a field survives a reload. */
enum Persist:
  /** In the URL hash: shareable, and walked by back/forward. */
  case Hash
  /** In the browser's storage: a per-browser preference, not part of a shared link. */
  case Local

/** What a field is FOR — which fields "clear all" clears and which become chips. */
enum Role:
  /** Part of the query, shown as a removable chip (unless `chip = false`), cleared by "clear all". */
  case Filter
  /** The time window and viewport: part of the query, never a chip, not cleared by "clear all". */
  case Span
  /** UI only: the tab, the open windows, the layers. Never sent to the API. */
  case View

/** One piece of UI state, typed. The web UI's SINGLE source of truth: every component reads a
  * field's `signal`/`now()` and commits through [[UiState.set]] — nothing keeps its own copy. */
final class Field[A](
    val key: String,
    val hashKey: String,
    initial: A,
    val persist: Persist,
    val role: Role,
    val label: Option[String] = None,
    val chip: Boolean = true
)(using val codec: FieldCodec[A]):
  private val v = Var(initial)
  // `var`: `from`/`to` default to the CONFIGURED span, known only once `/api/config` has answered
  private var dflt: A = initial

  def default: A              = dflt
  def now(): A                = v.now()
  def signal: StrictSignal[A] = v.signal
  /** Write WITHOUT committing (no hash, no reload) — a live drag; commit with [[UiState.set]]. */
  def set(a: A): Unit         = v.set(a)
  def update(f: A => A): Unit = v.update(f)
  def clear(): Unit           = v.set(dflt)
  def :=(a: A): UiState.Assign = UiState.Assign(() => v.set(a))

  private[web] def withDefault(a: A): Unit      = { dflt = a; v.set(a) }
  private[web] def encoded: List[String]        = codec.encode(now())
  private[web] def load(vs: List[String]): Unit = v.set(codec.decode(vs).getOrElse(dflt))
  /** Add hash values: merged into a list field, replacing a scalar one. */
  private[web] def add(vs: List[String]): Unit =
    if codec.multi then load((encoded ++ vs).distinct) else load(vs)
  /** Remove ONE chip value (a list keeps the others; a scalar goes back to its default). */
  private[web] def remove(value: String): Unit = load(encoded.filterNot(_ == value))

/** Key-value persistence for [[Persist.Local]] fields: the browser's `localStorage` in the app, an
  * in-memory map in tests (Node has none). Every access is guarded — private browsing and a full
  * quota must degrade to "not remembered", never to a broken page. */
trait Store:
  def get(key: String): Option[String]
  def put(key: String, value: String): Unit
  def remove(key: String): Unit

object Store:
  object Browser extends Store:
    def get(key: String): Option[String] =
      Try(Option(window.localStorage.getItem(key))).toOption.flatten
    def put(key: String, value: String): Unit = { val _ = Try(window.localStorage.setItem(key, value)) }
    def remove(key: String): Unit             = { val _ = Try(window.localStorage.removeItem(key)) }

  final class Memory extends Store:
    private val m = scala.collection.mutable.Map.empty[String, String]
    def get(key: String): Option[String]      = m.get(key)
    def put(key: String, value: String): Unit = m(key) = value
    def remove(key: String): Unit             = { val _ = m.remove(key) }

/** How far the sidebar is pulled up in the narrow (phone) layout, where it is a bottom sheet: a
  * strip, half the screen, or all of it but the search bar. The heights are the stylesheet's. */
enum Sheet(val wire: String):
  case Peek extends Sheet("peek")
  case Half extends Sheet("half")
  case Full extends Sheet("full")

object Sheet:
  def fromWire(s: String): Option[Sheet] = values.find(_.wire == s.trim.toLowerCase)
  /** A tap on the handle: one step up, and from the top back down to the strip. */
  def next(s: Sheet): Sheet = s match
    case Peek => Half
    case Half => Full
    case Full => Peek
  /** Where a drag lets go: the state whose height is nearest to `fraction` of the screen, given the
    * strip's and the full sheet's fractions. Pure. */
  def nearest(fraction: Double, peek: Double, full: Double): Sheet =
    List(Peek -> peek, Half -> 0.5, Full -> full).minBy((_, f) => math.abs(f - fraction))._1

/** The base map (docs/user-guide/exploring/web-ui.md, "The base map"). `Readable` and `Physical` are
  * the two vector styles drawn from the server's own extracts ([[BaseMaps]]); `Standard`, `Muted` and
  * `Dark` the ONLINE raster maps — `Standard` the configured tile URL (`ui.tiles`), `Dark` the same tiles
  * drawn dark (no other tile server is contacted) — offered only when `ui.base-map-source` is online. */
enum BaseMap(val wire: String, val label: String, val vector: Option[com.histmersion.web.map.VectorStyle], val url: Option[String]):
  case Readable extends BaseMap("readable", "Readable", Some(com.histmersion.web.map.VectorStyle.Readable), None)
  case Physical extends BaseMap("physical", "Physical", Some(com.histmersion.web.map.VectorStyle.Physical), None)
  case Standard extends BaseMap("standard", "Standard (online)", None, None)
  case Muted    extends BaseMap("muted", "Muted (online)", None, Some("https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"))
  case Dark     extends BaseMap("dark", "Dark (online)", None, None)

  def online: Boolean = vector.isEmpty

/** Whether the base map under the names is light or dark — what the map's label colours follow
  * (`.map-tone-light` / `.map-tone-dark`: dark text on a light halo, or the inverse). */
enum MapTone(val wire: String):
  case Light extends MapTone("light")
  case Dark  extends MapTone("dark")

object MapTone:
  /** Pure: `ui.tiles-tone`'s value; anything but "dark" is the light default (OSM's own tiles). */
  def parse(s: String): MapTone = if s.trim.equalsIgnoreCase("dark") then Dark else Light

object BaseMap:
  /** A kept choice; `terrain` is what a browser kept for the local map before the two styles: Readable. */
  def fromWire(s: String): Option[BaseMap] =
    val w = s.trim.toLowerCase
    if w == "terrain" then Some(Readable) else values.find(_.wire == w)

  val online: List[BaseMap] = values.toList.filter(_.online)
  val vectors: List[BaseMap] = values.toList.filterNot(_.online)

  /** Pure: the tone of base map `b`, when the configured tiles (`ui.tiles`) are `configured`. */
  def tone(b: BaseMap, configured: MapTone): MapTone = b match
    case Standard => configured
    case Dark     => MapTone.Dark
    case _        => MapTone.Light

  /** Pure: is base map `b` drawn by darkening light tiles? Not when the configured tiles already are dark. */
  def darkened(b: BaseMap, configured: MapTone): Boolean = b == Dark && configured == MapTone.Light

/** A viewer's options over the vector styles ([[BaseMaps]]): names on or off, their language, the
  * contours (Physical), the sea's depth in each style (`depthPhysical`, `depthReadable`). `None`: the
  * default (names on, English — or `basemap.labels.language` — contours on, the depth on in Physical and
  * off in Readable). */
final case class MapPrefs(names: Option[Boolean] = None, language: Option[String] = None, contours: Option[Boolean] = None,
    depthPhysical: Option[Boolean] = None, depthReadable: Option[Boolean] = None)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject:

  /** Is the sea drawn by its depth in `style`: the viewer's choice, else the style's default. Pure. */
  def seaDepth(style: VectorStyle): Boolean = style match
    case VectorStyle.Physical => depthPhysical.getOrElse(MapPrefs.depthByDefault(style))
    case VectorStyle.Readable => depthReadable.getOrElse(MapPrefs.depthByDefault(style))

  /** The viewer's choice of the depth in `style`; the style's default is kept as no choice. Pure. */
  def withSeaDepth(style: VectorStyle, on: Boolean): MapPrefs =
    val v = Option.when(on != MapPrefs.depthByDefault(style))(on)
    style match
      case VectorStyle.Physical => copy(depthPhysical = v)
      case VectorStyle.Readable => copy(depthReadable = v)

object MapPrefs:
  /** Physical is the map of the land's form, its sea coloured by depth; Readable keeps OSM Carto's flat
    * water unless the viewer asks for the depth. */
  def depthByDefault(style: VectorStyle): Boolean = style == VectorStyle.Physical

  /** Kept as one JSON value; anything unreadable is no options at all. */
  given FieldCodec[MapPrefs]:
    def encode(a: MapPrefs) = if a == MapPrefs() then Nil else List(io.circe.syntax.EncoderOps(a).asJson.noSpaces)
    def decode(vs: List[String]) =
      Some(vs.headOption.flatMap(v => io.circe.parser.decode[MapPrefs](v).toOption).getOrElse(MapPrefs()))

object UiState:

  /** A pending write, built with `field := value` and committed by [[set]]. */
  final class Assign(private[web] val run: () => Unit)

  import FieldCodec.given

  // ── the fields ────────────────────────────────────────────────────────────────────────────────
  val q        = Field[Option[String]]("q", "q", None, Persist.Hash, Role.Filter, Some("search"))
  val tags     = Field[List[String]]("tags", "tag", Nil, Persist.Hash, Role.Filter, Some("tag"))
  val authors  = Field[List[String]]("authors", "author", Nil, Persist.Hash, Role.Filter, Some("author"))
  val places   = Field[List[String]]("places", "place", Nil, Persist.Hash, Role.Filter, Some("place"))
  val placeIds = Field[List[String]]("placeIds", "placeId", Nil, Persist.Hash, Role.Filter, Some("place"))
  val topics   = Field[List[String]]("topics", "topic", Nil, Persist.Hash, Role.Filter, Some("topic"))
  val status   = Field[List[String]]("status", "status", Nil, Persist.Hash, Role.Filter, Some("status"))
  val yearFrom = Field[Option[Long]]("yearFrom", "yearFrom", None, Persist.Hash, Role.Filter, Some("published from"))
  val yearTo   = Field[Option[Long]]("yearTo", "yearTo", None, Persist.Hash, Role.Filter, Some("published to"))
  val sort     = Field[Option[String]]("sort", "sort", None, Persist.Hash, Role.Filter, Some("sort"), chip = false)
  /** The title quick-filter above the tabs: narrows every list by its items' own title (a source's
    * title, an article's title, an event's label). A filter the user typed, so "clear all" clears
    * it; not a chip, because the input showing it is always on screen. */
  val title    = Field[Option[String]]("title", "title", None, Persist.Hash, Role.Filter, Some("title"), chip = false)
  val from     = Field[Option[Long]]("from", "from", None, Persist.Hash, Role.Span)
  val to       = Field[Option[Long]]("to", "to", None, Persist.Hash, Role.Span)
  val bbox     = Field[Option[String]]("bbox", "bbox", None, Persist.Hash, Role.Span)
  val inView   = Field[Boolean]("inView", "inView", false, Persist.Hash, Role.Span)
  /** The open windows — written by [[WindowManager]]. */
  val open     = Field[List[WindowKey]]("open", "open", Nil, Persist.Hash, Role.View)
  /** Library's order of its sources ([[library.LibrarySort]]): a view of the list, not a filter. */
  val librarySort = Field[Option[String]]("librarySort", "lsort", None, Persist.Hash, Role.View)
  val tab      = Field[Option[String]]("tab", "tab", None, Persist.Hash, Role.View)
  val layers   = Field[Set[MapLayer]]("layers", "layers", Set.empty, Persist.Local, Role.View)
  /** The finding markers ("sources") on the map. Not a [[MapLayer]]: it is ON by default, and a
    * browser that already remembers a `layers` set (every one saved before this switch existed) would
    * restore it without the markers. A key of its own that is absent until first toggled keeps them on. */
  val sources  = Field[Boolean]("sources", "sources", true, Persist.Local, Role.View)
  val baseMap  = Field[BaseMap]("baseMap", "baseMap", BaseMap.Standard, Persist.Local, Role.View)
  /** The viewer's options over the vector styles ([[BaseMaps]]): names, their language, contours. Per device. */
  val mapOptions = Field[MapPrefs]("mapOptions", "mapOptions", MapPrefs(), Persist.Local, Role.View)
  /** The media layer's provider and licence filters (#1446): `None` = every one. Per device. */
  val mediaProvider = Field[Option[String]]("mediaProvider", "mediaProvider", None, Persist.Local, Role.View)
  val mediaLicense  = Field[Option[String]]("mediaLicense", "mediaLicense", None, Persist.Local, Role.View)
  /** The events layer's filters (#1449): a kind or a group (`natural` | `human`), and the tier
    * (`attested` | `catalogue`); `None` = every one. The sites layer's: one site type. Per device. */
  val eventKind = Field[Option[String]]("eventKind", "eventKind", None, Persist.Local, Role.View)
  val eventTier = Field[Option[String]]("eventTier", "eventTier", None, Persist.Local, Role.View)
  val siteType  = Field[Option[String]]("siteType", "siteType", None, Persist.Local, Role.View)
  /** "Also show sites (events) with no known date": drawn beneath the dated ones, outlined dashed. Off by
    * default; per device. */
  val sitesUndated  = Field[Boolean]("sitesUndated", "sitesUndated", false, Persist.Local, Role.View)
  val eventsUndated = Field[Boolean]("eventsUndated", "eventsUndated", false, Persist.Local, Role.View)
  /** How the boundaries layer reads the period ([[GeoPick]]): a snapshot at its middle (the default),
    * start or end, or every state in it with its earliest or latest shape. Sites follow a snapshot's
    * year and the middle otherwise; playback always draws one year. Per device. */
  val areaPick  = Field[GeoPick]("areaPick", "areaPick", GeoPick.Mid, Persist.Local, Role.View)
  /** The map's motion (#4718): follow the system's reduced-motion setting (the default), always animate, or
    * never. Per device; [[MotionPref]] answers "does the map animate now?" from it. */
  val mapMotion = Field[MapMotion]("mapMotion", "mapMotion", MapMotion.System,
    Persist.Local, Role.View)
  /** The sidebar's bottom sheet in the narrow layout (a per-device preference, never in a link). */
  val sheet    = Field[Sheet]("sheet", "sheet", Sheet.Peek, Persist.Local, Role.View)
  /** The jobs window's filters (core `ListFilters.JobFilter`): the state (`None` = every job), the
    * kinds (any of), the start dates and the subject text. */
  val jobStatus  = Field[Option[JobState]]("jobStatus", "jobs", None, Persist.Hash, Role.View)
  val jobKinds   = Field[List[String]]("jobKinds", "jobKind", Nil, Persist.Hash, Role.View)
  val jobSince   = Field[Option[String]]("jobSince", "jobSince", None, Persist.Hash, Role.View)
  val jobUntil   = Field[Option[String]]("jobUntil", "jobUntil", None, Persist.Hash, Role.View)
  val jobSubject = Field[Option[String]]("jobSubject", "jobSubject", None, Persist.Hash, Role.View)
  /** The agent runs window's filters (`ListFilters.RunFilter`): the state (`None` = every run), the
    * pipelines and origins (any of), one manager, provider, model and goal, the start dates, the
    * target text. */
  val runStatus    = Field[Option[Agents.RunState]]("runStatus", "runs", None, Persist.Hash, Role.View)
  val runPipelines = Field[List[String]]("runPipelines", "runPipeline", Nil, Persist.Hash, Role.View)
  val runOrigins   = Field[List[String]]("runOrigins", "runOrigin", Nil, Persist.Hash, Role.View)
  val runManager   = Field[Option[String]]("runManager", "runManager", None, Persist.Hash, Role.View)
  val runProvider  = Field[Option[String]]("runProvider", "runProvider", None, Persist.Hash, Role.View)
  val runModel     = Field[Option[String]]("runModel", "runModel", None, Persist.Hash, Role.View)
  val runGoal      = Field[Option[String]]("runGoal", "runGoal", None, Persist.Hash, Role.View)
  val runSince     = Field[Option[String]]("runSince", "runSince", None, Persist.Hash, Role.View)
  val runUntil     = Field[Option[String]]("runUntil", "runUntil", None, Persist.Hash, Role.View)
  val runSubject   = Field[Option[String]]("runSubject", "runSubject", None, Persist.Hash, Role.View)
  /** The approvals window's filters (`ListFilters.ApprovalFilter`): the state (`None` = the open
    * ones, `all` = every one), the kinds and deciders (any of), the subject text. */
  val approvalState   = Field[Option[String]]("approvalState", "apprState", None, Persist.Hash, Role.View)
  val approvalKinds   = Field[List[String]]("approvalKinds", "apprKind", Nil, Persist.Hash, Role.View)
  val approvalBy      = Field[List[String]]("approvalBy", "apprBy", Nil, Persist.Hash, Role.View)
  val approvalSubject = Field[Option[String]]("approvalSubject", "apprSubject", None, Persist.Hash, Role.View)
  /** The scheduler panel's filters over its queue: the job types and the reasons (any of), the target text. */
  val schedTypes   = Field[List[String]]("schedTypes", "schedType", Nil, Persist.Hash, Role.View)
  val schedCodes   = Field[List[String]]("schedCodes", "schedWhy", Nil, Persist.Hash, Role.View)
  val schedSubject = Field[Option[String]]("schedSubject", "schedSubject", None, Persist.Hash, Role.View)
  /** The acquisition window also lists the items already done. */
  val acquireDone = Field[Boolean]("acquireDone", "acqDone", false, Persist.Hash, Role.View)
  /** The backlog window also lists the tasks already done. */
  val tasksDone = Field[Boolean]("tasksDone", "tasksDone", false, Persist.Hash, Role.View)
  /** The bundle the merge review screen is reviewing — its sha256 on the server, so a reload (or a
    * link) reopens the same review. */
  val mergeBundle = Field[Option[String]]("mergeBundle", "bundle", None, Persist.Hash, Role.View)
  /** The API key writes are sent with, when the server asks for one ([[Auth]]). In this browser's
    * storage only — never in the URL, so a shared link cannot carry it. */
  val apiKey   = Field[Option[String]]("apiKey", "apiKey", None, Persist.Local, Role.View)

  private val fields: List[Field[?]] =
    List(q, tags, authors, places, placeIds, topics, status, yearFrom, yearTo, sort, title, from, to, bbox,
      inView, open, librarySort, tab, layers, sources, baseMap, mapOptions, mediaProvider, mediaLicense, eventKind, eventTier, siteType, sitesUndated, eventsUndated, areaPick, mapMotion, sheet, jobStatus, jobKinds, jobSince, jobUntil, jobSubject,
      runStatus, runPipelines, runOrigins, runManager, runProvider, runModel, runGoal, runSince, runUntil, runSubject,
      approvalState, approvalKinds, approvalBy, approvalSubject, schedTypes, schedCodes, schedSubject, acquireDone, tasksDone,
      mergeBundle, apiKey)

  private def hashFields  = fields.filter(_.persist == Persist.Hash)
  private def localFields = fields.filter(_.persist == Persist.Local)

  /** One overlay's state, as its toggle shows it. */
  def layerOn(l: MapLayer): Signal[Boolean] = layers.signal.map(_.contains(l))

  def setLayer(l: MapLayer, on: Boolean): Unit =
    set(layers := (if on then layers.now() + l else layers.now() - l))()

  // ── the hovered suggestion (never a chip, never in the URL) ────────────────────────────────────

  /** A hovered filter suggestion, applied to every query but never to the chips or the URL. */
  case class Preview(key: String, value: String)
  val preview: Var[Option[Preview]] = Var(None)

  def setPreview(p: Option[Preview]): Unit =
    if preview.now() != p then
      preview.set(p)
      bump(byPreview = true)

  // `var`s: whether the newest change was only a hovered suggestion's preview, and how long a reload
  // waits after one (`ui.debounce.preview-ms`, known once `/api/config` has answered)
  private var previewChange: Boolean = false
  private[web] var previewMs: Int    = 0

  /** How long a reload scheduled NOW waits: `normalMs`, or less after a preview — the pointer resting
    * on a suggestion should see its lists at once, not after the typing debounce. */
  def reloadDelay(normalMs: Int): Int = if previewChange then math.min(normalMs, previewMs) else normalMs

  private def withPreview(key: String, vs: List[String]): List[String] =
    preview.now() match
      case Some(Preview(`key`, v)) if !vs.contains(v) => vs :+ v
      case _                                        => vs

  // ── history sequence (the timeline's back/forward buttons read it) ─────────────────────────────

  private var seq: Int  = 0
  private var high: Int = 0

  def currentSeq: Int = seq
  def maxSeq: Int     = high

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
    * Called from an event handler, `set` runs inside an Airstream transaction, where `Var.set` only
    * SCHEDULES the update: reading the fields right after would mirror the OLD values (a clicked
    * layer was stored as off, and on reload its button and layer came back off). The mirroring is
    * therefore a transaction of its own, queued after the assignments; outside any transaction
    * (tests, start-up) it runs at once, as before. */
  def set(assigns: Assign*)(push: Boolean = false): Unit =
    assigns.foreach(_.run())
    Transaction { _ =>
      writeHash(push)
      writeLocal()
      bump()
    }

  /** Pure: the gazetteer place filter after place `id` is clicked on the map. It becomes THE place
    * (places are any-of, so adding it beside another would widen the lists, not narrow them); when it
    * is already chosen, it is removed and the other chosen places stay. */
  def toggledPlace(ids: List[String], id: String): List[String] =
    if ids.contains(id) then ids.filterNot(_ == id) else List(id)

  /** Choose or unchoose place `id` (see [[toggledPlace]]); a step in the history, so Back undoes a
    * click on the map. Returns whether it is chosen now. */
  def togglePlace(id: String): Boolean =
    val next = toggledPlace(placeIds.now(), id)
    set(placeIds := next)(push = true)
    next.contains(id)

  /** Both search boxes share the raw draft, including a space just typed between words.
    * Filter.of normalizes it at the request boundary, not while the controlled input is editing. */
  def setSearch(text: String): Unit = set(q := Option.when(text.nonEmpty)(text))()

  def toFilter(except: Set[String] = Set.empty): Filter =
    def keep(keys: String*) = !keys.exists(except)
    val box = if keep("bbox") && inView.now() then bbox.now().flatMap(parseBbox) else None
    val f = Filter.of(
      q = if keep("q") then q.now() else None,
      tags = if keep("tags", "tag") then withPreview("tags", tags.now()) else Nil,
      authors = if keep("authors", "author") then withPreview("authors", authors.now()) else Nil,
      places =
        (if keep("places", "place") then withPreview("places", places.now()) else Nil) ++
          (if keep("placeIds", "place") then placeIds.now() else Nil),
      topics = if keep("topics", "topic") then withPreview("topics", topics.now()) else Nil,
      status = if keep("status") then withPreview("status", status.now()) else Nil,
      yearFrom = if keep("yearFrom") then yearFrom.now() else None,
      yearTo = if keep("yearTo") then yearTo.now() else None,
      from = if keep("from") then from.now() else None,
      to = if keep("to") then to.now() else None,
      minLon = box.map(_._1), minLat = box.map(_._2), maxLon = box.map(_._3), maxLat = box.map(_._4),
      sort = sort.now(),
      limit = None,
      offset = None,
      title = if keep("title") then title.now() else None
    )
    val p = f.period
    val corrected =
      if p.exists(pp => pp.start > pp.end) then f.copy(period = p.map(pp => Period(pp.end, pp.start)))
      else f
    val y = corrected.years
    if y.exists(yy => yy.start > yy.end) then corrected.copy(years = y.map(yy => Period(yy.end, yy.start)))
    else corrected

  /** What the Articles tab asks for: the global filter, title included — the server lists the
    * articles citing the sources it selects. */
  def articleQuery(): Api.ArticleQuery = Api.ArticleQuery(toFilter().copy(limit = ui.Layout.tabFetch))

  /** What the Events tab asks for: the global filter, title included — the server lists the events
    * whose source it selects, in its period. */
  def eventQuery(): Api.EventQuery = Api.EventQuery(toFilter().copy(limit = ui.Layout.tabFetch))

  private[web] def parseBbox(s: String): Option[(Double, Double, Double, Double)] =
    s.split(",").flatMap(_.trim.toDoubleOption).toList match
      case a :: b :: c :: d :: Nil => Some((a, b, c, d))
      case _                       => None

  def clearFilters(): Unit =
    fields.filter(_.role == Role.Filter).foreach(_.clear())
    writeHash(push = false)
    bump()

  case class ActiveChip(key: String, label: String, value: String, shown: Option[String] = None)

  def activeChips(): List[ActiveChip] =
    fields.filter(f => f.role == Role.Filter && f.chip).flatMap(f =>
      f.encoded.map(v => ActiveChip(f.key, f.label.getOrElse(f.key), v)))

  def removeChip(key: String, value: String): Unit =
    fields.find(_.key == key).foreach(_.remove(value))
    writeHash(push = false)
    bump()

  // ── the URL hash ──────────────────────────────────────────────────────────────────────────────

  private def pairsOf(hash: String): Map[String, List[String]] =
    val params = new dom.URLSearchParams(hash.stripPrefix("#"))
    val out = List.newBuilder[(String, String)]
    params.forEach((value, key) => out += key -> value)
    out.result().groupMap(_._1)(_._2)

  /** The hash for the current state. */
  private[web] def hashString: String =
    val params = new dom.URLSearchParams("")
    hashFields.foreach(f => f.encoded.foreach(params.append(f.hashKey, _)))
    params.toString

  private def writeHash(push: Boolean): Unit =
    if mirrorHash then navigate(window.location.pathname + window.location.search, push)

  /** Go to another page of the web UI (`pathAndSearch`, e.g. `/research/backlog?item=3`): the address
    * changes, the hash state goes along (the filters, the open windows), and with `push` it is a step in
    * the history that Back undoes. */
  def navigate(pathAndSearch: String, push: Boolean): Unit =
    if mirrorHash then
      val qs  = hashString
      val url = pathAndSearch + (if qs.nonEmpty then "#" + qs else "")
      if push then
        seq = { high += 1; high }
        window.history.pushState(js.Dynamic.literal(histmersionSeq = seq), "", url)
      else
        window.history.replaceState(js.Dynamic.literal(histmersionSeq = seq), "", url)

  /** Load every HASH field from `hash`: a field the hash does not mention goes back to its default. */
  private[web] def loadHash(hash: String): Unit =
    val m = pairsOf(hash)
    hashFields.foreach(f => f.load(m.getOrElse(f.hashKey, Nil)))

  /** Merge a hash fragment INTO the state — a `/#tag=x` link inside a window adds the tag and keeps
    * every other filter (following it as a navigation replaced them all). */
  def addHash(hash: String): Unit =
    val m       = pairsOf(hash)
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
      store.get(LocalPrefix + f.key).foreach(raw =>
        Try(js.JSON.parse(raw).asInstanceOf[js.Array[String]].toList).foreach(f.load))
    }
    store.get(LegacyLayersKey).foreach { raw =>
      if store.get(LocalPrefix + layers.key).isEmpty then
        Try(js.JSON.parse(raw)).foreach { d =>
          def on(name: String) =
            Try(d.selectDynamic(name).asInstanceOf[Any]).toOption.contains(true)
          layers.set(List(MapLayer.Boundaries -> "overlay", MapLayer.Media -> "media", MapLayer.Sites -> "sites")
            .collect { case (l, n) if on(n) => l }.toSet)
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
    window.addEventListener("popstate", { (e: dom.PopStateEvent) =>
      val state = e.state
      seq = if state != null then
        Try(state.asInstanceOf[js.Dynamic].histmersionSeq.asInstanceOf[Int]).getOrElse(0)
      else 0
      loadHash(window.location.hash)
      bump()
    })

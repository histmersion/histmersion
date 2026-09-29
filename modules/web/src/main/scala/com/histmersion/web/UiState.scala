package com.histmersion.web

import com.raquo.airstream.core.Transaction

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
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

  given baseMap: FieldCodec[BaseMap]:
    def encode(a: BaseMap)       = List(a.wire)
    def decode(vs: List[String])  = clean(vs).headOption.flatMap(BaseMap.fromWire)

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

/** The base-map tile style. `Standard` uses the configured tile URL. */
enum BaseMap(val wire: String, val label: String, val url: Option[String]):
  case Standard extends BaseMap("standard", "Standard", None)
  case Muted    extends BaseMap("muted", "Muted", Some("https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"))

object BaseMap:
  def fromWire(s: String): Option[BaseMap] = values.find(_.wire == s.trim.toLowerCase)

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
  val tab      = Field[Option[String]]("tab", "tab", None, Persist.Hash, Role.View)
  val layers   = Field[Set[MapLayer]]("layers", "layers", Set.empty, Persist.Local, Role.View)
  /** The finding markers ("sources") on the map. Not a [[MapLayer]]: it is ON by default, and a
    * browser that already remembers a `layers` set (every one saved before this switch existed) would
    * restore it without the markers. A key of its own that is absent until first toggled keeps them on. */
  val sources  = Field[Boolean]("sources", "sources", true, Persist.Local, Role.View)
  val baseMap  = Field[BaseMap]("baseMap", "baseMap", BaseMap.Standard, Persist.Local, Role.View)
  /** The jobs window's status filter (`None` = every job). */
  val jobStatus = Field[Option[JobState]]("jobStatus", "jobs", None, Persist.Hash, Role.View)
  /** The acquisition window also lists the items already done. */
  val acquireDone = Field[Boolean]("acquireDone", "acqDone", false, Persist.Hash, Role.View)
  /** The backlog window also lists the tasks already done. */
  val tasksDone = Field[Boolean]("tasksDone", "tasksDone", false, Persist.Hash, Role.View)
  /** The API key writes are sent with, when the server asks for one ([[Auth]]). In this browser's
    * storage only — never in the URL, so a shared link cannot carry it. */
  val apiKey   = Field[Option[String]]("apiKey", "apiKey", None, Persist.Local, Role.View)

  private val fields: List[Field[?]] =
    List(q, tags, authors, places, placeIds, topics, status, yearFrom, yearTo, sort, title, from, to, bbox,
      inView, open, tab, layers, sources, baseMap, jobStatus, acquireDone, tasksDone, apiKey)

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
      bump()

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

  private def bump(): Unit = changeSignal.update(_ + 1)

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
    if mirrorHash then
      val qs  = hashString
      val url = if qs.nonEmpty then "#" + qs else window.location.pathname + window.location.search
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
    from.withDefault(Some(config.time.defaultFrom))
    to.withDefault(Some(config.time.defaultTo))
    restoreLocal()
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

package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js

object TimelinePlayback:

  private val playing   = Var(false)
  private val playhead  = Var(0L)
  private val speed     = Var(1.0)
  private val windowHalf = Var(Layout.playbackWindowHalf)

  private var frameId: Int = 0
  private var lastTs: Double = 0.0
  private var spanFrom: Long = 0
  private var spanTo: Long = 0

  /** The playback speeds offered, slowest first; 1× plays the shown span in `Layout.playbackDuration`. */
  private[web] val Speeds: List[Double] = List(0.25, 0.5, 1.0, 2.0, 4.0)

  /** `0.25×`, `1×`: how a speed reads in the menu (and its option value). Pure. */
  private[web] def speedLabel(s: Double): String =
    val n = if s == math.rint(s) then s.toLong.toString else BigDecimal(s).bigDecimal.stripTrailingZeros.toPlainString
    s"$n×"

  /** The speed an option stands for; one that is not offered is refused. Pure. */
  private[web] def parseSpeed(label: String): Option[Double] = Speeds.find(speedLabel(_) == label.trim)

  private val playheadPos = Var(Option.empty[Long])

  def playheadSignal: Signal[Option[Long]] = playheadPos.signal
  def playheadNow: Option[Long] = playheadPos.now()

  private val paused = Var(false)

  // the events layer's settings (ui.events), from the config the element was built with
  private var eventsConfig: EventsUiConfig = null

  def element(config: UiConfig): HtmlElement =
    eventsConfig = config.events
    div(
      cls := "btn-group tl-playback",
      role := "group",
      aria.label := "playback",
      button(
        typ := "button",
        cls := "btn btn--icon btn--toggle",
        aria.pressed <-- playing.signal.map(_.toString),
        child.text <-- playing.signal.map(p => if p then "⏸" else "▶"),
        title <-- playing.signal.map(p => if p then "pause" else "play"),
        aria.label <-- playing.signal.map(p => if p then "pause" else "play"),
        onClick --> { _ =>
          if playing.now() then
            pause()
            paused.set(true)
          else if paused.now() then
            paused.set(false)
            playing.set(true)
            lastTs = 0.0
            tick(0.0)
          else start()
        }
      ),
      button(
        typ := "button",
        cls := "btn btn--icon",
        cls("is-hidden") <-- playheadPos.signal.map(_.isEmpty),
        "⏹",
        title := "stop and reset",
        aria.label := "stop and reset",
        onClick --> { _ =>
          paused.set(false)
          stop()
        }
      ),
      select(
        cls := "field tl-play-speed",
        title := "playback speed",
        aria.label := "playback speed",
        controlled(
          value <-- speed.signal.map(speedLabel),
          onChange.mapToValue --> { v => parseSpeed(v).foreach(speed.set) }
        ),
        Speeds.map(s => option(value := speedLabel(s), speedLabel(s)))
      ),
      htmlTag("span")(
        cls := "tl-play-year",
        child.text <-- playheadPos.signal.map {
          case Some(ph) => Fmt.yr(ph)
          case None => ""
        }
      )
    )

  private var overlayWasOn: Boolean = false

  private def start(): Unit =
    val (sf, st) = TimelineView.currentSpan
    spanFrom = sf
    spanTo = st
    if playhead.now() < sf || playhead.now() >= st then playhead.set(sf)
    // playback reads the polities: boundaries go on for its duration (the toggle shows it) and
    // back off afterwards unless the reader had them on already
    overlayWasOn = UiState.layers.now().contains(MapLayer.Boundaries)
    if !overlayWasOn then UiState.setLayer(MapLayer.Boundaries, true)
    MapView.setPlaybackMode(true)
    // the filter's events over the span played, fetched once; each frame then draws them locally
    if eventsConfig != null && MapView.leafletMap != null then
      EventLayer.start(MapView.leafletMap, eventsConfig,
        Api.EventQuery(UiState.toFilter().copy(period = Some(Period(sf, st)))))
    playing.set(true)
    lastTs = 0.0
    tick(0.0)

  private def pause(): Unit =
    playing.set(false)
    dom.window.cancelAnimationFrame(frameId)

  private def stop(): Unit =
    playing.set(false)
    playheadPos.set(None)
    frames.drop()
    dom.window.cancelAnimationFrame(frameId)
    MapView.setPlaybackMode(false)
    EventLayer.stop()
    if !overlayWasOn then UiState.setLayer(MapLayer.Boundaries, false)

  private def tick(ts: Double): Unit =
    if playing.now() then
      if lastTs == 0.0 then lastTs = ts
      val dt = (ts - lastTs) / 1000.0
      lastTs = ts

      val totalYears = (spanTo - spanFrom).toDouble
      val yearsPerSecond = totalYears / Layout.playbackDuration * speed.now()
      val advance = (yearsPerSecond * dt).toLong.max(1)
      val next = playhead.now() + advance

      if next >= spanTo then
        playhead.set(spanTo)
        playheadPos.set(Some(spanTo))
        stop()
      else
        playhead.set(next)
        playheadPos.set(Some(next))
        EventLayer.frame(next, yearsPerSecond)
        updateMapFilter()
        frameId = dom.window.requestAnimationFrame((t: Double) => tick(t))

  /** The map follows the playhead with ONE findings request in flight, the newest frame's next: it
    * asked once per animation frame (~60/s), and every answer — in whatever order they came back —
    * re-rendered the map. A frame whose answer arrives after playback stopped is not drawn. */
  private val frames = LatestOnly[Long]((ph, done) => {
    val wh = windowHalf.now()
    val f = UiState.toFilter().copy(period = Some(Period(ph - wh, ph + wh)))
    ApiClient.findings(Api.FindingsQuery.of(f)).onComplete { r =>
      r.foreach(points => if playheadPos.now().isDefined then MapView.render(points, (ph - wh, ph + wh)))
      done()
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)
  })

  private def updateMapFilter(): Unit = frames(playhead.now())

package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.Api
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.Period
import com.histmersion.api.instance.{EventsUiConfig, UiConfig}
import com.histmersion.api.library.{Filter, GeoHit}

object TimelinePlayback:

  private val playing = Var(false)
  private val playhead = Var(0L)
  private val speed = Var(1.0)
  private val windowHalf = Var(Layout.playbackWindowHalf)

  private var frameId: Int = 0
  private var lastTs: Double = 0.0
  private var spanFrom: Long = 0
  private var spanTo: Long = 0

  /** The playback speeds offered, slowest first; 1× plays the shown span in `Layout.playbackDuration`. */
  private[web] val Speeds: List[Double] = List(0.25, 0.5, 1.0, 2.0, 4.0)

  /** How a speed reads in the menu: in plain words. Pure. */
  private[web] def speedWords(s: Double): String = s match
    case 0.25 => "Very slow"
    case 0.5  => "Slow"
    case 1.0  => "Normal speed"
    case 2.0  => "Fast"
    case 4.0  => "Very fast"
    case _    => speedLabel(s)

  /** `0.25×`, `1×`: a speed's option value. Pure. */
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
        cls := "btn btn--toggle tl-play",
        idAttr := "tl-play",
        aria.pressed <-- playing.signal.map(_.toString),
        child.text <-- playing.signal
          .combineWith(paused.signal)
          .map((p, held) => if p then "⏸ Pause" else if held then "▶ Resume" else "▶ Play"),
        title <-- playing.signal.map(p => if p then "Pause the playback" else "Play the span year by year"),
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
        cls := "btn tl-stop",
        cls("is-hidden") <-- playheadPos.signal.map(_.isEmpty),
        "⏹ Stop",
        title := "Stop and show the whole span again",
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
        Speeds.map(s => option(value := speedLabel(s), speedWords(s)))
      ),
      htmlTag("span")(
        cls := "tl-play-year",
        child.text <-- playheadPos.signal.map {
          case Some(ph) => Fmt.yr(ph)
          case None     => ""
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
    if eventsConfig != null then
      EventLayer.start(eventsConfig, Api.EventQuery(UiState.toFilter().copy(period = Some(Period(sf, st)))))
    playing.set(true)
    lastTs = 0.0
    tick(0.0)

  private def pause(): Unit =
    playing.set(false)
    dom.window.cancelAnimationFrame(frameId)

  private def stop(): Unit =
    playing.set(false)
    playheadPos.set(None)
    feed.reset()
    dom.window.cancelAnimationFrame(frameId)
    MapView.setPlaybackMode(false)
    EventLayer.stop()
    if !overlayWasOn then UiState.setLayer(MapLayer.Boundaries, false)
    // the last frame drew the playhead's window: the map shows the whole span again (findings, legend,
    // sites, pictures, events, boundaries), whether playback ran out or ⏹ stopped it
    val (sf, st) = TimelineView.currentSpan
    App.restoreSpan(sf, st)

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
        feed.frame(next, yearsPerSecond)
        frameId = dom.window.requestAnimationFrame((t: Double) => tick(t))

  /** The map follows the playhead from findings fetched ahead ([[PlaybackFeed]], #3764): it asked for each frame's
    * window (~60 requests a second) and re-drew the map whenever an answer came, so the map changed at the rate answers
    * arrived. Now one request covers [[Layout.playbackFetchSeconds]] of playback, and every frame draws its window from
    * what is held — the first frames wait for the first answer. A frame's answer that arrives after playback stopped is
    * not kept.
    */
  private object feed:
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    // `var`s: what is held and up to which year it covers; whether a request is out (and, after a
    // failure, not before when); the filter it was asked with; the generation (a stopped playback's
    // answers are dropped)
    private var held = Vector.empty[GeoHit]
    private var coveredTo: Option[Long] = None
    private var asking = false
    private var retryAt = 0.0
    private var filter: Option[Filter] = None
    private var generation = 0

    def reset(): Unit =
      generation += 1
      held = Vector.empty
      coveredTo = None
      asking = false
      filter = None

    def frame(ph: Long, yearsPerSecond: Double): Unit =
      val wh = windowHalf.now()
      val f = UiState.toFilter()
      // the filter changed while it played: what is held was asked with the old one
      if !filter.contains(f) then { reset(); filter = Some(f) }
      val chunk = PlaybackFeed.chunkYears(wh, yearsPerSecond, Layout.playbackFetchSeconds)
      if !asking && dom.window.performance.now() >= retryAt then
        PlaybackFeed.next(coveredTo, ph, wh, chunk, spanTo).foreach(ask(f, _))
      if coveredTo.exists(_ >= ph + wh) then
        MapView.playFrame(PlaybackFeed.inWindow(held, ph - wh, ph + wh), (ph - wh, ph + wh), ph, yearsPerSecond)

    private def ask(f: Filter, period: (Long, Long)): Unit =
      asking = true
      val mine = generation
      val q =
        Api.FindingsQuery.of(f.copy(period = Some(Period(period._1, period._2)), limit = Layout.playbackFetchLimit))
      ApiClient.findings(q).onComplete { r =>
        if mine == generation then
          asking = false
          r match
            case scala.util.Success(points) =>
              val lo = playhead.now() - windowHalf.now()
              val known = held.toSet
              held = PlaybackFeed.keep(held, lo).toVector ++ points.filterNot(known.contains)
              coveredTo = Some(PlaybackFeed.covered(period, points, Layout.playbackFetchLimit))
            case scala.util.Failure(_) =>
              retryAt = dom.window.performance.now() + 1000
      }

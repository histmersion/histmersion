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

  private val SPEEDS = List(0.25, 0.5, 1.0, 2.0, 4.0)

  private val playheadPos = Var(Option.empty[Long])

  def playheadSignal: Signal[Option[Long]] = playheadPos.signal
  def playheadNow: Option[Long] = playheadPos.now()

  private val paused = Var(false)

  def element(config: UiConfig): HtmlElement =
    div(
      cls := "tl-playback",
      button(
        cls <-- playing.signal.map(p => if p then "tl-btn tl-play-active" else "tl-btn"),
        child.text <-- playing.signal.map(p => if p then "⏸" else "▶"),
        title <-- playing.signal.map(p => if p then "pause" else "play"),
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
        cls := "tl-btn",
        display <-- playheadPos.signal.map(p => if p.isDefined then "" else "none"),
        "⏹",
        title := "stop and reset",
        onClick --> { _ =>
          paused.set(false)
          stop()
        }
      ),
      htmlTag("span")(
        cls := "tl-play-speed",
        child.text <-- speed.signal.map(s => s"${s}×"),
        title := "playback speed (click to cycle)",
        onClick --> { _ =>
          val cur = speed.now()
          val idx = SPEEDS.indexOf(cur)
          val next = if idx < 0 || idx >= SPEEDS.length - 1 then SPEEDS.head else SPEEDS(idx + 1)
          speed.set(next)
        }
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
    playing.set(true)
    lastTs = 0.0
    tick(0.0)

  private def pause(): Unit =
    playing.set(false)
    dom.window.cancelAnimationFrame(frameId)

  private def stop(): Unit =
    playing.set(false)
    playheadPos.set(None)
    dom.window.cancelAnimationFrame(frameId)
    MapView.setPlaybackMode(false)
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
        updateMapFilter()
        frameId = dom.window.requestAnimationFrame((t: Double) => tick(t))

  private def updateMapFilter(): Unit =
    val ph = playhead.now()
    val wh = windowHalf.now()
    val f = UiState.toFilter().copy(period = Some(Period(ph - wh, ph + wh)))
    ApiClient.findings(Api.FindingsQuery.of(f)).foreach { points =>
      MapView.render(points, (ph - wh, ph + wh))
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import histdb.web.ui.Layout
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

  def element(config: UiConfig): HtmlElement =
    div(
      cls := "tl-playback",
      button(
        cls := "tl-play-btn",
        child.text <-- playing.signal.map(p => if p then "⏸" else "▶"),
        title <-- playing.signal.map(p => if p then "pause" else "play"),
        onClick --> { _ =>
          if playing.now() then stop()
          else start()
        }
      ),
      button(
        cls := "tl-play-btn tl-play-stop",
        "⏹",
        title := "stop and reset",
        onClick --> { _ =>
          stop()
          val (sf, st) = TimelineView.currentSpan
          playhead.set(sf)
        }
      ),
      htmlTag("span")(
        cls := "tl-play-speed",
        child.text <-- speed.signal.map(s => s"${s}×"),
        title := "playback speed",
        onClick --> { _ =>
          val cur = speed.now()
          val idx = SPEEDS.indexOf(cur)
          val next = if idx < 0 || idx >= SPEEDS.length - 1 then SPEEDS.head else SPEEDS(idx + 1)
          speed.set(next)
        }
      ),
      div(
        cls := "tl-play-progress",
        div(
          cls := "tl-play-bar",
          width <-- playhead.signal.map { ph =>
            val (sf, st) = TimelineView.currentSpan
            if st <= sf then "0%"
            else
              val pct = ((ph - sf).toDouble / (st - sf).toDouble * 100).max(0).min(100)
              s"${pct}%"
          }
        ),
        onClick --> { ev =>
          val rect = ev.currentTarget.asInstanceOf[dom.Element].getBoundingClientRect()
          val pct = (ev.clientX - rect.left) / rect.width
          val (sf, st) = TimelineView.currentSpan
          val yr = sf + ((st - sf) * pct).toLong
          playhead.set(yr.max(sf).min(st))
          if playing.now() then updateMapFilter()
        }
      ),
      htmlTag("span")(
        cls := "tl-play-year muted",
        child.text <-- playhead.signal.map(Fmt.yr)
      )
    )

  private var overlayWasOn: Boolean = false

  private def start(): Unit =
    val (sf, st) = TimelineView.currentSpan
    spanFrom = sf
    spanTo = st
    if playhead.now() < sf || playhead.now() >= st then playhead.set(sf)
    overlayWasOn = MapView.isOverlayOn
    if !overlayWasOn then MapView.toggleOverlay()
    MapView.setPlaybackMode(true)
    playing.set(true)
    lastTs = 0.0
    tick(0.0)

  private def stop(): Unit =
    playing.set(false)
    dom.window.cancelAnimationFrame(frameId)
    MapView.setPlaybackMode(false)
    if !overlayWasOn then MapView.toggleOverlay()

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
        stop()
      else
        playhead.set(next)
        updateMapFilter()
        frameId = dom.window.requestAnimationFrame((t: Double) => tick(t))

  private def updateMapFilter(): Unit =
    val ph = playhead.now()
    val wh = windowHalf.now()
    val f = UiState.toFilter().copy(period = Some(Period(ph - wh, ph + wh)))
    ApiClient.findings(Api.FindingsQuery.of(f)).foreach { points =>
      MapView.render(points, (ph - wh, ph + wh))
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

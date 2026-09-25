package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

import com.raquo.laminar.codecs.StringAsIsCodec

/** Gazetteer place picker for the filter bar. Ported from places.js (241 lines).
  *
  * Two kinds of chips: gazetteer ids (`UiState.placeIds`) and free-text names (`UiState.places`).
  * Suggestions come from `ApiClient.places(q, asOf, limit)`. Selected places draw polygons on the
  * map via `L.geoJSON` on a `LayerGroup`. */
object PlacesInput:

  /** Cached label/kind/contested for gazetteer ids. */
  private case class KnownPlace(label: String, kind: String, contested: Boolean)
  private val known = scala.collection.mutable.Map.empty[String, KnownPlace]

  /** Suggestion items: either a gazetteer place or the "text:" fallback row. */
  private case class SuggestItem(
      id: String,
      label: String,
      kind: String,
      findings: Option[Int],
      contested: Boolean,
      textFallback: Boolean,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None
  )

  private var items: List[SuggestItem]   = Nil
  private var activeIdx: Int             = -1
  private var timer: Int                 = 0
  private var reqId: Int                 = 0
  private var inputEl: dom.HTMLInputElement = null

  private val itemsVar   = Var(List.empty[SuggestItem])
  private val activeVar  = Var(-1)
  private val menuHidden = Var(true)

  // The chip list renders from UiState directly
  private val chipsSignal: Signal[List[(String, String, Boolean, Boolean)]] =
    UiState.placeIds.signal.combineWith(UiState.places.signal).map { (ids, texts) =>
      val gazChips = ids.map { id =>
        val k = known.getOrElse(id, KnownPlace(id, "", contested = false))
        (id, k.label, k.contested, false) // (key, label, contested, isText)
      }
      val textChips = texts.map(v => (v, v, false, true))
      gazChips ++ textChips
    }

  private var layer: LayerGroup    = null
  private var previewLayer: LayerGroup = null
  private var previewId: String    = null
  private var drawn: String        = ""
  private var uiConfig: UiConfig   = null
  private var lastTextJson: String = "[]"

  private def ids(): List[String]   = UiState.placeIds.now()
  private def texts(): List[String] = UiState.places.now()

  /** The year the gazetteer is read at: the middle of the selected span. */
  private def asOf(): Option[Long] =
    val f = UiState.from.now()
    val t = UiState.to.now()
    (f, t) match
      case (None, None)       => None
      case (None, Some(b))    => Some(b)
      case (Some(a), None)    => Some(a)
      case (Some(a), Some(b)) => Some(Math.round((a.toDouble + b.toDouble) / 2))

  private def flag(contested: Boolean, label: String): Boolean =
    contested && !Option(label).getOrElse("").toLowerCase.contains("contested")

  // ── chips ──────────────────────────────────────────────────────────────────

  private def close(): Unit =
    menuHidden.set(true)
    itemsVar.set(Nil)
    activeVar.set(-1)
    items = Nil
    activeIdx = -1

  private def setIds(next: List[String]): Unit =
    UiState.set("placeIds" -> next)(push = false)

  private def addId(id: String): Unit =
    if id.nonEmpty && !ids().contains(id) then setIds(ids() :+ id)
    close()
    if inputEl != null then inputEl.value = ""

  private def addText(v: String): Unit =
    val t = Option(v).getOrElse("").trim
    if t.nonEmpty && !texts().contains(t) then
      UiState.set("places" -> (texts() :+ t))(push = false)
    close()
    if inputEl != null then inputEl.value = ""

  private def removeIdAt(i: Int): Unit =
    setIds(ids().zipWithIndex.collect { case (v, j) if j != i => v })

  private def removeTextAt(i: Int): Unit =
    UiState.set("places" -> texts().zipWithIndex.collect { case (v, j) if j != i => v })(push = false)

  private def pick(it: SuggestItem): Unit =
    if it.textFallback then addText(it.label)
    else addId(it.id)

  // ── suggestions ────────────────────────────────────────────────────────────

  private def suggest(): Unit =
    if inputEl == null then ()
    else
      val q = inputEl.value.trim
      if q.isEmpty then close()
      else
        reqId += 1
        val mine = reqId
        val show: List[PlaceHit] => Unit = rows =>
          if mine != reqId || inputEl == null || dom.document.activeElement != inputEl then ()
          else
            val gaz = Option(rows).getOrElse(Nil)
              .filterNot(p => ids().contains(p.id))
              .map { p =>
                known(p.id) = KnownPlace(p.label, p.kind, p.contested)
                SuggestItem(p.id, p.label, p.kind, Some(p.findings), p.contested, textFallback = false,
                  p.periodStart, p.periodEnd)
              }
            // the fallback row is always last unless it duplicates an existing text chip
            val fallback =
              if texts().contains(q) then Nil
              else List(SuggestItem("", q, "", None, contested = false, textFallback = true))
            items = gaz ++ fallback
            activeIdx = -1
            itemsVar.set(items)
            activeVar.set(-1)
            menuHidden.set(false)

        ApiClient.places(Some(q), asOf(), Some(uiConfig.facetSuggestions)).onComplete {
          case Success(rows) => show(rows)
          case Failure(_)    => show(Nil)
        }

  private def schedule(): Unit =
    if timer != 0 then dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => suggest(), uiConfig.debounce.suggestMs.toDouble).toInt

  // ── polygon overlay ────────────────────────────────────────────────────────

  /** Draw every selected place's shape. Re-fetched when the selection or span changed. */
  private def draw(fit: Boolean): Unit =
    if layer == null then ()
    else
      val key = js.JSON.stringify(js.Array(ids().toJSArray, asOf().map(_.toDouble).getOrElse(0.0): js.Any))
      if key == drawn then ()
      else
        drawn = key
        layer.clearLayers()
        val currentIds = ids()
        val pending: List[Future[Option[(String, PlaceDetail)]]] = currentIds.map { id =>
          ApiClient.place(id, asOf()).map(detail => Some((id, detail))).recover { case _ => None }
        }
        Future.sequence(pending).foreach { results =>
          val bounds = scala.collection.mutable.ListBuffer.empty[LatLngBounds]
          results.flatten.foreach { pair =>
            val (id, d) = pair
            known(id) = KnownPlace(d.place.label, d.place.kind, d.contested)
            d.geometry.foreach { geoStr =>
              val geo = js.JSON.parse(geoStr)
              val uncertain = d.contested
              val style = js.Dynamic.literal(
                color = "#c2410c",
                weight = (if uncertain then 1.5 else 2.0),
                opacity = (if uncertain then 0.7 else 0.9),
                fill = true,
                fillOpacity = (if uncertain then 0.03 else 0.06),
                dashArray = (if uncertain then "8 6" else null)
              )
              val opts = js.Dynamic.literal(
                style = style,
                pointToLayer = { (_: js.Any, latlng: js.Dynamic) =>
                  L.circleMarker(
                    js.Array(latlng.lat.asInstanceOf[Double], latlng.lng.asInstanceOf[Double]),
                    js.Dynamic.literal(
                      radius = uiConfig.markerRadius + 3,
                      color = "#c2410c",
                      fill = false
                    ).asInstanceOf[js.Object]
                  )
                }: js.Function2[js.Any, js.Dynamic, CircleMarker]
              )
              val g = L.geoJSON(geo, opts.asInstanceOf[js.Object])
              val tooltipText = d.place.label +
                (if flag(d.contested, d.place.label) then " (contested)" else "")
              g.bindTooltip(tooltipText, js.Dynamic.literal(sticky = true).asInstanceOf[js.Object])
              g.addTo(layer)
              bounds += g.getBounds()
            }
          }
          if fit && bounds.nonEmpty then
            val all = bounds.reduceLeft((a, b) => a.extend(b))
            if all.isValid() then
              MapView.leafletMap.fitBounds(
                all,
                js.Dynamic.literal(padding = js.Array(20, 20), maxZoom = 9).asInstanceOf[js.Object]
              )
        }

  // ── public API ─────────────────────────────────────────────────────────────

  def init(config: UiConfig): HtmlElement =
    uiConfig = config
    lastTextJson = js.JSON.stringify(texts().toJSArray)

    div(
      cls := "chips gazetteer",
      // chip list
      span(
        cls := "chips-list",
        children <-- chipsSignal.map { chips =>
          chips.zipWithIndex.map { case ((key, label, contested, isText), idx) =>
            if isText then
              Chip(label, prefix = Some("text: "), extraCls = "place place-text",
                tip = Some("matched as text against the finding's region name"),
                remove = Some { () =>
                  // text chips come after gaz chips; compute text index
                  removeTextAt(idx - ids().length)
                  if inputEl != null then inputEl.focus()
                })
            else
              Chip(label, tip = Some(key), extraCls = s"place${if contested then " contested-chip" else ""}",
                mods = (if flag(contested, label) then
                          span(cls := "contested", title := "disputed extent", " (contested)")
                        else emptyNode),
                remove = Some { () =>
                  removeIdAt(idx)
                  if inputEl != null then inputEl.focus()
                })
          }
        }
      ),
      // input
      input(
        cls := "chip-input",
        typ := "text",
        idAttr := "f-place-search",
        autoComplete := "off",
        spellCheck := false,
        role := "combobox",
        htmlAttr("aria-autocomplete", StringAsIsCodec) := "list",
        aria.expanded <-- menuHidden.signal.map(h => !h),
        aria.label := "place",
        placeholder := "e.g. Anatolia",
        onMountCallback(ctx => inputEl = ctx.thisNode.ref),
        onInput --> { _ => schedule() },
        onFocus --> { _ =>
          if inputEl != null && inputEl.value.trim.nonEmpty then schedule()
        },
        onBlur --> { _ => close() },
        onKeyDown --> { e =>
          val key = e.key
          if key == "ArrowDown" || key == "ArrowUp" then
            if menuHidden.now() then
              if inputEl != null && inputEl.value.trim.nonEmpty then suggest()
            else
              e.preventDefault()
              val d = if key == "ArrowDown" then 1 else -1
              val cur = activeVar.now()
              val len = items.length
              val next =
                if cur < 0 then (if d > 0 then 0 else len - 1)
                else (cur + d + len) % len
              activeIdx = next
              activeVar.set(next)
          else if key == "Enter" then
            e.preventDefault()
            val a = activeVar.now()
            if a >= 0 && a < items.length then pick(items(a))
            else if inputEl != null && inputEl.value.trim.nonEmpty then addText(inputEl.value)
          else if key == "Escape" then
            if !menuHidden.now() then
              e.preventDefault()
              e.stopPropagation()
              close()
          else if key == "Backspace" && inputEl != null && inputEl.value.isEmpty
            && (ids().nonEmpty || texts().nonEmpty) then
            e.preventDefault()
            if texts().nonEmpty then removeTextAt(texts().length - 1)
            else removeIdAt(ids().length - 1)
        }
      ),
      // suggestion dropdown
      ul(
        cls := "chip-suggest",
        role := "listbox",
        hidden <-- menuHidden.signal,
        children <-- itemsVar.signal.combineWith(activeVar.signal).map { (its, act) =>
          its.zipWithIndex.map { (it, i) =>
            if it.textFallback then
              li(
                role := "option",
                cls := s"text-fallback${if i == act then " active" else ""}",
                htmlAttr("aria-selected", StringAsIsCodec) := (i == act).toString,
                span(cls := "v", s"""text: "${it.label}""""),
                " ",
                span(cls := "c", "match the region name"),
                onMouseDown.preventDefault --> { _ => pick(it) },
                onMouseOver --> { _ => activeIdx = i; activeVar.set(i) }
              )
            else
              val period = Fmt.periodStr(it.periodStart, it.periodEnd)
              val findingsText = it.findings match
                case Some(n) if n > 0 => s"$n finding${if n != 1 then "s" else ""}"
                case _                => "no findings"
              val detail = List(Fmt.display(it.kind), findingsText,
                if period.nonEmpty then period else "").filter(_.nonEmpty).mkString(" · ")
              li(
                role := "option",
                cls("active") <-- activeVar.signal.map(_ == i),
                htmlAttr("aria-selected", StringAsIsCodec) <-- activeVar.signal.map(a => (a == i).toString),
                span(
                  cls := "v",
                  it.label,
                  (if flag(it.contested, it.label) then
                     span(cls := "contested", title := "disputed extent", " (contested)")
                   else emptyNode)
                ),
                " ",
                span(cls := "c", detail),
                onMouseDown.preventDefault --> { _ => pick(it) },
                onMouseOver --> { _ =>
                  activeIdx = i; activeVar.set(i)
                  if it.id.nonEmpty then PlacesInput.preview(it.id, asOf())
                }
              )
          }
        }
      ),
      // Wire to state changes: redraw when ids/places change
      onMountCallback { _ =>
        layer = L.layerGroup().addTo(MapView.leafletMap)
        draw(fit = true)

        // Subscribe to UiState changes
        var lastIds = js.JSON.stringify(ids().toJSArray)
        UiState.signal.foreach { _ =>
          val nowIds = js.JSON.stringify(ids().toJSArray)
          val nowTexts = js.JSON.stringify(texts().toJSArray)
          if nowTexts != lastTextJson then lastTextJson = nowTexts
          val added = nowIds != lastIds && ids().length > js.JSON.parse(lastIds).asInstanceOf[js.Array[?]].length
          lastIds = nowIds
          draw(fit = added)
        }(using unsafeWindowOwner)
      }
    )

  /** Re-render chips from state (called by FilterBar after "clear all"). */
  def sync(): Unit =
    draw(fit = false)

  /** Draw ONE place's shape as a TRANSIENT overlay and fit the map to it. Toggle behavior: same id
    * clears it. */
  def preview(id: String, asOfYear: Option[Long]): Unit =
    if layer == null then ()
    else
      if previewLayer == null then previewLayer = L.layerGroup().addTo(MapView.leafletMap)
      val wasShowing = previewId == id && previewLayer.getLayers().length > 0
      previewLayer.clearLayers()
      if wasShowing then
        previewId = null
      else
        previewId = id
        val effectiveAsOf = asOfYear.orElse(asOf())
        ApiClient.place(id, effectiveAsOf).foreach { d =>
          known(id) = KnownPlace(d.place.label, d.place.kind, d.contested)
          d.geometry.foreach { geoStr =>
            val geo = js.JSON.parse(geoStr)
            val style = js.Dynamic.literal(
              color = "#0ea5e9",
              weight = 2,
              opacity = 0.9,
              fill = true,
              fillOpacity = 0.05,
              dashArray = (if d.contested then "6 4" else "2 4")
            )
            val opts = js.Dynamic.literal(
              style = style,
              pointToLayer = { (_: js.Any, latlng: js.Dynamic) =>
                L.circleMarker(
                  js.Array(latlng.lat.asInstanceOf[Double], latlng.lng.asInstanceOf[Double]),
                  js.Dynamic.literal(
                    radius = uiConfig.markerRadius + 3,
                    color = "#0ea5e9",
                    fill = false
                  ).asInstanceOf[js.Object]
                )
              }: js.Function2[js.Any, js.Dynamic, CircleMarker]
            )
            val g = L.geoJSON(geo, opts.asInstanceOf[js.Object])
            val tooltipText = d.place.label +
              (if flag(d.contested, d.place.label) then " (contested)" else "")
            g.bindTooltip(tooltipText, js.Dynamic.literal(sticky = true).asInstanceOf[js.Object])
            g.addTo(previewLayer)
            val bb = g.getBounds()
            if bb.isValid() then
              MapView.leafletMap.fitBounds(
                bb,
                js.Dynamic.literal(padding = js.Array(20, 20), maxZoom = 9).asInstanceOf[js.Object]
              )
          }
        }

  /** What a gazetteer id is CALLED. Falls back to the id when the label is not cached. */
  def labelOf(id: String): String =
    known.get(id).map(_.label).getOrElse(id)

end PlacesInput

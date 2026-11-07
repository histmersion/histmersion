package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.MapStack
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

import com.raquo.laminar.codecs.StringAsIsCodec
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.Label
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.Place

/** Gazetteer place picker for the filter bar. Ported from places.js (241 lines).
  *
  * Two kinds of chips: gazetteer ids (`UiState.placeIds`) and free-text names (`UiState.places`). Suggestions come from
  * `ApiClient.places(q, asOf, limit)`. Selected places draw polygons on the map as shapes ([[ShapeSet]] "place-pick").
  */
object PlacesInput:
  type FitToView = FitToView.T
  object FitToView extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerVisible = LayerVisible.T
  object LayerVisible extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceId = PlaceId.T
  object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Cached label/kind/contested for gazetteer ids. */

  type LearnId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val LearnId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId
  type PreviewId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val PreviewId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId
  type DrawPlaceId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val DrawPlaceId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId
  type RememberId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val RememberId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId
  type RememberLabel = _root_.com.histmersion.web.PlacesInput.KnownPlace.Label
  val RememberLabel: _root_.com.histmersion.web.PlacesInput.KnownPlace.Label.type = _root_.com.histmersion.web.PlacesInput.KnownPlace.Label
  type RememberKind = _root_.com.histmersion.web.PlacesInput.KnownPlace.Kind
  val RememberKind: _root_.com.histmersion.web.PlacesInput.KnownPlace.Kind.type = _root_.com.histmersion.web.PlacesInput.KnownPlace.Kind
  type RememberContested = _root_.com.histmersion.web.PlacesInput.KnownPlace.Contested
  val RememberContested: _root_.com.histmersion.web.PlacesInput.KnownPlace.Contested.type = _root_.com.histmersion.web.PlacesInput.KnownPlace.Contested
  type ToggleFromMapId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val ToggleFromMapId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId
  type EnsureKnownId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val EnsureKnownId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId
  type ChipTextSignalId = _root_.com.histmersion.web.PlacesInput.PlaceId
  val ChipTextSignalId: _root_.com.histmersion.web.PlacesInput.PlaceId.type = _root_.com.histmersion.web.PlacesInput.PlaceId

  private case class KnownPlace(label: KnownPlace.Label, kind: KnownPlace.Kind, contested: KnownPlace.Contested)

  object KnownPlace:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  private val known = BoundedCache[String, KnownPlace](com.histmersion.web.ui.Layout.placeLabelCacheSize)

  /** The labels' figures, for [[Diagnostics]]. */
  def cacheStats: BoundedCache.Stats = BoundedCache.stats(known)

  /** Moves whenever `known` learns a place, so every chip that names one re-reads its label. */
  private val knownVersion = Var(0)

  private def learn(id: com.histmersion.web.PlacesInput.LearnId, k: KnownPlace): Unit =
    if !known.get(id).contains(k) then
      known.put(id, k)
      knownVersion.update(_ + 1)

  /** Suggestion items: either a gazetteer place or the "text:" fallback row. */
  private case class SuggestItem(
      id: SuggestItem.Id,
      label: SuggestItem.Label,
      kind: SuggestItem.Kind,
      findings: Option[SuggestItem.Findings],
      contested: SuggestItem.Contested,
      textFallback: SuggestItem.TextFallback,
      periodStart: Option[SuggestItem.PeriodStart] = None,
      periodEnd: Option[SuggestItem.PeriodEnd] = None
  )

  object SuggestItem:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextFallback = TextFallback.T
    object TextFallback extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private var items: List[SuggestItem] = Nil
  private var activeIdx: Int = -1
  private var timer: Int = 0
  // the suggestions request a late answer must belong to
  private val latest = Latest()
  private var inputEl: dom.HTMLInputElement = null

  private val itemsVar = Var(List.empty[SuggestItem])
  private val activeVar = Var(-1)
  private val menuHidden = Var(true)

  // The chip list renders from UiState directly
  private val chipsSignal: Signal[List[(com.histmersion.web.PlacesInput.PlaceId, com.histmersion.api.UiLabels.Text, com.histmersion.web.PlacesInput.KnownPlace.Contested, com.histmersion.web.PlacesInput.SuggestItem.TextFallback)]] =
    UiState.placeIds.signal.combineWith(UiState.places.signal, knownVersion.signal).map { (ids, texts, _) =>
      val gazChips = ids.map { id =>
        ensureKnown(id)
        val k = known.get(id).getOrElse(KnownPlace("…", "", contested = false))
        (id, k.label, k.contested, false) // (key, label, contested, isText)
      }
      val textChips = texts.map(v => (v, v, false, true))
      gazChips ++ textChips
    }

  // the chosen places' and the previewed place's drawing: shapes on the map, a point-only region's name
  // as a marker; `layer` is whether the map is up for them
  private var layer: com.histmersion.web.PlacesInput.LayerVisible = com.histmersion.web.PlacesInput.LayerVisible(false)
  private val pickNames = scala.collection.mutable.ListBuffer.empty[DomMarker]
  private val previewNames = scala.collection.mutable.ListBuffer.empty[DomMarker]
  private val drawnPlaces = scala.collection.mutable.HashMap.empty[String, (String, String, String)]
  private var previewShown = false
  private var previewId: com.histmersion.web.PlacesInput.PreviewId = null
  private var drawn: String = ""
  private var uiConfig: UiConfig = null
  private var lastTextJson: String = "[]"

  private def ids(): List[com.histmersion.web.PlacesInput.PlaceId] = UiState.placeIds.now()
  private def texts(): List[com.histmersion.api.Api.Search.Text] = UiState.places.now()

  /** The year the gazetteer is read at: the middle of the selected span. */
  private def asOf(): Option[com.histmersion.api.Years.Astronomical] =
    val f = UiState.from.now()
    val t = UiState.to.now()
    (f, t) match
      case (None, None)       => None
      case (None, Some(b))    => Some(b)
      case (Some(a), None)    => Some(a)
      case (Some(a), Some(b)) => Some(Math.round((a.toDouble + b.toDouble) / 2))

  private def flag(contested: com.histmersion.web.PlacesInput.KnownPlace.Contested, label: com.histmersion.api.UiLabels.Text): Boolean =
    contested && !Option(label).getOrElse("").toLowerCase.contains("contested")

  // ── chips ──────────────────────────────────────────────────────────────────

  private def close(): Unit =
    menuHidden.set(true)
    itemsVar.set(Nil)
    activeVar.set(-1)
    items = Nil
    activeIdx = -1

  private def setIds(next: List[com.histmersion.web.PlacesInput.PlaceId]): Unit =
    UiState.set(UiState.placeIds := next)()

  private def addId(id: com.histmersion.web.PlacesInput.PlaceId): Unit =
    if id.nonEmpty && !ids().contains(id) then setIds(ids() :+ id)
    close()
    if inputEl != null then inputEl.value = ""

  private def addText(v: com.histmersion.api.Api.Search.Text): Unit =
    val t = Option(v).getOrElse("").trim
    if t.nonEmpty && !texts().contains(t) then UiState.set(UiState.places := texts() :+ t)()
    close()
    if inputEl != null then inputEl.value = ""

  private def removeIdAt(i: Int): Unit =
    setIds(ids().zipWithIndex.collect { case (v, j) if j != i => v })

  private def removeTextAt(i: Int): Unit =
    UiState.set(UiState.places := texts().zipWithIndex.collect { case (v, j) if j != i => v })()

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
        val show: List[Place.Hit] => Unit = rows =>
          if inputEl == null || dom.document.activeElement != inputEl then ()
          else
            val gaz = Option(rows)
              .getOrElse(Nil)
              .filterNot(p => ids().contains(p.id))
              .map { p =>
                learn(p.id, KnownPlace(p.label, p.kind, p.contested))
                SuggestItem(
                  p.id,
                  p.label,
                  p.kind,
                  Some(p.findings),
                  p.contested,
                  textFallback = com.histmersion.web.PlacesInput.SuggestItem.TextFallback(false),
                  p.periodStart,
                  p.periodEnd
                )
              }
            // the fallback row is always last unless it duplicates an existing text chip
            val fallback =
              if texts().contains(q) then Nil
              else List(SuggestItem(com.histmersion.web.PlacesInput.SuggestItem.Id(""), q, com.histmersion.web.PlacesInput.SuggestItem.Kind(""), None, contested = com.histmersion.web.PlacesInput.SuggestItem.Contested(false), textFallback = com.histmersion.web.PlacesInput.SuggestItem.TextFallback(true)))
            items = gaz ++ fallback
            activeIdx = -1
            itemsVar.set(items)
            activeVar.set(-1)
            menuHidden.set(false)

        latest.onCurrent(ApiClient.places(Some(q), asOf(), Some(uiConfig.facetSuggestions))) {
          case Success(rows) => show(rows)
          case Failure(_)    => show(Nil)
        }

  private def schedule(): Unit =
    if timer != 0 then dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => suggest(), uiConfig.debounce.suggestMs.toDouble).toInt

  // ── polygon overlay ────────────────────────────────────────────────────────

  /** The chosen places' shapes (`.hd-place-pick`'s colour; a disputed extent dashed and fainter), and the hovered
    * suggestion's (`.hd-place-preview`, dotted). A point is drawn as a site's dot.
    */
  private def pickSet: ShapeSet = TheMap.surface.shapes(
    com.histmersion.web.map.MapSurface.ShapesId("place-pick"),
    ShapeStyle(
      fillOpacity = 0.06,
      lineWidth = com.histmersion.web.map.ShapeStyle.LineWidth(2),
      lineOpacity = 0.9,
      colour = Some(com.histmersion.web.ui.ThemeTokens(com.histmersion.web.ui.ThemeTokens.Name("--place-pick"))),
      dash = List(com.histmersion.web.map.ShapeStyle.Dash(4), com.histmersion.web.map.ShapeStyle.Dash(3)),
      dashedLineOpacity = Some(0.7),
      circleRadius = uiConfig.markerRadius.toDouble,
      interactive = com.histmersion.web.map.ShapeStyle.Interactive(true)
    )
  )
  private def previewSet: ShapeSet = TheMap.surface.shapes(
    com.histmersion.web.map.MapSurface.ShapesId("place-preview"),
    ShapeStyle(
      fillOpacity = 0.05,
      lineWidth = com.histmersion.web.map.ShapeStyle.LineWidth(2),
      lineOpacity = 0.9,
      colour = Some(com.histmersion.web.ui.ThemeTokens(com.histmersion.web.ui.ThemeTokens.Name("--place-preview"))),
      dash = List(com.histmersion.web.map.ShapeStyle.Dash(1), com.histmersion.web.map.ShapeStyle.Dash(2)),
      circleRadius = uiConfig.markerRadius.toDouble,
      interactive = true
    )
  )

  /** A place's geometry on the map: its shape (a point-only region or a people: its name instead). */
  private def drawPlace(
      id: com.histmersion.web.PlacesInput.DrawPlaceId,
      d: PlaceDetail,
      geo: js.Dynamic,
      names: scala.collection.mutable.ListBuffer[DomMarker],
      at: () => Option[com.histmersion.api.Years.Astronomical]
  ): Option[ShapeFeature] =
    drawnPlaces(id) = (id, d.place.label, d.place.kind)
    val t = if js.isUndefined(geo.selectDynamic("type")) then "" else geo.selectDynamic("type").toString
    if (t == "Point" || t == "MultiPoint") && PlaceGlyph.of(d.place.kind, com.histmersion.web.PlaceGlyph.GeometryType("Point")) == PlaceGlyph.Label then
      val c = if t == "Point" then geo.coordinates else geo.coordinates.selectDynamic("0")
      val m = PlaceGlyph.labelMarker(
        c.selectDynamic("1").asInstanceOf[Double],
        c.selectDynamic("0").asInstanceOf[Double],
        d.place.label,
        d.place.kind
      )
      PlaceHover.bind(m, id, d.place.label, d.place.kind, at)
      TheMap.pane(MapStack.Pane.AreaPoints).add(m)
      names += m
      None
    else Some(ShapeFeature(id, geo, dashed = d.contested))

  private def clearNames(names: scala.collection.mutable.ListBuffer[DomMarker]): Unit =
    names.foreach(m => TheMap.pane(MapStack.Pane.AreaPoints).remove(m))
    names.clear()

  /** Draw every selected place's shape. Re-fetched when the selection or span changed. */
  private def draw(fit: com.histmersion.web.PlacesInput.FitToView): Unit =
    if !layer then ()
    else
      val key = js.JSON.stringify(js.Array(ids().toJSArray, asOf().map(_.toDouble).getOrElse(0.0): js.Any))
      if key == drawn then ()
      else
        drawn = key
        val currentIds = ids()
        val pending: List[Future[Option[(com.histmersion.web.PlacesInput.PlaceId, PlaceDetail)]]] = currentIds.map { id =>
          ApiClient.place(id, asOf()).map(detail => Some((id, detail))).recover { case _ => None }
        }
        Future.sequence(pending).foreach { results =>
          if drawn == key then
            clearNames(pickNames)
            val bounds = scala.collection.mutable.ListBuffer.empty[GeoBounds]
            val shapes = results.flatten.flatMap { (id, d) =>
              learn(id, KnownPlace(d.place.label, d.place.kind, d.contested))
              d.geometry.flatMap { geoStr =>
                val geo = js.JSON.parse(geoStr)
                GeoBounds.ofGeometry(geo).foreach(bounds += _)
                drawPlace(id, d, geo, pickNames, () => asOf())
              }
            }
            pickSet.set(shapes)
            if fit && bounds.nonEmpty then
              val all = bounds.reduceLeft(_.extend(_))
              if all.valid then TheMap.surface.fitBounds(all, com.histmersion.web.map.MapSurface.FitBoundsPadding(20), com.histmersion.web.map.MapSurface.MaxZoom(9))
        }

  // ── public API ─────────────────────────────────────────────────────────────

  def init(config: UiConfig): HtmlElement =
    uiConfig = config
    Preview.placeAsOf = () => asOf() // a place chip's card reads the gazetteer at the map's year
    lastTextJson = js.JSON.stringify(texts().toJSArray)

    div(
      cls := "field chips gazetteer",
      // chip list
      span(
        cls := "chips-list",
        children <-- chipsSignal.map { chips =>
          chips.zipWithIndex.map { case ((key, label, contested, isText), idx) =>
            if isText then
              Chip(
                label,
                prefix = Some(com.histmersion.web.Chip.Prefix("text: ")),
                extraCls = com.histmersion.web.Chip.ExtraClass("place place-text"),
                tip = Some("matched as text against the finding's region name"),
                remove = Some { () =>
                  // text chips come after gaz chips; compute text index
                  removeTextAt(idx - ids().length)
                  if inputEl != null then inputEl.focus()
                }
              )
            else
              Chip(
                label,
                extraCls = s"place${if contested then " contested-chip" else ""}",
                mods = List[Modifier[HtmlElement]](
                  placeCard(key, label),
                  if flag(contested, label) then span(cls := "contested", title := "disputed extent", " (contested)")
                  else emptyNode
                ),
                remove = Some { () =>
                  removeIdAt(idx)
                  if inputEl != null then inputEl.focus()
                }
              )
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
        // unmounted: the pending debounce goes and an answer still on its way is dropped
        onMountUnmountCallback(
          mount = ctx => inputEl = ctx.thisNode.ref,
          unmount = _ => {
            if timer != 0 then dom.window.clearTimeout(timer)
            timer = 0
            latest.cancel()
            inputEl = null
          }
        ),
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
            && (ids().nonEmpty || texts().nonEmpty)
          then
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
              val detail = List(Fmt.display(it.kind), findingsText, if period.nonEmpty then period else "")
                .filter(_.nonEmpty)
                .mkString(" · ")
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
        layer = TheMap.isUp
        if layer then
          PlaceHover.shapes(com.histmersion.web.PlaceHover.ShapeSetName("place-pick"), PlaceHover.Places(drawnPlaces.get, () => asOf()))
          PlaceHover.shapes(com.histmersion.web.PlaceHover.ShapeSetName("place-preview"), PlaceHover.Places(drawnPlaces.get, () => asOf()))
        draw(fit = com.histmersion.web.PlacesInput.FitToView(true))

        // Subscribe to UiState changes
        var lastIds = js.JSON.stringify(ids().toJSArray)
        UiState.signal.foreach { _ =>
          val nowIds = js.JSON.stringify(ids().toJSArray)
          val nowTexts = js.JSON.stringify(texts().toJSArray)
          if nowTexts != lastTextJson then lastTextJson = nowTexts
          val added = nowIds != lastIds && ids().length > js.JSON.parse(lastIds).asInstanceOf[js.Array[?]].length
          lastIds = nowIds
          // a place chosen ON the map is already in view: zooming to it would move the map under the pointer
          draw(fit = added && !chosenOnMap)
          chosenOnMap = false
        }(using unsafeWindowOwner)
      }
    )

  /** Re-render chips from state (called by FilterBar after "clear all"). */
  def sync(): Unit =
    draw(fit = com.histmersion.web.PlacesInput.FitToView(false))

  /** Draw ONE place's shape as a TRANSIENT overlay and fit the map to it. Toggle behavior: same id clears it.
    */
  def preview(id: com.histmersion.web.PlacesInput.PreviewId, asOfYear: Option[com.histmersion.api.Years.Astronomical]): Unit =
    if !layer then ()
    else
      val wasShowing = previewId == id && previewShown
      previewSet.clear()
      clearNames(previewNames)
      previewShown = false
      if wasShowing then previewId = null
      else
        previewId = id
        val effectiveAsOf = asOfYear.orElse(asOf())
        ApiClient.place(id, effectiveAsOf).foreach { d =>
          if previewId == id then
            learn(id, KnownPlace(d.place.label, d.place.kind, d.contested))
            d.geometry.foreach { geoStr =>
              val geo = js.JSON.parse(geoStr)
              previewSet.set(
                drawPlace(id, d, geo, previewNames, () => effectiveAsOf).map(_.modify(_.dashed).setTo(true)).toList
              )
              previewShown = true
              GeoBounds.ofGeometry(geo).filter(_.valid).foreach(bb => TheMap.surface.fitBounds(bb, 20, 9))
            }
        }

  // `var`: the next redraw follows a place chosen on the map, so it must not fit the map to it
  private var chosenOnMap = false

  /** Remember what a gazetteer id is called, so its chip reads as a name the moment it appears. */
  def remember(id: com.histmersion.web.PlacesInput.RememberId, label: com.histmersion.web.PlacesInput.RememberLabel, kind: com.histmersion.web.PlacesInput.RememberKind, contested: com.histmersion.web.PlacesInput.RememberContested = com.histmersion.web.PlacesInput.RememberContested(false)): Unit =
    if label.trim.nonEmpty then learn(id, KnownPlace(label, kind, contested))

  /** A click on place `id` on the map: it becomes the place filter — the same `UiState.placeIds` the box above sets, so
    * the lists narrow to it and its descendants and its chip appears — or, when it already is chosen, it is unchosen.
    * Returns whether it is chosen now.
    */
  def toggleFromMap(id: com.histmersion.web.PlacesInput.ToggleFromMapId, label: com.histmersion.api.UiLabels.Text, kind: com.histmersion.web.PlacesInput.KnownPlace.Kind): Boolean =
    remember(id, label, kind, known.get(id).exists(_.contested))
    chosenOnMap = true
    UiState.togglePlace(id)

  /** The chosen places as chips ON the map — in the narrow layout the filter bar is a closed drawer, and a filter set
    * by tapping the map must be visible (and clearable) where the tap happened.
    */
  def mapChips: HtmlElement =
    div(
      cls := "map-place-chips",
      cls("is-hidden") <-- UiState.placeIds.signal.map(_.isEmpty),
      children <-- UiState.placeIds.signal
        .combineWith(knownVersion.signal)
        .map((ids, _) =>
          ids.map { id =>
            ensureKnown(id)
            Chip(
              chipText(id, describe),
              prefix = Some(com.histmersion.web.Chip.Prefix("place: ")),
              extraCls = "place",
              remove = Some { () =>
                val _ = UiState.togglePlace(id)
              },
              mods = placeCard(id, chipText(id, describe))
            )
          }
        )
    )

  /** A gazetteer chip answers like the place on the map ([[Preview]]): hover or focus shows its card, a click or Enter
    * pins it so its links can be followed.
    */
  private def placeCard(id: com.histmersion.web.PlacesInput.PlaceId, label: com.histmersion.api.UiLabels.Text): Modifier[HtmlElement] =
    List(
      dataAttr("place") := id,
      tabIndex := 0,
      role := "button",
      aria.hasPopup := true,
      aria.label := s"$label — show its card"
    )

  /** What a gazetteer id is CALLED. Falls back to the id when the label is not cached. */
  def labelOf(id: com.histmersion.web.PlacesInput.PlaceId): com.histmersion.api.UiLabels.Text =
    known.get(id).map(_.label).getOrElse(id)

  /** The (label, kind) of a place this page has seen, if any. */
  def describe(id: com.histmersion.web.PlacesInput.PlaceId): Option[(com.histmersion.api.UiLabels.Text, com.histmersion.web.PlacesInput.KnownPlace.Kind)] = known.get(id).map(k => (k.label, k.kind))

  /** Pure: a chosen place as a chip names it — its label and kind ("Kingdom of Patones (polity)"), NEVER its id: while
    * the label is still being fetched, "…".
    */
  def chipText(id: com.histmersion.web.PlacesInput.PlaceId, lookup: com.histmersion.web.PlacesInput.PlaceId => Option[(com.histmersion.api.UiLabels.Text, com.histmersion.web.PlacesInput.KnownPlace.Kind)]): com.histmersion.api.UiLabels.Text =
    lookup(id).fold("…")((label, kind) => if kind.trim.isEmpty then label else s"$label (${Label.display(kind)})")

  /** Fetch the label of a chosen place the page has not seen (one from a link or a restored state). */
  def ensureKnown(id: com.histmersion.web.PlacesInput.EnsureKnownId): Unit =
    if id.nonEmpty && !known.contains(id) && !fetching.contains(id) then
      fetching += id
      ApiClient.place(id, None).onComplete { r =>
        fetching -= id
        r.foreach(d => learn(id, KnownPlace(d.place.label, d.place.kind, d.contested)))
      }

  private val fetching = scala.collection.mutable.Set.empty[String]

  /** A chip's text for a chosen place, following what the page learns about it ([[chipText]]). */
  def chipTextSignal(id: com.histmersion.web.PlacesInput.ChipTextSignalId): Signal[com.histmersion.api.UiLabels.Text] =
    ensureKnown(id)
    knownVersion.signal.map(_ => chipText(id, describe))

end PlacesInput

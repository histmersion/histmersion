package com.histmersion
package web

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
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.ui.Layout
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Years
import com.histmersion.api.Api
import com.histmersion.api.Paging
import com.histmersion.web.map.MapSurface
import com.histmersion.web.map.ShapeStyle
import com.histmersion.web.ui.ThemeTokens
import com.histmersion.api.Geo
import com.histmersion.lint.{LintRule, tolerated}

/** Gazetteer place picker for the filter bar. Ported from places.js (241 lines).
  *
  * Two kinds of chips: gazetteer ids (`UiState.placeIds`) and free-text names (`UiState.places`). Suggestions come from
  * `ApiClient.places(q, asOf, limit)`. Selected places draw polygons on the map as shapes ([[ShapeSet]] "place-pick").
  */
object PlacesInput:
  export Pages.PlaceId
  type ChipTextSignalId = PlaceId
  val ChipTextSignalId: Pages.PlaceId.type = Pages.PlaceId

  /** Cached label/kind/contested for gazetteer ids. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  private case class KnownPlace(label: UiLabels.Text, kind: KnownPlace.Kind, contested: Boolean)

  object KnownPlace:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def knownOf(label: String, kind: String, contested: Boolean): KnownPlace =
    KnownPlace(UiLabels.Text(label), KnownPlace.Kind(kind), contested)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val known = BoundedCache[PlaceId, KnownPlace](BoundedCache.MaxEntries(Layout.placeLabelCacheSize.value))

  /** The labels' figures, for [[Diagnostics]]. */
  def cacheStats: BoundedCache.Stats = BoundedCache.stats(known)

  /** Moves whenever `known` learns a place, so every chip that names one re-reads its label. */
  private val knownVersion = Var(0)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def learn(id: PlaceId, k: KnownPlace): Unit =
    if !known.get(id).contains(k) then
      known.put(id, k)
      knownVersion.update(_ + 1)

  /** Suggestion items: either a gazetteer place or the "text:" fallback row. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  private case class SuggestItem(
      id: String,
      label: String,
      kind: String,
      findings: Option[Int],
      contested: Boolean,
      textFallback: Boolean,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None
  )

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val chipsSignal: Signal[List[(String, String, Boolean, Boolean)]] =
    UiState.placeIds.signal.combineWith(UiState.places.signal, knownVersion.signal).map { (ids, texts, _) =>
      val gazChips = ids.map { raw =>
        val id = PlaceId(raw)
        ensureKnown(id)
        val k = known.get(id).getOrElse(knownOf("…", "", contested = false))
        (raw, k.label.value, k.contested, false) // (key, label, contested, isText)
      }
      val textChips = texts.map(v => (v, v, false, true))
      gazChips ++ textChips
    }

  // the chosen places' and the previewed place's drawing: shapes on the map, a point-only region's name
  // as a marker; `layer` is whether the map is up for them
  private var layer: Boolean = false
  private val pickNames = scala.collection.mutable.ListBuffer.empty[DomMarker]
  private val previewNames = scala.collection.mutable.ListBuffer.empty[DomMarker]
  private val drawnPlaces =
    scala.collection.mutable.HashMap.empty[PlaceId, (PlaceId, UiLabels.Text, PlaceHover.PlaceKind)]
  private var previewShown = false
  private var previewId: Option[PlaceId] = None
  private var drawn: String = ""
  private var uiConfig: UiConfig = null
  private var lastTextJson: String = "[]"

  private def ids(): List[PlaceId] = UiState.placeIds.now().map(PlaceId(_))
  private def texts(): List[String] = UiState.places.now()

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fetchPlace(id: PlaceId, year: Option[Years.Astronomical]): Future[PlaceDetail] =
    ApiClient.place(Place.Id(id.value), year.map(a => Api.AggregateQuery.AsOf(a.value)))

  /** The year the gazetteer is read at: the middle of the selected span. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def asOf(): Option[Years.Astronomical] =
    val f = UiState.from.now()
    val t = UiState.to.now()
    (f, t) match
      case (None, None)       => None
      case (None, Some(b))    => Some(Years.Astronomical(b))
      case (Some(a), None)    => Some(Years.Astronomical(a))
      case (Some(a), Some(b)) => Some(Years.Astronomical(Math.round((a.toDouble + b.toDouble) / 2)))

  private def flag(contested: Boolean, label: String): Boolean =
    contested && !Option(label).getOrElse("").toLowerCase.contains("contested")

  // ── chips ──────────────────────────────────────────────────────────────────

  private def close(): Unit =
    menuHidden.set(true)
    itemsVar.set(Nil)
    activeVar.set(-1)
    items = Nil
    activeIdx = -1

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def setIds(next: List[PlaceId]): Unit =
    UiState.set(UiState.placeIds := next.map(_.value))()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def addId(id: PlaceId): Unit =
    if id.value.nonEmpty && !ids().contains(id) then setIds(ids() :+ id)
    close()
    if inputEl != null then inputEl.value = ""

  private def addText(v: String): Unit =
    val t = Option(v).getOrElse("").trim
    if t.nonEmpty && !texts().contains(t) then UiState.set(UiState.places := texts() :+ t)()
    close()
    if inputEl != null then inputEl.value = ""

  private def removeIdAt(i: Int): Unit =
    setIds(ids().zipWithIndex.collect { case (v, j) if j != i => v })

  private def removeTextAt(i: Int): Unit =
    UiState.set(UiState.places := texts().zipWithIndex.collect { case (v, j) if j != i => v })()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def pick(it: SuggestItem): Unit =
    if it.textFallback then addText(it.label)
    else addId(PlaceId(it.id))

  // ── suggestions ────────────────────────────────────────────────────────────

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def suggest(): Unit =
    if inputEl == null then ()
    else
      val q = inputEl.value.trim
      if q.isEmpty then close()
      else
        val show: List[Place.Hit] => Unit = rows =>
          if inputEl == null || (dom.document.activeElement ne inputEl) then ()
          else
            val gaz = Option(rows)
              .getOrElse(Nil)
              .filterNot(p => ids().contains(PlaceId(p.id.value)))
              .map { p =>
                learn(PlaceId(p.id.value), knownOf(p.label.value, p.kind.value, p.contested.value))
                SuggestItem(
                  p.id.value,
                  p.label.value,
                  p.kind.value,
                  Some(p.findings.value),
                  p.contested.value,
                  textFallback = false,
                  p.periodStart,
                  p.periodEnd
                )
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

        latest.onCurrent(
          ApiClient.places(
            Some(Api.Search.Text(q)),
            asOf().map(a => Api.AggregateQuery.AsOf(a.value)),
            Some(Paging.Limit(uiConfig.facetSuggestions.value))
          )
        ) {
          case Success(rows) => show(rows)
          case Failure(_)    => show(Nil)
        }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def schedule(): Unit =
    if timer != 0 then dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => suggest(), uiConfig.debounce.suggestMs.value.toDouble).toInt

  // ── polygon overlay ────────────────────────────────────────────────────────

  /** The chosen places' shapes (`.hd-place-pick`'s colour; a disputed extent dashed and fainter), and the hovered
    * suggestion's (`.hd-place-preview`, dotted). A point is drawn as a site's dot.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pickSet: ShapeSet = TheMap.surface.shapes(
    MapSurface.ShapesId("place-pick"),
    ShapeStyle(
      fillOpacity = ShapeStyle.FillOpacity(0.06),
      lineWidth = ShapeStyle.LineWidth(2),
      lineOpacity = ShapeStyle.LineOpacity(0.9),
      colour = Some(ShapeStyle.Colour(ThemeTokens(ThemeTokens.Name("--place-pick")).value)),
      dash = List(ShapeStyle.Dash(4), ShapeStyle.Dash(3)),
      dashedLineOpacity = Some(ShapeStyle.DashedLineOpacity(0.7)),
      circleRadius = ShapeStyle.CircleRadius(uiConfig.markerRadius.value.toDouble),
      interactive = ShapeStyle.Interactive(true)
    )
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def previewSet: ShapeSet = TheMap.surface.shapes(
    MapSurface.ShapesId("place-preview"),
    ShapeStyle(
      fillOpacity = ShapeStyle.FillOpacity(0.05),
      lineWidth = ShapeStyle.LineWidth(2),
      lineOpacity = ShapeStyle.LineOpacity(0.9),
      colour = Some(ShapeStyle.Colour(ThemeTokens(ThemeTokens.Name("--place-preview")).value)),
      dash = List(ShapeStyle.Dash(1), ShapeStyle.Dash(2)),
      circleRadius = ShapeStyle.CircleRadius(uiConfig.markerRadius.value.toDouble),
      interactive = ShapeStyle.Interactive(true)
    )
  )

  /** A place's geometry on the map: its shape (a point-only region or a people: its name instead). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def drawPlace(
      id: PlaceId,
      d: PlaceDetail,
      geo: js.Dynamic,
      names: scala.collection.mutable.ListBuffer[DomMarker],
      at: () => Option[Years.Astronomical]
  ): Option[ShapeFeature] =
    drawnPlaces(id) = (id, UiLabels.Text(d.place.label.value), PlaceHover.PlaceKind(d.place.kind.value))
    val t = if js.isUndefined(geo.selectDynamic("type")) then "" else geo.selectDynamic("type").toString
    if (t == "Point" || t == "MultiPoint") && PlaceGlyph.of(
        d.place.kind,
        PlaceGlyph.GeometryType("Point")
      ) == PlaceGlyph.Label
    then
      val c = if t == "Point" then geo.coordinates else geo.coordinates.selectDynamic("0")
      val m = PlaceGlyph.labelMarker(
        Geo.Latitude(c.selectDynamic("1").asInstanceOf[Double]),
        Geo.Longitude(c.selectDynamic("0").asInstanceOf[Double]),
        UiLabels.Text(d.place.label.value),
        d.place.kind
      )
      PlaceHover.bind(m, id, UiLabels.Text(d.place.label.value), PlaceHover.PlaceKind(d.place.kind.value), at)
      TheMap.pane(MapStack.Pane.AreaPoints).add(m)
      names += m
      None
    else Some(ShapeFeature(ShapeFeature.Id(id.value), geo, dashed = ShapeFeature.Dashed(d.contested.value)))

  private def clearNames(names: scala.collection.mutable.ListBuffer[DomMarker]): Unit =
    names.foreach(m => TheMap.pane(MapStack.Pane.AreaPoints).remove(m))
    names.clear()

  /** Draw every selected place's shape. Re-fetched when the selection or span changed. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def draw(fit: Boolean): Unit =
    if !layer then ()
    else
      val key = js.JSON.stringify(js.Array(ids().toJSArray, asOf().map(_.value.toDouble).getOrElse(0.0): js.Any))
      if key == drawn then ()
      else
        drawn = key
        val currentIds = ids()
        val pending: List[Future[Option[(PlaceId, PlaceDetail)]]] = currentIds.map { id =>
          fetchPlace(id, asOf()).map(detail => Some((id, detail))).recover { case _ => None }
        }
        Future.sequence(pending).foreach { results =>
          if drawn == key then
            clearNames(pickNames)
            val bounds = scala.collection.mutable.ListBuffer.empty[GeoBounds]
            val shapes = results.flatten.flatMap { (id, d) =>
              learn(id, knownOf(d.place.label.value, d.place.kind.value, d.contested.value))
              d.geometry.flatMap { geoStr =>
                val geo = js.JSON.parse(geoStr.value)
                GeoBounds.ofGeometry(geo).foreach(bounds += _)
                drawPlace(id, d, geo, pickNames, () => asOf())
              }
            }
            pickSet.set(shapes)
            if fit && bounds.nonEmpty then
              val all = bounds.reduceLeft(_.extend(_))
              if all.valid then TheMap.surface.fitBounds(all, MapSurface.FitBoundsPadding(20), MapSurface.MaxZoom(9))
        }

  // ── public API ─────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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
                Chip.Text(label),
                prefix = Some(Chip.Prefix("text: ")),
                extraCls = Chip.ExtraClass("place place-text"),
                tip = Some(Chip.Tip("matched as text against the finding's region name")),
                remove = Some { () =>
                  // text chips come after gaz chips; compute text index
                  removeTextAt(idx - ids().length)
                  if inputEl != null then inputEl.focus()
                }
              )
            else
              Chip(
                Chip.Text(label),
                extraCls = Chip.ExtraClass(s"place${if contested then " contested-chip" else ""}"),
                mods = List[Modifier[HtmlElement]](
                  placeCard(PlaceId(key), UiLabels.Text(label)),
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
              val detail = List(Fmt.display(UiLabels.Text(it.kind)).value, findingsText, period.value)
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
                  if it.id.nonEmpty then preview(PlaceId(it.id), asOf())
                }
              )
          }
        }
      ),
      // Wire to state changes: redraw when ids/places change
      onMountCallback { _ =>
        TheMap.whenReady { _ =>
          layer = true
          PlaceHover.shapes(PlaceHover.ShapeSetName("place-pick"), PlaceHover.Places(drawnPlaces.get, () => asOf()))
          PlaceHover.shapes(PlaceHover.ShapeSetName("place-preview"), PlaceHover.Places(drawnPlaces.get, () => asOf()))
          draw(fit = true)
        }

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
    draw(fit = false)

  /** Draw ONE place's shape as a TRANSIENT overlay and fit the map to it. Toggle behavior: same id clears it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def preview(id: PlaceId, asOfYear: Option[Years.Astronomical]): Unit =
    if !layer then ()
    else
      val wasShowing = previewId.contains(id) && previewShown
      previewSet.clear()
      clearNames(previewNames)
      previewShown = false
      if wasShowing then previewId = None
      else
        previewId = Some(id)
        val effectiveAsOf = asOfYear.orElse(asOf())
        fetchPlace(id, effectiveAsOf).foreach { d =>
          if previewId.contains(id) then
            learn(id, knownOf(d.place.label.value, d.place.kind.value, d.contested.value))
            d.geometry.foreach { geoStr =>
              val geo = js.JSON.parse(geoStr.value)
              previewSet.set(
                drawPlace(id, d, geo, previewNames, () => effectiveAsOf)
                  .map(_.modify(_.dashed).setTo(ShapeFeature.Dashed(true)))
                  .toList
              )
              previewShown = true
              GeoBounds
                .ofGeometry(geo)
                .filter(_.valid)
                .foreach(bb => TheMap.surface.fitBounds(bb, MapSurface.FitBoundsPadding(20), MapSurface.MaxZoom(9)))
            }
        }

  // `var`: the next redraw follows a place chosen on the map, so it must not fit the map to it
  private var chosenOnMap = false

  /** Remember what a gazetteer id is called, so its chip reads as a name the moment it appears. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def remember(id: PlaceId, label: UiLabels.Text, kind: KnownPlace.Kind, contested: Boolean = false): Unit =
    if label.value.trim.nonEmpty then learn(id, KnownPlace(label, kind, contested))

  /** A click on place `id` on the map: it becomes the place filter — the same `UiState.placeIds` the box above sets, so
    * the lists narrow to it and its descendants and its chip appears — or, when it already is chosen, it is unchosen.
    * Returns whether it is chosen now.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def toggleFromMap(id: PlaceId, label: UiLabels.Text, kind: PlaceHover.PlaceKind): Boolean =
    remember(id, label, KnownPlace.Kind(kind.value), known.get(id).exists(_.contested))
    chosenOnMap = true
    UiState.togglePlace(UiState.TogglePlaceId(id.value))

  /** The chosen places as chips ON the map — in the narrow layout the filter bar is a closed drawer, and a filter set
    * by tapping the map must be visible (and clearable) where the tap happened.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mapChips: HtmlElement =
    div(
      cls := "map-place-chips",
      cls("is-hidden") <-- UiState.placeIds.signal.map(_.isEmpty),
      children <-- UiState.placeIds.signal
        .combineWith(knownVersion.signal)
        .map((ids, _) =>
          ids.map { raw =>
            val id = PlaceId(raw)
            ensureKnown(id)
            Chip(
              Chip.Text(chipText(id, describe).value),
              prefix = Some(Chip.Prefix("place: ")),
              extraCls = Chip.ExtraClass("place"),
              remove = Some { () =>
                val _ = UiState.togglePlace(UiState.TogglePlaceId(id.value))
              },
              mods = placeCard(id, chipText(id, describe))
            )
          }
        )
    )

  /** A gazetteer chip answers like the place on the map ([[Preview]]): hover or focus shows its card, a click or Enter
    * pins it so its links can be followed.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def placeCard(id: PlaceId, label: UiLabels.Text): Modifier[HtmlElement] =
    List(
      dataAttr("place") := id.value,
      tabIndex := 0,
      role := "button",
      aria.hasPopup := true,
      aria.label := s"${label.value} — show its card"
    )

  /** What a gazetteer id is CALLED. Falls back to the id when the label is not cached. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def labelOf(id: PlaceId): UiLabels.Text =
    known.get(id).map(_.label).getOrElse(UiLabels.Text(id.value))

  /** The (label, kind) of a place this page has seen, if any. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def describe(id: PlaceId): Option[(UiLabels.Text, KnownPlace.Kind)] = known.get(id).map(k => (k.label, k.kind))

  /** Pure: a chosen place as a chip names it — its label and kind ("Kingdom of Patones (polity)"), NEVER its id: while
    * the label is still being fetched, "…".
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def chipText(id: PlaceId, lookup: PlaceId => Option[(UiLabels.Text, KnownPlace.Kind)]): UiLabels.Text =
    lookup(id).fold(UiLabels.Text("…"))((label, kind) =>
      if kind.value.trim.isEmpty then label
      else UiLabels.Text(s"${label.value} (${Label.display(Label.Text(kind.value)).value})")
    )

  /** Fetch the label of a chosen place the page has not seen (one from a link or a restored state). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def ensureKnown(id: PlaceId): Unit =
    if id.value.nonEmpty && !known.contains(id) && !fetching.contains(id) then
      fetching += id
      fetchPlace(id, None).onComplete { r =>
        fetching -= id
        r.foreach(d => learn(id, knownOf(d.place.label.value, d.place.kind.value, d.contested.value)))
      }

  private val fetching = scala.collection.mutable.Set.empty[PlaceId]

  /** A chip's text for a chosen place, following what the page learns about it ([[chipText]]). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def chipTextSignal(id: PlaceId): Signal[UiLabels.Text] =
    ensureKnown(id)
    knownVersion.signal.map(_ => chipText(id, describe))

end PlacesInput

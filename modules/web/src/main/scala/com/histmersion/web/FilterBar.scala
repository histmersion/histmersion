package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.api.Wording
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import com.raquo.laminar.codecs.StringAsIsCodec
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.Label
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Years

/** The filter panel on Explore's left (shared with Library: one filter state, [[UiState]]): the words, the place, the
  * period (the timeline's span, echoed and editable) and the topic in view; authors, status, tags and the publication
  * years behind "More filters"; every active filter as a chip, and "Clear all". It folds to a rail that counts the
  * active filters — remembered by this browser — and lies OVER the map, so neither folding nor opening it resizes the
  * map. On a phone it is the filters drawer.
  */
object FilterBar:
  type FieldName = FieldName.T
  object FieldName extends RefinedType[String, Pure]
  type StorageKey = StorageKey.T
  object StorageKey extends RefinedType[String, Pure]
  type StoredText = StoredText.T
  object StoredText extends RefinedType[String, Pure]

  /** One chip filter: the UiState field it edits (no fallback — an unknown key is not a field) and the facet dimension
    * its suggestions come from.
    */

  type Folded = Folded.T
  object Folded extends RefinedType[Boolean, Pure]

  final private case class ChipField(state: Field[List[String]], facet: ChipInput.Config.Field, label: ChipInput.Config.Label, placeholder: ChipInput.Config.Placeholder)

  private val topicField = ChipField(UiState.topics, ChipInput.Config.Field("topics"), ChipInput.Config.Label("Topic"), ChipInput.Config.Placeholder("e.g. bronze age collapse"))

  /** Behind "More filters", in this order (then "Published between"). */
  private val moreFields = List(
    ChipField(UiState.authors, ChipInput.Config.Field("authors"), ChipInput.Config.Label("Authors"), ChipInput.Config.Placeholder("e.g. Reich")),
    ChipField(UiState.status, ChipInput.Config.Field("status"), ChipInput.Config.Label("Status"), ChipInput.Config.Placeholder("e.g. foundational")),
    ChipField(UiState.tags, ChipInput.Config.Field("tags"), ChipInput.Config.Label("Tags"), ChipInput.Config.Placeholder("e.g. sea peoples"))
  )

  private val moreKey = StorageKey("histmersion.moreFilters")
  private val foldedKey = StorageKey("histmersion.filtersFolded")

  private val moreOpen = Var(false)

  /** Whether the panel is folded to its rail (a wide screen; a phone's drawer never folds). */
  val folded: Var[Folded] = Var(Folded(false))

  def ensureMoreVisible(): Unit = moreOpen.set(true)

  /** Pure: is "More filters" open at start-up? Closed unless it was left open — or a filter in it is set (from a shared
    * link, say), so nothing that filters is hidden.
    */
  def moreOpenInitially(stored: Option[StoredText], someMoreSet: Boolean, narrow: Boolean = false): Boolean =
    stored.exists(_.value == "1") || someMoreSet

  /** Pure: is the panel folded at start-up? Only when this browser left it folded. */
  def foldedInitially(stored: Option[StoredText]): Boolean = stored.exists(_.value == "1")

  private def stored(key: StorageKey): Option[StoredText] =
    try Option(dom.window.localStorage.getItem(key.value)).map(StoredText(_))
    catch case _: Exception => None

  private def keep(key: StorageKey, on: Boolean): Unit =
    try dom.window.localStorage.setItem(key.value, if on then "1" else "0")
    catch case _: Exception => ()

  def setFolded(on: Folded): Unit =
    folded.set(on)
    keep(foldedKey, on.value)

  private def chipInput(c: ChipField, config: UiConfig): HtmlElement =
    val chipConfig = ChipInput.Config(
      field = c.facet,
      label = c.label,
      placeholder = c.placeholder,
      debounceMs = ChipInput.Config.DebounceMs(config.debounce.suggestMs.value),
      getSuggestions = prefix =>
        ApiClient
          .facets(
            Api.Facets.Field(c.facet.value),
            Some(Api.Facets.Prefix(prefix.value)),
            UiState.toFilter(except = Set(UiState.ToFilterExcept(c.state.key.value)))
          )
          .map(_.take(config.facetSuggestions.value)),
      onChange = values => UiState.set(c.state := values.map(_.value))(),
      onPreview = v => UiState.setPreview(v.map(x => UiState.Preview(UiState.Preview.Key(c.state.key.value), UiState.Preview.Value(x.value)))),
      // a status reads as the status code: its mark and its word
      display =
        if c.state.key == UiState.status.key then wire => ChipInput.Config.DisplayResult(Wording.statusOf(Api.GoalQuery.Status(wire.value)))
        else v => ChipInput.Config.DisplayResult(Label.display(Label.Text(v.value)).value),
      mark = v => Option.when(c.state.key == UiState.status.key)(ChipInput.Config.MarkResult(s"st-${v.value.trim.toLowerCase}"))
    )
    div(cls := "f", label(c.label.value), ChipInput.ofStrings(chipConfig, c.state.signal))

  /** The period's two ends: the timeline's span, written as years (`500 BCE`, `-500`, `1200`). */
  private def periodField(config: UiConfig): HtmlElement =
    def end(name: FieldName, field: Field[Option[Long]], dflt: Long, other: () => Long, valid: (Long, Long) => Boolean) =
      val bad = Var(false)
      input(
        typ := "text",
        cls := "field f-year",
        idAttr := s"f-period-$name",
        aria.label := s"Period $name",
        inputMode := "numeric",
        aria.invalid <-- bad.signal.map(_.toString),
        value <-- field.signal.map(v => Fmt.yr(Year(v.getOrElse(dflt))).value),
        onChange.mapToValue --> { raw =>
          Fmt.parseYear(Years.Text(raw)).map(_.value).filter(y => valid(y, other())) match
            case Some(y) =>
              bad.set(false)
              UiState.set(field := Some(y))(push = UiState.SetPush(true))
            case None => bad.set(true)
        }
      )
    div(
      cls := "f f-period",
      label(forId := "f-period-from", "Period"),
      div(
        cls := "f-period-ends",
        end(
          FieldName("from"),
          UiState.from,
          config.time.defaultFrom.value,
          () => UiState.to.now().getOrElse(config.time.defaultTo.value),
          _ <= _
        ),
        span(cls := "dash", "to"),
        end(
          FieldName("to"),
          UiState.to,
          config.time.defaultTo.value,
          () => UiState.from.now().getOrElse(config.time.defaultFrom.value),
          _ >= _
        )
      ),
      span(cls := "f-hint muted", "Also set by dragging on the timeline")
    )

  /** `top` and `foot`: what a page that lists by these filters adds above and under them (Library's "My library" and
    * "Show these on the map").
    */
  def init(
      config: UiConfig,
      top: Modifier[HtmlElement] = emptyMod,
      foot: Modifier[HtmlElement] = emptyMod
  ): HtmlElement =
    val someMoreSet = moreFields.exists(_.state.now().nonEmpty) ||
      UiState.yearFrom.now().isDefined || UiState.yearTo.now().isDefined
    moreOpen.set(moreOpenInitially(stored(moreKey), someMoreSet, Viewport.narrowNow))
    folded.set(Folded(foldedInitially(stored(foldedKey))))
    val active = UiState.signal.map(_ => FilterChips.countNow(config))

    div(
      cls := "fbar filter-panel",
      cls("is-folded") <-- folded.signal.map(_.value),
      role := "search",
      aria.label := "Filters",
      // on a phone this panel is the filters drawer, and this its heading
      MobileChrome.drawerHead(MobileChrome.Name("filters")),
      // folded: the rail, which counts the active filters and opens the panel
      button(
        typ := "button",
        cls := "btn btn--bare filter-rail",
        idAttr := "f-unfold",
        aria.expanded := false,
        aria.label <-- active.map(n => s"Show the filters ($n active)"),
        title := "Show the filters",
        Icon(Icon.Filter),
        child.maybe <-- active.map(n => Option.when(n.value > 0)(span(cls := "rail-count", n.toString))),
        onClick --> (_ => setFolded(Folded(false)))
      ),
      div(
        cls := "filter-body",
        top,
        div(
          cls := "filter-head",
          h2(cls := "filter-title", "Filters"),
          button(
            typ := "button",
            cls := "btn btn--bare btn--icon filter-fold",
            idAttr := "f-fold",
            aria.expanded := true,
            aria.label := "Fold the filters",
            title := "Fold the filters to the side",
            Icon(Icon.FoldLeft),
            onClick --> (_ => setFolded(Folded(true)))
          )
        ),
        div(
          cls := "f f-words",
          label(forId := "f-words", "Words"),
          input(
            typ := "search",
            cls := "field",
            idAttr := "f-words",
            placeholder := "words in the sources",
            // the same state as the top bar's search: typing in either is typing in both
            controlled(value <-- UiState.q.signal.map(_.getOrElse("")), onInput.mapToValue --> (t => UiState.setSearch(Api.Search.Text(t))))
          )
        ),
        div(cls := "f f-gazetteer", label(forId := "f-place-search", "Place"), PlacesInput.init(config)),
        periodField(config),
        chipInput(topicField, config),
        button(
          typ := "button",
          cls := "btn btn--ghost btn--block f-more",
          idAttr := "f-more-toggle",
          aria.expanded <-- moreOpen.signal,
          htmlAttr("aria-controls", StringAsIsCodec) := "f-more",
          child.text <-- moreOpen.signal.map(o => if o then "Fewer filters ▴" else "More filters ▾"),
          onClick --> { _ =>
            val next = !moreOpen.now()
            moreOpen.set(next)
            keep(moreKey, next)
          }
        ),
        div(
          cls := "frow-more",
          idAttr := "f-more",
          // in the phone's filters drawer, always shown
          hidden <-- moreOpen.signal.combineWith(Viewport.narrow).map((open, narrow) => !open && !narrow),
          moreFields.map(chipInput(_, config)),
          yearRangeSlider()
        ),
        div(
          cls := "filter-active",
          div(
            cls := "filter-active-head",
            span(
              cls := "filter-active-title",
              child.text <-- active.map(n => if n.value == 0 then "No active filters" else s"Active filters · $n")
            ),
            button(
              typ := "button",
              cls := "btn btn--ghost btn--sm f-clear",
              idAttr := "f-clear",
              disabled <-- active.map(_.value == 0),
              "Clear all",
              onClick --> { _ =>
                UiState.clearFilters()
                UiState.set(UiState.from := Some(config.time.defaultFrom.value), UiState.to := Some(config.time.defaultTo.value))()
                PlacesInput.sync()
              }
            )
          ),
          FilterChips.list(config)
        ),
        foot
      )
    )
  end init

  private val YEAR_MIN = 1850L
  private val YEAR_MAX = 2026L

  private def yearRangeSlider(): HtmlElement =
    // the handles show UiState.yearFrom/yearTo directly; a drag commits every step
    val sliderFrom = UiState.yearFrom.signal
    val sliderTo = UiState.yearTo.signal
    var dragTarget = ""
    var trackEl: dom.Element = null

    def pxToYear(x: Double, w: Double): Long =
      val t = (x / w).max(0).min(1)
      Math.round(YEAR_MIN + t * (YEAR_MAX - YEAR_MIN))

    def yearToPct(y: Long): Double =
      ((y - YEAR_MIN).toDouble / (YEAR_MAX - YEAR_MIN).toDouble * 100).max(0).min(100)

    def handlePointer(ev: dom.PointerEvent, start: Boolean): Unit =
      if trackEl == null then ()
      else
        val rect = trackEl.getBoundingClientRect()
        val x = ev.clientX - rect.left
        val w = rect.width.max(1)
        val yr = pxToYear(x, w)
        if start then
          val fromDist = Math.abs(yr - sliderFrom.now().getOrElse(YEAR_MIN))
          val toDist = Math.abs(yr - sliderTo.now().getOrElse(YEAR_MAX))
          dragTarget = if fromDist <= toDist then "from" else "to"
        if dragTarget == "from" then
          UiState.set(UiState.yearFrom := Some(Math.min(yr, sliderTo.now().getOrElse(YEAR_MAX))))()
        else UiState.set(UiState.yearTo := Some(Math.max(yr, sliderFrom.now().getOrElse(YEAR_MIN))))()

    div(
      cls := "f f-years",
      label("Published between"),
      htmlTag("span")(
        cls := "yr-label",
        child.text <-- sliderFrom.map(_.map(_.toString).getOrElse(YEAR_MIN.toString))
      ),
      div(
        cls := "year-range",
        onMountCallback { ctx => trackEl = ctx.thisNode.ref },
        div(cls := "year-range-track"),
        div(
          cls := "year-range-fill",
          styleAttr <-- sliderFrom.combineWith(sliderTo).map { (f, t) =>
            val from = yearToPct(f.getOrElse(YEAR_MIN))
            val to = yearToPct(t.getOrElse(YEAR_MAX))
            s"--from:$from%;--span:${(to - from).max(0)}%"
          }
        ),
        div(
          cls := "year-range-handle",
          styleAttr <-- sliderFrom.map(f => s"--at:${yearToPct(f.getOrElse(YEAR_MIN))}%"),
          onPointerDown --> { ev =>
            ev.preventDefault()
            dragTarget = ("from")
            ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
          },
          onPointerMove --> { ev =>
            if dragTarget == "from" then handlePointer(ev, start = false)
          },
          onPointerUp --> { _ => dragTarget = ("") }
        ),
        div(
          cls := "year-range-handle",
          styleAttr <-- sliderTo.map(t => s"--at:${yearToPct(t.getOrElse(YEAR_MAX))}%"),
          onPointerDown --> { ev =>
            ev.preventDefault()
            dragTarget = ("to")
            ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
          },
          onPointerMove --> { ev =>
            if dragTarget == "to" then handlePointer(ev, start = false)
          },
          onPointerUp --> { _ => dragTarget = ("") }
        ),
        onPointerDown --> { ev =>
          val target = ev.target.asInstanceOf[dom.Element]
          if !target.classList.contains("year-range-handle") then handlePointer(ev, start = true)
        }
      ),
      htmlTag("span")(
        cls := "yr-label",
        child.text <-- sliderTo.map(_.map(_.toString).getOrElse(YEAR_MAX.toString))
      )
    )

end FilterBar

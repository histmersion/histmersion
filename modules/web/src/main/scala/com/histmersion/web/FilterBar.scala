package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.Wording
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import com.raquo.laminar.codecs.StringAsIsCodec
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.Label

/** The filter panel on Explore's left (shared with Library: one filter state, [[UiState]]): the words, the place, the
  * period (the timeline's span, echoed and editable) and the topic in view; authors, status, tags and the publication
  * years behind "More filters"; every active filter as a chip, and "Clear all". It folds to a rail that counts the
  * active filters — remembered by this browser — and lies OVER the map, so neither folding nor opening it resizes the
  * map. On a phone it is the filters drawer.
  */
object FilterBar:

  /** One chip filter: the UiState field it edits (no fallback — an unknown key is not a field) and the facet dimension
    * its suggestions come from.
    */
  final private case class ChipField(state: Field[List[ChipField.State]], facet: ChipField.Facet, label: ChipField.Label, placeholder: ChipField.Placeholder)

  object ChipField:
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Facet = Facet.T
    object Facet extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Placeholder = Placeholder.T
    object Placeholder extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val topicField = ChipField(UiState.topics, "topics", "Topic", "e.g. bronze age collapse")

  /** Behind "More filters", in this order (then "Published between"). */
  private val moreFields = List(
    ChipField(UiState.authors, "authors", "Authors", "e.g. Reich"),
    ChipField(UiState.status, "status", "Status", "e.g. foundational"),
    ChipField(UiState.tags, "tags", "Tags", "e.g. sea peoples")
  )

  private val moreKey = "histmersion.moreFilters"
  private val foldedKey = "histmersion.filtersFolded"

  private val moreOpen = Var(false)

  /** Whether the panel is folded to its rail (a wide screen; a phone's drawer never folds). */
  val folded: Var[Boolean] = Var(false)

  def ensureMoreVisible(): Unit = moreOpen.set(true)

  /** Pure: is "More filters" open at start-up? Closed unless it was left open — or a filter in it is set (from a shared
    * link, say), so nothing that filters is hidden.
    */
  def moreOpenInitially(stored: Option[String], someMoreSet: Boolean, narrow: Boolean = false): Boolean =
    stored.contains("1") || someMoreSet

  /** Pure: is the panel folded at start-up? Only when this browser left it folded. */
  def foldedInitially(stored: Option[String]): Boolean = stored.contains("1")

  private def stored(key: String): Option[String] =
    try Option(dom.window.localStorage.getItem(key))
    catch case _: Exception => None

  private def keep(key: String, on: Boolean): Unit =
    try dom.window.localStorage.setItem(key, if on then "1" else "0")
    catch case _: Exception => ()

  def setFolded(on: Boolean): Unit =
    folded.set(on)
    keep(foldedKey, on)

  private def chipInput(c: ChipField, config: UiConfig): HtmlElement =
    val chipConfig = ChipInput.Config(
      field = c.facet,
      label = c.label,
      placeholder = c.placeholder,
      debounceMs = config.debounce.suggestMs,
      getSuggestions = prefix =>
        ApiClient
          .facets(c.facet, Some(prefix), UiState.toFilter(except = Set(c.state.key)))
          .map(_.take(config.facetSuggestions)),
      onChange = values => UiState.set(c.state := values)(),
      onPreview = v => UiState.setPreview(v.map(UiState.Preview(c.state.key, _))),
      // a status reads as the status code: its mark and its word
      display = if c.state.key == UiState.status.key then Wording.statusOf else Label.display,
      mark = v => Option.when(c.state.key == UiState.status.key)(s"st-${v.trim.toLowerCase}")
    )
    div(cls := "f", label(c.label), ChipInput(chipConfig, c.state.signal))

  /** The period's two ends: the timeline's span, written as years (`500 BCE`, `-500`, `1200`). */
  private def periodField(config: UiConfig): HtmlElement =
    def end(name: String, field: Field[Option[Long]], dflt: Long, other: () => Long, valid: (Long, Long) => Boolean) =
      val bad = Var(false)
      input(
        typ := "text",
        cls := "field f-year",
        idAttr := s"f-period-$name",
        aria.label := s"Period $name",
        inputMode := "numeric",
        aria.invalid <-- bad.signal.map(_.toString),
        value <-- field.signal.map(v => Fmt.yr(v.getOrElse(dflt))),
        onChange.mapToValue --> { raw =>
          Fmt.parseYear(raw).filter(y => valid(y, other())) match
            case Some(y) =>
              bad.set(false)
              UiState.set(field := Some(y))(push = true)
            case None => bad.set(true)
        }
      )
    div(
      cls := "f f-period",
      label(forId := "f-period-from", "Period"),
      div(
        cls := "f-period-ends",
        end(
          "from",
          UiState.from,
          config.time.defaultFrom,
          () => UiState.to.now().getOrElse(config.time.defaultTo),
          _ <= _
        ),
        span(cls := "dash", "to"),
        end(
          "to",
          UiState.to,
          config.time.defaultTo,
          () => UiState.from.now().getOrElse(config.time.defaultFrom),
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
    folded.set(foldedInitially(stored(foldedKey)))
    val active = UiState.signal.map(_ => FilterChips.countNow(config))

    div(
      cls := "fbar filter-panel",
      cls("is-folded") <-- folded.signal,
      role := "search",
      aria.label := "Filters",
      // on a phone this panel is the filters drawer, and this its heading
      MobileChrome.drawerHead("filters"),
      // folded: the rail, which counts the active filters and opens the panel
      button(
        typ := "button",
        cls := "btn btn--bare filter-rail",
        idAttr := "f-unfold",
        aria.expanded := false,
        aria.label <-- active.map(n => s"Show the filters ($n active)"),
        title := "Show the filters",
        Icon(Icon.Filter),
        child.maybe <-- active.map(n => Option.when(n > 0)(span(cls := "rail-count", n.toString))),
        onClick --> (_ => setFolded(false))
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
            onClick --> (_ => setFolded(true))
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
            controlled(value <-- UiState.q.signal.map(_.getOrElse("")), onInput.mapToValue --> UiState.setSearch)
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
              child.text <-- active.map(n => if n == 0 then "No active filters" else s"Active filters · $n")
            ),
            button(
              typ := "button",
              cls := "btn btn--ghost btn--sm f-clear",
              idAttr := "f-clear",
              disabled <-- active.map(_ == 0),
              "Clear all",
              onClick --> { _ =>
                UiState.clearFilters()
                UiState.set(UiState.from := Some(config.time.defaultFrom), UiState.to := Some(config.time.defaultTo))()
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
    var dragTarget: String = ""
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
            dragTarget = "from"
            ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
          },
          onPointerMove --> { ev =>
            if dragTarget == "from" then handlePointer(ev, start = false)
          },
          onPointerUp --> { _ => dragTarget = "" }
        ),
        div(
          cls := "year-range-handle",
          styleAttr <-- sliderTo.map(t => s"--at:${yearToPct(t.getOrElse(YEAR_MAX))}%"),
          onPointerDown --> { ev =>
            ev.preventDefault()
            dragTarget = "to"
            ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
          },
          onPointerMove --> { ev =>
            if dragTarget == "to" then handlePointer(ev, start = false)
          },
          onPointerUp --> { _ => dragTarget = "" }
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

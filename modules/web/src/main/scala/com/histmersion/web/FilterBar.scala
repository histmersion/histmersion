package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import com.raquo.laminar.codecs.StringAsIsCodec

/** Filter bar with search box, place input, chip inputs for tags/authors/topics/status, publication
  * year range, sort dropdown, "more filters" toggle, "clear all" button. Ported from filters.js
  * (141 lines). */
object FilterBar:

  /** One chip filter: the UiState field it edits (no fallback — an unknown key is not a field) and
    * the facet dimension its suggestions come from. */
  private final case class ChipField(state: Field[List[String]], facet: String, label: String, placeholder: String)

  /** In the order the row shows them: authors, topics, status, tags (then "published by", "sort by"). */
  private val chipFields = List(
    ChipField(UiState.authors, "authors", "authors", "e.g. Reich"),
    ChipField(UiState.topics, "topics", "topics", "e.g. bronze age collapse"),
    ChipField(UiState.status, "status", "status", "e.g. foundational"),
    ChipField(UiState.tags, "tags", "tags", "e.g. sea peoples")
  )

  private val sorts = List("relevance", "cited", "period", "year", "title")
  private val moreKey = "histmersion.moreFilters"

  private val moreOpen = Var(false)

  def ensureMoreVisible(): Unit = moreOpen.set(true)

  /** Pure: is the "more filters" row open at start-up? Open by default — only an explicit collapse
    * the browser remembers (`stored` = "0") keeps it closed, and even then a filter set in that row
    * (from a shared link, say) opens it, so nothing that filters is hidden. */
  def moreOpenInitially(stored: Option[String], someMoreSet: Boolean): Boolean =
    !stored.contains("0") || someMoreSet

  def init(config: UiConfig): HtmlElement =
    val someMoreSet = chipFields.exists(_.state.now().nonEmpty) ||
      UiState.yearFrom.now().isDefined || UiState.yearTo.now().isDefined
    val stored =
      try Option(dom.window.localStorage.getItem(moreKey))
      catch case _: Exception => None
    moreOpen.set(moreOpenInitially(stored, someMoreSet))

    // build chip inputs
    val chipInputElements = chipFields.map { c =>
      val chipConfig = ChipInput.Config(
        field = c.facet,
        label = c.label,
        placeholder = c.placeholder,
        debounceMs = config.debounce.suggestMs,
        getSuggestions = prefix =>
          ApiClient.facets(c.facet, Some(prefix), UiState.toFilter(except = Set(c.state.key)))
            .map(_.take(config.facetSuggestions)),
        onChange = values => UiState.set(c.state := values)(),
        onPreview = v => UiState.setPreview(v.map(UiState.Preview(c.state.key, _)))
      )

      div(
        cls := "f",
        label(c.label),
        ChipInput(chipConfig, c.state.signal)
      )
    }

    div(
      cls := "fbar",
      // main row
      div(
        cls := "frow frow-main",
        // search box
        div(
          cls := "f f-q",
          label(forId := "f-q", "search"),
          input(
            typ := "search",
            idAttr := "f-q",
            placeholder := "full text…",
            autoFocus := true,
            // controlled by UiState.q: "clear all", back/forward and a shared link all show up here
            controlled(
              value <-- UiState.q.signal.map(_.getOrElse("")),
              onInput.mapToValue --> { v => UiState.set(UiState.q := Some(v.trim).filter(_.nonEmpty))() }
            ),
            onKeyDown --> { e => if e.key == "Enter" then e.preventDefault() }
          )
        ),
        // place input
        div(
          cls := "f f-gazetteer",
          label(forId := "f-place-search", "place"),
          PlacesInput.init(config)
        ),
        // more filters toggle
        button(
          typ := "button",
          cls := "layer-btn f-more",
          idAttr := "f-more-toggle",
          aria.expanded <-- moreOpen.signal,
          htmlAttr("aria-controls", StringAsIsCodec) := "f-more",
          child.text <-- moreOpen.signal.map(o =>
            if o then "fewer filters ▴" else "more filters ▾"
          ),
          onClick --> { _ =>
            val next = !moreOpen.now()
            moreOpen.set(next)
            try dom.window.localStorage.setItem(moreKey, if next then "1" else "0")
            catch case _: Exception => ()
          }
        ),
        // clear all
        button(
          typ := "button",
          cls := "layer-btn f-clear",
          idAttr := "f-clear",
          "clear all",
          onClick --> { _ =>
            UiState.clearFilters()
            PlacesInput.sync()
            Option(dom.document.getElementById("f-q")).foreach(_.asInstanceOf[dom.HTMLElement].focus())
          }
        ),
        Toolbar.element
      ),
      // more filters row
      div(
        cls := "frow frow-more",
        idAttr := "f-more",
        hidden <-- moreOpen.signal.map(!_),
        chipInputElements,
        yearRangeSlider(),
        // sort
        div(
          cls := "f",
          label(forId := "f-sort", "sort by"),
          select(
            idAttr := "f-sort",
            controlled(
              value <-- UiState.sort.signal.map(_.filter(sorts.contains).getOrElse(sorts.head)),
              onChange.mapToValue --> { v => UiState.set(UiState.sort := Some(v))() }
            ),
            sorts.map(v => option(value := v, v))
          )
        )
      )
    )
  end init

  private val YEAR_MIN = 1850L
  private val YEAR_MAX = 2026L

  private def yearRangeSlider(): HtmlElement =
    // the handles show UiState.yearFrom/yearTo directly; a drag commits every step
    val sliderFrom = UiState.yearFrom.signal
    val sliderTo   = UiState.yearTo.signal
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
        else
          UiState.set(UiState.yearTo := Some(Math.max(yr, sliderFrom.now().getOrElse(YEAR_MIN))))()

    div(
      cls := "f f-years",
      label("published by"),
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
          left <-- sliderFrom.map(f => s"${yearToPct(f.getOrElse(YEAR_MIN))}%"),
          width <-- sliderFrom.combineWith(sliderTo).map { (f, t) =>
            val from = yearToPct(f.getOrElse(YEAR_MIN))
            val to = yearToPct(t.getOrElse(YEAR_MAX))
            s"${(to - from).max(0)}%"
          }
        ),
        div(
          cls := "year-range-handle",
          left <-- sliderFrom.map(f => s"${yearToPct(f.getOrElse(YEAR_MIN))}%"),
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
          left <-- sliderTo.map(t => s"${yearToPct(t.getOrElse(YEAR_MAX))}%"),
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
          if !target.classList.contains("year-range-handle") then
            handlePointer(ev, start = true)
        }
      ),
      htmlTag("span")(
        cls := "yr-label",
        child.text <-- sliderTo.map(_.map(_.toString).getOrElse(YEAR_MAX.toString))
      )
    )

end FilterBar

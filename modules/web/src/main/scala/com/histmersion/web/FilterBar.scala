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

  private case class ChipField(
      key: String,
      field: String,
      label: String,
      placeholder: String
  )

  private val chipFields = List(
    ChipField("tags", "tags", "tags", "e.g. sea peoples"),
    ChipField("authors", "authors", "authors", "e.g. Reich"),
    ChipField("topics", "topics", "topics", "e.g. bronze age collapse"),
    ChipField("status", "status", "status", "e.g. foundational")
  )

  private val sorts = List("relevance", "cited", "period", "year", "title")
  private val moreKey = "histmersion.moreFilters"

  // Keep references to chip Vars for sync()
  private val chipVars = scala.collection.mutable.Map.empty[String, Var[List[String]]]

  private var qInput: dom.HTMLInputElement         = null
  private var sortSelect: dom.HTMLSelectElement     = null

  private val moreOpen = Var(false)
  private val sliderFrom = Var(Option.empty[Long])
  private val sliderTo = Var(Option.empty[Long])

  def ensureMoreVisible(): Unit = moreOpen.set(true)

  def addFilter(key: String, value: String): Unit =
    val sv = stateVar(key)
    if !sv.now().contains(value) then
      sv.update(_ :+ value)
      UiState.set(key -> sv.now())(push = false)
      moreOpen.set(true)

  private def stateVar(key: String): Var[List[String]] = key match
    case "tags"    => UiState.tags
    case "authors" => UiState.authors
    case "topics"  => UiState.topics
    case "status"  => UiState.status
    case _         => Var(Nil)

  def init(config: UiConfig): HtmlElement =
    // Determine initial "more filters" state: open if any filter is set, or user toggled it on
    val s = UiState.toFilter()
    val someMoreSet = chipFields.exists(c => stateVar(c.key).now().nonEmpty) ||
      UiState.yearFrom.now().isDefined || UiState.yearTo.now().isDefined
    val wantMore =
      try dom.window.localStorage.getItem(moreKey) == "1"
      catch case _: Exception => false
    moreOpen.set(wantMore || someMoreSet)

    // build chip inputs
    val chipInputElements = chipFields.map { c =>
      val sv = stateVar(c.key)
      chipVars(c.key) = sv

      val chipConfig = ChipInput.Config(
        field = c.field,
        label = c.label,
        placeholder = c.placeholder,
        debounceMs = config.debounce.suggestMs,
        getSuggestions = prefix =>
          ApiClient.facets(c.field, Some(prefix), UiState.toFilter(except = Set(c.key)))
            .map(_.take(config.facetSuggestions)),
        onChange = values => UiState.set(c.key -> values)(push = false),
        onPreview = v => UiState.setPreview(v.map(UiState.Preview(c.key, _)))
      )

      div(
        cls := "f",
        label(c.label),
        ChipInput(chipConfig, sv.now(), externalVar = Some(sv))
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
            onMountCallback { ctx =>
              qInput = ctx.thisNode.ref
              qInput.value = UiState.q.now().getOrElse("")
            },
            onInput --> { _ =>
              if qInput != null then
                UiState.set("q" -> qInput.value.trim)(push = false)
            },
            onKeyDown --> { e =>
              if e.key == "Enter" then
                e.preventDefault()
                if qInput != null then
                  UiState.set("q" -> qInput.value.trim)(push = false)
            }
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
          cls := "f-more",
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
          cls := "f-clear",
          idAttr := "f-clear",
          "clear all",
          onClick --> { _ =>
            UiState.clearFilters()
            syncInputs()
            PlacesInput.sync()
            if qInput != null then qInput.focus()
          }
        )
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
          label(forId := "f-sort", "sort"),
          select(
            idAttr := "f-sort",
            onMountCallback { ctx =>
              sortSelect = ctx.thisNode.ref
              val current = UiState.sort.now().filter(sorts.contains).getOrElse(sorts.head)
              sortSelect.value = current
            },
            sorts.map(v => option(value := v, v)),
            onChange --> { _ =>
              if sortSelect != null then
                UiState.set("sort" -> sortSelect.value)(push = false)
            }
          )
        )
      )
    )
  end init

  private val YEAR_MIN = 1850L
  private val YEAR_MAX = 2026L

  private def yearRangeSlider(): HtmlElement =
    sliderFrom.set(UiState.yearFrom.now())
    sliderTo.set(UiState.yearTo.now())

    var dragTarget: String = ""
    var trackEl: dom.Element = null

    def pxToYear(x: Double, w: Double): Long =
      val t = (x / w).max(0).min(1)
      Math.round(YEAR_MIN + t * (YEAR_MAX - YEAR_MIN))

    def yearToPct(y: Long): Double =
      ((y - YEAR_MIN).toDouble / (YEAR_MAX - YEAR_MIN).toDouble * 100).max(0).min(100)

    def pushSlider(): Unit =
      UiState.set("yearFrom" -> sliderFrom.now(), "yearTo" -> sliderTo.now())(push = false)

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
          val capped = Math.min(yr, sliderTo.now().getOrElse(YEAR_MAX))
          sliderFrom.set(Some(capped))
        else
          val capped = Math.max(yr, sliderFrom.now().getOrElse(YEAR_MIN))
          sliderTo.set(Some(capped))
        pushSlider()

    div(
      cls := "f f-years",
      label("published"),
      htmlTag("span")(
        cls := "yr-label",
        child.text <-- sliderFrom.signal.map(_.map(_.toString).getOrElse(YEAR_MIN.toString))
      ),
      div(
        cls := "year-range",
        onMountCallback { ctx => trackEl = ctx.thisNode.ref },
        div(cls := "year-range-track"),
        div(
          cls := "year-range-fill",
          left <-- sliderFrom.signal.map(f => s"${yearToPct(f.getOrElse(YEAR_MIN))}%"),
          width <-- sliderFrom.signal.combineWith(sliderTo.signal).map { (f, t) =>
            val from = yearToPct(f.getOrElse(YEAR_MIN))
            val to = yearToPct(t.getOrElse(YEAR_MAX))
            s"${(to - from).max(0)}%"
          }
        ),
        div(
          cls := "year-range-handle",
          left <-- sliderFrom.signal.map(f => s"${yearToPct(f.getOrElse(YEAR_MIN))}%"),
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
          left <-- sliderTo.signal.map(t => s"${yearToPct(t.getOrElse(YEAR_MAX))}%"),
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
        child.text <-- sliderTo.signal.map(_.map(_.toString).getOrElse(YEAR_MAX.toString))
      )
    )

  /** Push State back into the controls (initial paint, and after "clear all"). */
  private def syncInputs(): Unit =
    if qInput != null then qInput.value = UiState.q.now().getOrElse("")
    sliderFrom.set(UiState.yearFrom.now())
    sliderTo.set(UiState.yearTo.now())
    if sortSelect != null then
      sortSelect.value = UiState.sort.now().filter(sorts.contains).getOrElse(sorts.head)

end FilterBar

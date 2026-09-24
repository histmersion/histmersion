package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
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
    ChipField("topics", "topics", "topics", "e.g. bronze_age_collapse"),
    ChipField("status", "status", "status", "e.g. foundational")
  )

  private val sorts = List("relevance", "cited", "period", "year", "title")
  private val moreKey = "histdb.moreFilters"

  // Keep references to chip Vars for sync()
  private val chipVars = scala.collection.mutable.Map.empty[String, Var[List[String]]]

  private var qInput: dom.HTMLInputElement         = null
  private var yearFromInput: dom.HTMLInputElement   = null
  private var yearToInput: dom.HTMLInputElement     = null
  private var sortSelect: dom.HTMLSelectElement     = null

  private val moreOpen = Var(false)
  private val yearHint = Var("")

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
        onChange = values => UiState.set(c.key -> values)(push = false)
      )

      div(
        cls := "f",
        label(c.label),
        ChipInput(chipConfig, sv.now())
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
        // publication year range
        div(
          cls := "f f-years",
          label(forId := "f-year-from", "published"),
          input(
            typ := "number",
            idAttr := "f-year-from",
            cls := "year",
            placeholder := "from",
            aria.label := "published from",
            onMountCallback { ctx =>
              yearFromInput = ctx.thisNode.ref
              yearFromInput.value = UiState.yearFrom.now().map(_.toString).getOrElse("")
            },
            onInput --> { _ => pushYears() },
            onChange --> { _ => pushYears() },
            onKeyDown --> { e =>
              if e.key == "Enter" then
                e.preventDefault()
                pushYears()
            }
          ),
          span(cls := "dash", "–"),
          input(
            typ := "number",
            idAttr := "f-year-to",
            cls := "year",
            placeholder := "to",
            aria.label := "published to",
            onMountCallback { ctx =>
              yearToInput = ctx.thisNode.ref
              yearToInput.value = UiState.yearTo.now().map(_.toString).getOrElse("")
            },
            onInput --> { _ => pushYears() },
            onChange --> { _ => pushYears() },
            onKeyDown --> { e =>
              if e.key == "Enter" then
                e.preventDefault()
                pushYears()
            }
          ),
          span(
            cls := "f-hint muted",
            idAttr := "f-year-hint",
            child.text <-- yearHint.signal
          )
        ),
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

  private val yearPattern = """^-?\d{1,4}$""".r

  private def pushYears(): Unit =
    if yearFromInput == null || yearToInput == null then ()
    else
      val fromRaw = yearFromInput.value.trim
      val toRaw   = yearToInput.value.trim
      val inputs  = List((yearFromInput, fromRaw), (yearToInput, toRaw))
      val bad     = inputs.filter { (_, raw) => raw.nonEmpty && yearPattern.findFirstIn(raw).isEmpty }
      inputs.foreach { (el, raw) =>
        el.setAttribute("aria-invalid",
          (if raw.nonEmpty && yearPattern.findFirstIn(raw).isEmpty then "true" else "false"))
      }
      if bad.nonEmpty then
        yearHint.set("a publication year, e.g. 1998 (deep time goes in the span above)")
      else
        yearHint.set("")
        val yf = if fromRaw.isEmpty then None else fromRaw.toLongOption
        val yt = if toRaw.isEmpty then None else toRaw.toLongOption
        UiState.set("yearFrom" -> yf, "yearTo" -> yt)(push = false)

  /** Push State back into the controls (initial paint, and after "clear all"). */
  private def syncInputs(): Unit =
    if qInput != null then qInput.value = UiState.q.now().getOrElse("")
    if yearFromInput != null then
      yearFromInput.value = UiState.yearFrom.now().map(_.toString).getOrElse("")
    if yearToInput != null then
      yearToInput.value = UiState.yearTo.now().map(_.toString).getOrElse("")
    if sortSelect != null then
      sortSelect.value = UiState.sort.now().filter(sorts.contains).getOrElse(sorts.head)

end FilterBar

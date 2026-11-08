package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import com.raquo.laminar.codecs.StringAsIsCodec
import com.histmersion.api.library.{Facet, Label}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.api.UiLabels

/** Reusable autocomplete + chip component. Ported from chips.js (137 lines).
  *
  * Shows a text input with removable chips (tags displayed as pills) and a dropdown suggestion list that appears on
  * typing. Keyboard: arrow-up/arrow-down to move, Enter to pick, Esc to close, Backspace on an empty input removes the
  * last chip.
  */
object ChipInput:
  type Entry = Entry.T
  object Entry extends Newtype[String, Pure]
  type HintLabel = HintLabel.T
  object HintLabel extends Newtype[String, Pure]

  private var seq: Int = 0

  case class Config(
      field: Config.Field,
      label: Config.Label,
      placeholder: Config.Placeholder,
      debounceMs: Config.DebounceMs,
      getSuggestions: Api.Facets.Prefix => Future[List[Facet]],
      onChange: List[Chip.Choice] => Unit,
      /** Transient preview of a hovered suggestion; `None` ends it. Never touches the chips. */
      onPreview: Option[Chip.Choice] => Unit = _ => (),
      /** How a value reads on its chip and in the suggestions. */
      display: Chip.Choice => Config.DisplayResult = v =>
        Config.DisplayResult(
          com.histmersion.api.library.Label.display(com.histmersion.api.library.Label.Text(v.value)).value
        ),
      /** The classes of a value's mark, if it has one (a source status's `st-<status>`: the status code). */
      mark: Chip.Choice => Option[Config.MarkResult] = _ => None
  )

  object Config:
    type DisplayResult = DisplayResult.T
    object DisplayResult extends Newtype[String, Pure]
    type MarkResult = MarkResult.T
    object MarkResult extends Newtype[String, Pure]

    type Field = Field.T
    object Field extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Placeholder = Placeholder.T
    object Placeholder extends Newtype[String, Pure]
    type DebounceMs = DebounceMs.T
    object DebounceMs extends Newtype[Int, Pure]

  /** A CONTROLLED chip input: it shows `values` (a UiState field's signal) and reports every change through
    * `config.onChange` — it keeps no list of its own, so it cannot drift from the state.
    */
  def apply(config: Config, values: StrictSignal[List[Chip.Choice]]): HtmlElement =
    view(config, values, () => values.now())

  /** [[apply]] over a UiState field's values, which are plain text. */
  def ofStrings(config: Config, values: StrictSignal[List[String]]): HtmlElement =
    view(config, values.map(_.map(Chip.Choice(_))), () => values.now().map(Chip.Choice(_)))

  private def view(config: Config, values: Signal[List[Chip.Choice]], now: () => List[Chip.Choice]): HtmlElement =
    seq += 1
    val id = s"chips-${config.field}-$seq"

    val items = Var(List.empty[Facet])
    val active = Var(-1)
    val menuHidden = Var(true)

    var timer: Int = 0
    // the suggestions request a late answer must belong to
    val latest = Latest()
    var inputEl: dom.HTMLInputElement = null
    var previewValue: Option[Chip.Choice] = None

    def preview(v: Option[Chip.Choice]): Unit =
      if previewValue != v then
        previewValue = v
        config.onPreview(v)

    def endPreview(): Unit = preview(None)

    def close(): Unit =
      endPreview()
      menuHidden.set(true)
      items.set(Nil)
      active.set(-1)
      if inputEl != null then
        inputEl.setAttribute("aria-expanded", "false")
        inputEl.removeAttribute("aria-activedescendant")

    def paint(): Unit =
      val a = active.now()
      if inputEl != null then
        if a >= 0 then inputEl.setAttribute("aria-activedescendant", s"$id-o$a")
        else inputEl.removeAttribute("aria-activedescendant")

    def open(rows: List[Facet]): Unit =
      val currentValues = now()
      val filtered = rows.filter(r => r.value.value != null && !currentValues.contains(Chip.Choice(r.value.value)))
      if filtered.isEmpty then close()
      else
        active.set(-1)
        items.set(filtered)
        menuHidden.set(false)
        if inputEl != null then inputEl.setAttribute("aria-expanded", "true")

    def suggest(): Unit =
      if inputEl == null then ()
      else
        val prefix = inputEl.value.trim
        if prefix.isEmpty then close()
        else
          latest.onCurrent(config.getSuggestions(Api.Facets.Prefix(prefix))) { result =>
            if inputEl != null && (dom.document.activeElement eq inputEl) then
              result.fold(_ => close(), rows => open(rows))
          }

    def schedule(): Unit =
      if timer != 0 then dom.window.clearTimeout(timer)
      timer = dom.window.setTimeout(() => suggest(), config.debounceMs.value.toDouble).toInt

    def add(raw: Entry): Unit =
      val v = Option(raw.value).getOrElse("").trim
      if inputEl != null then inputEl.value = ""
      menuHidden.set(true)
      items.set(Nil)
      active.set(-1)
      if inputEl != null then
        inputEl.setAttribute("aria-expanded", "false")
        inputEl.removeAttribute("aria-activedescendant")
      if v.nonEmpty && !now().contains(Chip.Choice(v)) then config.onChange(now() :+ Chip.Choice(v))
      // commit first, then drop the preview: the filter stays identical, so nothing reloads twice
      endPreview()

    def removeAt(i: Int): Unit =
      config.onChange(now().zipWithIndex.collect { case (v, j) if j != i => v })

    div(
      cls := "field chips",
      // chip list
      span(
        cls := "chips-list",
        children <-- values.map { vs =>
          vs.zipWithIndex.map { (v, i) =>
            Chip(
              Chip.Text(config.display(v).value),
              remove = Some { () =>
                removeAt(i)
                if inputEl != null then inputEl.focus()
              },
              extraCls = Chip.ExtraClass(config.mark(v).fold("")(m => s"has-mark $m")),
              tip = Option.when(config.display(v).value != v.value)(Chip.Tip(v.value))
            )
          }
        }
      ),
      // input
      input(
        cls := "chip-input",
        typ := "text",
        autoComplete := "off",
        spellCheck := false,
        role := "combobox",
        htmlAttr("aria-autocomplete", StringAsIsCodec) := "list",
        aria.expanded <-- menuHidden.signal.map(h => !h),
        htmlAttr("aria-controls", StringAsIsCodec) := s"$id-list",
        aria.label := config.label.value,
        placeholder := config.placeholder.value,
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
              val cur = active.now()
              val len = items.now().length
              val next =
                if cur < 0 then (if d > 0 then 0 else len - 1)
                else (cur + d + len) % len
              active.set(next)
          else if key == "Enter" then
            e.preventDefault()
            val a = active.now()
            if a >= 0 then
              val its = items.now()
              if a < its.length then add(Entry(its(a).value.value))
            else if inputEl != null then add(Entry(inputEl.value))
          else if key == "Escape" then
            if !menuHidden.now() then
              e.preventDefault()
              e.stopPropagation()
              close()
          else if key == "Backspace" && inputEl != null && inputEl.value.isEmpty && now().nonEmpty then
            e.preventDefault()
            removeAt(now().length - 1)
        }
      ),
      // suggestion dropdown
      ul(
        cls := "chip-suggest",
        idAttr := s"$id-list",
        role := "listbox",
        hidden <-- menuHidden.signal,
        onMouseLeave --> { _ => endPreview() },
        children <-- items.signal.combineWith(active.signal).map { (its, act) =>
          its.zipWithIndex.map { (it, i) =>
            li(
              role := "option",
              idAttr := s"$id-o$i",
              cls("active") <-- active.signal.map(_ == i),
              htmlAttr("aria-selected", StringAsIsCodec) <-- active.signal.map(a => (a == i).toString),
              config.mark(Chip.Choice(it.value.value)).map(m => span(cls := s"status-mark $m", aria.hidden := true)),
              span(cls := "v", config.display(Chip.Choice(it.value.value)).value),
              " ",
              span(cls := "c", s"(${it.count})"),
              onMouseDown.preventDefault --> { _ => add(Entry(it.value.value)) },
              onMouseEnter --> { _ =>
                active.set(i)
                preview(Some(Chip.Choice(it.value.value)))
              }
            )
          }
        }
      ),
      // clicking the container focuses the input
      onMouseDown --> { e =>
        val target = e.target.asInstanceOf[dom.Element]
        val isChipList = target.classList.contains("chips") || target.classList.contains("chips-list")
        if isChipList then
          e.preventDefault()
          if inputEl != null then inputEl.focus()
      }
    )
  end view

  /** What a suggestion shows: the display form (`_` as a space), as a plain text node — Laminar escapes it, so it must
    * NOT be HTML-escaped first (that shows `&amp;` literally). The value itself is what gets committed.
    */
  private[web] def hintLabel(value: Chip.Choice): HintLabel = HintLabel(Fmt.display(UiLabels.Text(value.value)).value)

end ChipInput

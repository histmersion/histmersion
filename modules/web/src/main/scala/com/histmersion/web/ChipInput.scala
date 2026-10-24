package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import com.raquo.laminar.codecs.StringAsIsCodec

/** Reusable autocomplete + chip component. Ported from chips.js (137 lines).
  *
  * Shows a text input with removable chips (tags displayed as pills) and a dropdown suggestion list
  * that appears on typing. Keyboard: arrow-up/arrow-down to move, Enter to pick, Esc to close,
  * Backspace on an empty input removes the last chip. */
object ChipInput:

  private var seq: Int = 0

  case class Config(
      field: String,
      label: String,
      placeholder: String,
      debounceMs: Int,
      getSuggestions: String => Future[List[Facet]],
      onChange: List[String] => Unit,
      /** Transient preview of a hovered suggestion; `None` ends it. Never touches the chips. */
      onPreview: Option[String] => Unit = _ => ()
  )

  /** A CONTROLLED chip input: it shows `values` (a UiState field's signal) and reports every change
    * through `config.onChange` — it keeps no list of its own, so it cannot drift from the state. */
  def apply(config: Config, values: StrictSignal[List[String]]): HtmlElement =
    seq += 1
    val id = s"chips-${config.field}-$seq"

    val items      = Var(List.empty[Facet])
    val active     = Var(-1)
    val menuHidden = Var(true)

    var timer: Int     = 0
    // the suggestions request a late answer must belong to
    val latest         = Latest()
    var inputEl: dom.HTMLInputElement = null
    var previewValue: Option[String] = None

    def preview(v: Option[String]): Unit =
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
      val currentValues = values.now()
      val filtered = rows.filter(r => r.value != null && !currentValues.contains(r.value))
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
          latest.onCurrent(config.getSuggestions(prefix)) { result =>
            if inputEl != null && dom.document.activeElement == inputEl then
              result.fold(_ => close(), rows => open(rows))
          }

    def schedule(): Unit =
      if timer != 0 then dom.window.clearTimeout(timer)
      timer = dom.window.setTimeout(() => suggest(), config.debounceMs.toDouble).toInt


    def add(raw: String): Unit =
      val v = Option(raw).getOrElse("").trim
      if inputEl != null then inputEl.value = ""
      menuHidden.set(true)
      items.set(Nil)
      active.set(-1)
      if inputEl != null then
        inputEl.setAttribute("aria-expanded", "false")
        inputEl.removeAttribute("aria-activedescendant")
      if v.nonEmpty && !values.now().contains(v) then config.onChange(values.now() :+ v)
      // commit first, then drop the preview: the filter stays identical, so nothing reloads twice
      endPreview()

    def removeAt(i: Int): Unit =
      config.onChange(values.now().zipWithIndex.collect { case (v, j) if j != i => v })

    div(
      cls := "field chips",
      // chip list
      span(
        cls := "chips-list",
        children <-- values.map { vs =>
          vs.zipWithIndex.map { (v, i) =>
            Chip.value(v, remove = Some { () =>
              removeAt(i)
              if inputEl != null then inputEl.focus()
            })
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
        aria.label := config.label,
        placeholder := config.placeholder,
        // unmounted: the pending debounce goes and an answer still on its way is dropped
        onMountUnmountCallback(
          mount = ctx => inputEl = ctx.thisNode.ref,
          unmount = _ => {
            if timer != 0 then dom.window.clearTimeout(timer)
            timer = 0
            latest.cancel()
            inputEl = null
          }),
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
              if a < its.length then add(its(a).value)
            else if inputEl != null then add(inputEl.value)
          else if key == "Escape" then
            if !menuHidden.now() then
              e.preventDefault()
              e.stopPropagation()
              close()
          else if key == "Backspace" && inputEl != null && inputEl.value.isEmpty && values.now().nonEmpty then
            e.preventDefault()
            removeAt(values.now().length - 1)
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
              span(cls := "v", hintLabel(it.value)),
              " ",
              span(cls := "c", s"(${it.count})"),
              onMouseDown.preventDefault --> { _ => add(it.value) },
              onMouseEnter --> { _ =>
                active.set(i)
                preview(Some(it.value))
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
  end apply

  /** What a suggestion shows: the display form (`_` as a space), as a plain text node — Laminar
    * escapes it, so it must NOT be HTML-escaped first (that shows `&amp;` literally). The value
    * itself is what gets committed. */
  private[web] def hintLabel(value: String): String = Fmt.display(value)

end ChipInput

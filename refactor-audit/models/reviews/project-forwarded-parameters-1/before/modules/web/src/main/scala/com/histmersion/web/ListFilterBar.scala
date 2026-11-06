package com.histmersion.web

import com.histmersion.api.{Paging, UiLabels, Wording}
import com.raquo.laminar.api.L.*

/** The filter controls of the three list windows — approvals, agent runs, jobs (core `ListFilters`): a facet's values
  * as toggle chips with the server's counts (multi-select), a `<select>` for a facet with many values, a text box
  * committed after the typing pause, and a date box. Every control reads and commits a [[UiState]] field, so a filter
  * is in the URL hash, survives a reload, and the window that owns the field restarts its list from the newest page
  * when it changes. The rows the window shows stay until the new page arrives (`PolledList`), and the chips' counts are
  * the server's, over every other filter. Pure, but for the view builders.
  */
object ListFilterBar:

  // `var`: the typing pause (`ui.debounce.search-ms`), known once `/api/config` has answered
  type TextCommitS = TextCommitS.T
  object TextCommitS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TypingMs = TypingMs.T
  object TypingMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipItemsResultPart1 = ChipItemsResultPart1.T
  object ChipItemsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipItemsResultPart2 = ChipItemsResultPart2.T
  object ChipItemsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipItemsCountsPart1 = ChipItemsCountsPart1.T
  object ChipItemsCountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipItemsCountsPart2 = ChipItemsCountsPart2.T
  object ChipItemsCountsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipItemsSelected = ChipItemsSelected.T
  object ChipItemsSelected extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggledResult = ToggledResult.T
  object ToggledResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggledSelected = ToggledSelected.T
  object ToggledSelected extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggledV = ToggledV.T
  object ToggledV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedResult = TypedResult.T
  object TypedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedS = TypedS.T
  object TypedS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DateResult = DateResult.T
  object DateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DateS = DateS.T
  object DateS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabelResult = OptionLabelResult.T
  object OptionLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabelV = OptionLabelV.T
  object OptionLabelV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabelN = OptionLabelN.T
  object OptionLabelN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabelShowArg1 = OptionLabelShowArg1.T
  object OptionLabelShowArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabelShowResult = OptionLabelShowResult.T
  object OptionLabelShowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnySetResult = AnySetResult.T
  object AnySetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TooltipResult = TooltipResult.T
  object TooltipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TooltipV = TooltipV.T
  object TooltipV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TooltipShowArg1 = TooltipShowArg1.T
  object TooltipShowArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TooltipShowResult = TooltipShowResult.T
  object TooltipShowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TooltipHintArg1 = TooltipHintArg1.T
  object TooltipHintArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TooltipHintResult = TooltipHintResult.T
  object TooltipHintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsLabel = ChipsLabel.T
  object ChipsLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsCountsPart1 = ChipsCountsPart1.T
  object ChipsCountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsCountsPart2 = ChipsCountsPart2.T
  object ChipsCountsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsField = ChipsField.T
  object ChipsField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsShowArg1 = ChipsShowArg1.T
  object ChipsShowArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsShowResult = ChipsShowResult.T
  object ChipsShowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsHintArg1 = ChipsHintArg1.T
  object ChipsHintArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsHintResult = ChipsHintResult.T
  object ChipsHintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsAbout = ChipsAbout.T
  object ChipsAbout extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerLabel = PickerLabel.T
  object PickerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerCountsPart1 = PickerCountsPart1.T
  object PickerCountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerCountsPart2 = PickerCountsPart2.T
  object PickerCountsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerField = PickerField.T
  object PickerField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerShowArg1 = PickerShowArg1.T
  object PickerShowArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerShowResult = PickerShowResult.T
  object PickerShowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerHintArg1 = PickerHintArg1.T
  object PickerHintArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerHintResult = PickerHintResult.T
  object PickerHintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerAbout = PickerAbout.T
  object PickerAbout extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextLabel = TextLabel.T
  object TextLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextField = TextField.T
  object TextField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DayLabel = DayLabel.T
  object DayLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DayField = DayField.T
  object DayField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[web] var typingMs: com.histmersion.web.ListFilterBar.TypingMs = 0

  /** The chips of a facet: the counted values in the server's order (largest first), then any value still selected that
    * the server did not count (it has none now), with 0 — so it can be unpicked.
    */
  def chipItems(counts: List[(com.histmersion.web.ListFilterBar.ChipItemsCountsPart1, com.histmersion.web.ListFilterBar.ChipItemsCountsPart2)], selected: List[com.histmersion.web.ListFilterBar.ChipItemsSelected]): List[(com.histmersion.web.ListFilterBar.ChipItemsResultPart1, com.histmersion.web.ListFilterBar.ChipItemsResultPart2)] =
    counts ++ selected.filterNot(v => counts.exists(_._1 == v)).map(_ -> 0)

  /** A chip clicked: picked when it was not, unpicked when it was; the others stay. */
  def toggled(selected: List[com.histmersion.web.ListFilterBar.ToggledSelected], v: com.histmersion.web.ListFilterBar.ToggledV): List[com.histmersion.web.ListFilterBar.ToggledResult] =
    if selected.contains(v) then selected.filterNot(_ == v) else selected :+ v

  /** What a text box commits: the trimmed text, or nothing. */
  def typed(s: com.histmersion.web.ListFilterBar.TypedS): Option[com.histmersion.web.ListFilterBar.TypedResult] = Option(s).map(_.trim).filter(_.nonEmpty)

  /** What a date box commits: its `YYYY-MM-DD`, or nothing (cleared, or a browser without one). */
  def date(s: com.histmersion.web.ListFilterBar.DateS): Option[com.histmersion.web.ListFilterBar.DateResult] = typed(s).filter(_.matches("""\d{4}-\d{2}-\d{2}"""))

  /** `Review an article (223)` — a value and its count, as a `<select>` option reads. */
  def optionLabel(v: com.histmersion.web.ListFilterBar.OptionLabelV, n: com.histmersion.web.ListFilterBar.OptionLabelN, show: com.histmersion.web.ListFilterBar.OptionLabelShowArg1 => com.histmersion.web.ListFilterBar.OptionLabelShowResult = UiLabels.label): com.histmersion.web.ListFilterBar.OptionLabelResult =
    if n > 0 then s"${show(v)} (${Paging.grouped(n)})" else show(v)

  /** Is any of these filters set (the "clear" button is offered then)? */
  def anySet(fields: List[Field[?]]): com.histmersion.web.ListFilterBar.AnySetResult = fields.exists(f => f.encoded != f.codec.encode(f.default))

  private def reset[A](f: Field[A]): UiState.Assign = f := f.default

  /** Every one of `fields` back to its default, in one commit. */
  def clear(fields: List[Field[?]]): Unit = UiState.set(fields.map(f => reset(f))*)()

  // ── the controls ────────────────────────────────────────────────────────────────────────────────

  /** A value's tooltip: its meaning, then the identifier it stands for when the label is not that identifier (for
    * people who type it on the command line).
    */
  def tooltip(v: com.histmersion.web.ListFilterBar.TooltipV, show: com.histmersion.web.ListFilterBar.TooltipShowArg1 => com.histmersion.web.ListFilterBar.TooltipShowResult, hint: com.histmersion.web.ListFilterBar.TooltipHintArg1 => Option[com.histmersion.web.ListFilterBar.TooltipHintResult]): Option[com.histmersion.web.ListFilterBar.TooltipResult] =
    val id = Option.when(show(v) != v)(s"($v)")
    (hint(v).toList ++ id.toList) match
      case Nil   => None
      case parts => Some(parts.mkString(" "))

  /** A facet as toggle chips: `label` then one chip per value with its count; hidden while it has none. `show` is a
    * value's label — by default the label dictionary's ([[UiLabels.label]]), never the raw identifier; `hint` its
    * meaning (with the identifier, its chip's tooltip), `about` what the group filters.
    */
  def chips(
      label: com.histmersion.web.ListFilterBar.ChipsLabel,
      counts: Signal[List[(com.histmersion.web.ListFilterBar.ChipsCountsPart1, com.histmersion.web.ListFilterBar.ChipsCountsPart2)]],
      field: Field[List[com.histmersion.web.ListFilterBar.ChipsField]],
      show: com.histmersion.web.ListFilterBar.ChipsShowArg1 => com.histmersion.web.ListFilterBar.ChipsShowResult = UiLabels.label,
      hint: com.histmersion.web.ListFilterBar.ChipsHintArg1 => Option[com.histmersion.web.ListFilterBar.ChipsHintResult] = _ => None,
      about: com.histmersion.web.ListFilterBar.ChipsAbout = ""
  ): HtmlElement =
    val items =
      counts.combineWith(field.signal).map((c, sel) => chipItems(c, sel).map((v, n) => (v, n, sel.contains(v))))
    div(
      cls := "btn-group list-facet",
      role := "group",
      aria.label := label,
      dataAttr("facet") := label,
      cls("is-hidden") <-- items.map(_.isEmpty),
      span(cls := "list-facet-label muted", Option.when(about.nonEmpty)(title := about), Wording.capitalize(label)),
      children <-- items.split(_._1) { (v, _, item) =>
        button(
          typ := "button",
          cls := "btn btn--toggle btn--sm",
          dataAttr("value") := v,
          tooltip(v, show, hint).map(h => title := h),
          aria.pressed <-- item.map(_._3.toString),
          span(show(v)),
          span(cls := "btn-count", child.text <-- item.map(t => Paging.grouped(t._2))),
          onClick --> (_ => UiState.set(field := toggled(field.now(), v))())
        )
      }
    )

  /** A facet with many values (managers, models) as a `<select>`: `any <label>`, then each value with its count. `hint`
    * is a value's meaning (its option's tooltip), `about` what the menu filters.
    */
  def picker(
      label: com.histmersion.web.ListFilterBar.PickerLabel,
      counts: Signal[List[(com.histmersion.web.ListFilterBar.PickerCountsPart1, com.histmersion.web.ListFilterBar.PickerCountsPart2)]],
      field: Field[Option[com.histmersion.web.ListFilterBar.PickerField]],
      show: com.histmersion.web.ListFilterBar.PickerShowArg1 => com.histmersion.web.ListFilterBar.PickerShowResult = UiLabels.label,
      hint: com.histmersion.web.ListFilterBar.PickerHintArg1 => Option[com.histmersion.web.ListFilterBar.PickerHintResult] = _ => None,
      about: com.histmersion.web.ListFilterBar.PickerAbout = ""
  ): HtmlElement =
    select(
      cls := "field list-select",
      aria.label := label,
      dataAttr("facet") := label,
      Option.when(about.nonEmpty)(title := about),
      children <-- counts.combineWith(field.signal).map { (c, sel) =>
        option(value := "", selected := sel.isEmpty, s"Any $label") ::
          chipItems(c, sel.toList).map((v, n) =>
            option(
              value := v,
              selected := sel.contains(v),
              tooltip(v, show, hint).map(h => title := h),
              optionLabel(v, n, show)
            )
          )
      },
      onChange.mapToValue --> (v => UiState.set(field := typed(v))())
    )

  /** A text filter: typed freely, committed after the typing pause (or at once on Enter), and following its field when
    * that changes elsewhere (cleared, back/forward).
    */
  def text(label: com.histmersion.web.ListFilterBar.TextLabel, field: Field[Option[com.histmersion.web.ListFilterBar.TextField]]): HtmlElement =
    val draft = Var(field.now().getOrElse(""))
    def commit(s: com.histmersion.web.ListFilterBar.TextCommitS): Unit = if typed(s) != field.now() then UiState.set(field := typed(s))()
    input(
      typ := "search",
      cls := "field list-text",
      placeholder := label,
      aria.label := label,
      controlled(value <-- draft.signal, onInput.mapToValue --> draft.writer),
      field.signal.changes --> (v => if typed(draft.now()) != v then draft.set(v.getOrElse(""))),
      draft.signal.changes.debounce(math.max(0, typingMs)) --> (s => commit(s)),
      onKeyDown.filter(_.key == "Enter") --> (_ => commit(draft.now()))
    )

  /** A date filter (`since` / `until` a day, UTC). */
  def day(label: com.histmersion.web.ListFilterBar.DayLabel, field: Field[Option[com.histmersion.web.ListFilterBar.DayField]]): HtmlElement =
    input(
      typ := "date",
      cls := "field list-date",
      aria.label := label,
      title := label,
      value <-- field.signal.map(_.getOrElse("")),
      onChange.mapToValue --> (v => UiState.set(field := date(v))())
    )

  /** "clear filters": every one of `fields` back to its default; there only while one is set. */
  def clearButton(fields: List[Field[?]]): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--ghost btn--sm list-clear",
      cls("is-hidden") <-- UiState.signal.map(_ => !anySet(fields)),
      "clear filters",
      onClick --> (_ => clear(fields))
    )

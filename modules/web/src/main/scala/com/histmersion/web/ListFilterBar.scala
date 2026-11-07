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
  type About = About.T
  object About extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipLabel = ChipLabel.T
  object ChipLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Choice = Choice.T
  object Choice extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceLabel = ChoiceLabel.T
  object ChoiceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Date = Date.T
  object Date extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldName = FieldName.T
  object FieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Hint = Hint.T
  object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tooltip = Tooltip.T
  object Tooltip extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // `var`: the typing pause (`ui.debounce.search-ms`), known once `/api/config` has answered

  type DateS = _root_.com.histmersion.web.ListFilterBar.Text
  val DateS: _root_.com.histmersion.web.ListFilterBar.Text.type = _root_.com.histmersion.web.ListFilterBar.Text

  private[web] var typingMs: Int = 0

  /** The chips of a facet: the counted values in the server's order (largest first), then any value still selected that
    * the server did not count (it has none now), with 0 — so it can be unpicked.
    */
  def chipItems(counts: List[(com.histmersion.web.ListFilterBar.Choice, com.histmersion.web.ListFilterBar.Count)], selected: List[com.histmersion.web.ListFilterBar.Choice]): List[(com.histmersion.web.ListFilterBar.ChipLabel, com.histmersion.web.ListFilterBar.Count)] =
    counts ++ selected.filterNot(v => counts.exists(_._1 == v)).map(_ -> 0)

  /** A chip clicked: picked when it was not, unpicked when it was; the others stay. */
  def toggled(selected: List[com.histmersion.web.ListFilterBar.Choice], v: com.histmersion.web.ListFilterBar.Choice): List[com.histmersion.web.ListFilterBar.Choice] =
    if selected.contains(v) then selected.filterNot(_ == v) else selected :+ v

  /** What a text box commits: the trimmed text, or nothing. */
  def typed(s: com.histmersion.web.ListFilterBar.Text): Option[com.histmersion.web.ListFilterBar.Text] = Option(s).map(_.trim).filter(_.nonEmpty)

  /** What a date box commits: its `YYYY-MM-DD`, or nothing (cleared, or a browser without one). */
  def date(s: com.histmersion.web.ListFilterBar.DateS): Option[com.histmersion.web.ListFilterBar.Date] = typed(s).filter(_.matches("""\d{4}-\d{2}-\d{2}"""))

  /** `Review an article (223)` — a value and its count, as a `<select>` option reads. */
  def optionLabel(v: com.histmersion.web.ListFilterBar.Choice, n: com.histmersion.web.ListFilterBar.Count, show: com.histmersion.web.ListFilterBar.Choice => com.histmersion.web.ListFilterBar.ChoiceLabel = UiLabels.label): com.histmersion.web.ListFilterBar.ChoiceLabel =
    if n > 0 then s"${show(v)} (${Paging.grouped(n)})" else show(v)

  /** Is any of these filters set (the "clear" button is offered then)? */
  def anySet(fields: List[Field[?]]): Boolean = fields.exists(f => f.encoded != f.codec.encode(f.default))

  private def reset[A](f: Field[A]): UiState.Assign = f := f.default

  /** Every one of `fields` back to its default, in one commit. */
  def clear(fields: List[Field[?]]): Unit = UiState.set(fields.map(f => reset(f))*)()

  // ── the controls ────────────────────────────────────────────────────────────────────────────────

  /** A value's tooltip: its meaning, then the identifier it stands for when the label is not that identifier (for
    * people who type it on the command line).
    */
  def tooltip(v: com.histmersion.web.ListFilterBar.Choice, show: com.histmersion.web.ListFilterBar.Choice => com.histmersion.web.ListFilterBar.ChoiceLabel, hint: com.histmersion.web.ListFilterBar.Hint => Option[com.histmersion.web.ListFilterBar.Hint]): Option[com.histmersion.web.ListFilterBar.Tooltip] =
    val id = Option.when(show(v) != v)(s"($v)")
    (hint(v).toList ++ id.toList) match
      case Nil   => None
      case parts => Some(parts.mkString(" "))

  /** A facet as toggle chips: `label` then one chip per value with its count; hidden while it has none. `show` is a
    * value's label — by default the label dictionary's ([[UiLabels.label]]), never the raw identifier; `hint` its
    * meaning (with the identifier, its chip's tooltip), `about` what the group filters.
    */
  def chips(
      label: com.histmersion.web.ListFilterBar.Label,
      counts: Signal[List[(com.histmersion.web.ListFilterBar.Choice, com.histmersion.web.ListFilterBar.Count)]],
      field: Field[List[com.histmersion.web.ListFilterBar.FieldName]],
      show: com.histmersion.web.ListFilterBar.Choice => com.histmersion.web.ListFilterBar.ChoiceLabel = UiLabels.label,
      hint: com.histmersion.web.ListFilterBar.Hint => Option[com.histmersion.web.ListFilterBar.Hint] = _ => None,
      about: com.histmersion.web.ListFilterBar.About = com.histmersion.web.ListFilterBar.About("")
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
      label: com.histmersion.web.ListFilterBar.Label,
      counts: Signal[List[(com.histmersion.web.ListFilterBar.Choice, com.histmersion.web.ListFilterBar.Count)]],
      field: Field[Option[com.histmersion.web.ListFilterBar.FieldName]],
      show: com.histmersion.web.ListFilterBar.Choice => com.histmersion.web.ListFilterBar.ChoiceLabel = UiLabels.label,
      hint: com.histmersion.web.ListFilterBar.Hint => Option[com.histmersion.web.ListFilterBar.Hint] = _ => None,
      about: com.histmersion.web.ListFilterBar.About = com.histmersion.web.ListFilterBar.About("")
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
  def text(label: com.histmersion.web.ListFilterBar.Label, field: Field[Option[com.histmersion.web.ListFilterBar.Text]]): HtmlElement =
    val draft = Var(field.now().getOrElse(""))
    def commit(s: com.histmersion.web.ListFilterBar.Text): Unit = if typed(s) != field.now() then UiState.set(field := typed(s))()
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
  def day(label: com.histmersion.web.ListFilterBar.Label, field: Field[Option[com.histmersion.web.ListFilterBar.Date]]): HtmlElement =
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

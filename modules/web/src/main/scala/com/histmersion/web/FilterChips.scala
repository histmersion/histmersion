package com.histmersion
package web

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

/** Every active filter as a chip with its remove button, the period among them once it is not the default span — the
  * one list the filter panel, its folded rail's count and an empty result list show.
  */
object FilterChips:
  type ChipCount = ChipCount.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ChipCount extends Newtype[Int, Pure]

  /** Pure: is the span `from`–`to` other than the default one? */

  def periodSet(from: Option[Long], to: Option[Long], defaultFrom: Long, defaultTo: Long): Boolean =
    from.getOrElse(defaultFrom) != defaultFrom || to.getOrElse(defaultTo) != defaultTo

  /** Pure: how many filters are active — the chips and, when set, the period. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def count(chips: List[UiState.ActiveChip], period: Boolean): ChipCount = ChipCount(
    chips.size + (if period then 1 else 0)
  )

  /** How many filters are active now. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def countNow(config: UiConfig): ChipCount =
    count(
      UiState.activeChips(),
      periodSet(UiState.from.now(), UiState.to.now(), config.time.defaultFrom.value, config.time.defaultTo.value)
    )

  /** The label a chip's field reads as. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def labelOf(c: UiState.ActiveChip): String = c.key match
    case k if k == UiState.q.key => "Words"
    case _                       => Wording.capitalize(c.label.value)

  /** One active filter's chip; a chosen place is named by its label, never by its id. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def chip(c: UiState.ActiveChip): HtmlElement =
    val remove = Some { () => UiState.removeChip(UiState.RemoveChipKey(c.key.value), c.value); PlacesInput.sync() }
    if c.key == UiState.placeIds.key then
      span(
        child <-- PlacesInput
          .chipTextSignal(PlacesInput.ChipTextSignalId(c.value.value))
          .map(t =>
            Chip(
              Chip.Text(t.value),
              prefix = Some(Chip.Prefix(s"${labelOf(c)}: ")),
              extraCls = Chip.ExtraClass("place"),
              remove = remove
            )
          )
      )
    else if c.key == UiState.status.key then
      Chip(
        Chip.Text(Wording.statusOf(Api.GoalQuery.Status(c.value.value))),
        prefix = Some(Chip.Prefix(s"${labelOf(c)}: ")),
        remove = remove
      )
    else
      Chip(
        Chip.Text(c.shown.map(_.value).getOrElse(Fmt.displayTag(Fmt.DisplayTagS(c.value.value)).value)),
        prefix = Some(Chip.Prefix(s"${labelOf(c)}: ")),
        remove = remove
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def periodChip(config: UiConfig): HtmlElement =
    Chip(
      Chip.Text(
        Fmt
          .periodStr(
            Year(UiState.from.now().getOrElse(config.time.defaultFrom.value)),
            Year(UiState.to.now().getOrElse(config.time.defaultTo.value))
          )
          .value
      ),
      prefix = Some(Chip.Prefix("Period: ")),
      extraCls = Chip.ExtraClass("chip-period"),
      remove = Some(() =>
        UiState
          .set(UiState.from := Some(config.time.defaultFrom.value), UiState.to := Some(config.time.defaultTo.value))(
            push = UiState.SetPush.Push
          )
      )
    )

  /** The chips of every active filter, following the state. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def list(config: UiConfig): HtmlElement =
    ul(
      cls := "filter-chips",
      aria.label := "Active filters",
      children <-- UiState.signal.map { _ =>
        val period =
          periodSet(UiState.from.now(), UiState.to.now(), config.time.defaultFrom.value, config.time.defaultTo.value)
        (if period then List(li(periodChip(config))) else Nil) ++ UiState.activeChips().map(c => li(chip(c)))
      }
    )

package com.histmersion
package web

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Year

/** Every active filter as a chip with its remove button, the period among them once it is not the default span — the
  * one list the filter panel, its folded rail's count and an empty result list show.
  */
object FilterChips:
  type ChipCount = ChipCount.T
  object ChipCount extends Newtype[Int, Pure]

  /** Pure: is the span `from`–`to` other than the default one? */

  def periodSet(from: Option[Long], to: Option[Long], defaultFrom: Long, defaultTo: Long): Boolean =
    from.getOrElse(defaultFrom) != defaultFrom || to.getOrElse(defaultTo) != defaultTo

  /** Pure: how many filters are active — the chips and, when set, the period. */
  def count(chips: List[UiState.ActiveChip], period: Boolean): ChipCount = ChipCount(
    chips.size + (if period then 1 else 0)
  )

  /** How many filters are active now. */
  def countNow(config: UiConfig): ChipCount =
    count(
      UiState.activeChips(),
      periodSet(UiState.from.now(), UiState.to.now(), config.time.defaultFrom.value, config.time.defaultTo.value)
    )

  /** The label a chip's field reads as. */
  private def labelOf(c: UiState.ActiveChip): String = c.key match
    case k if k.value == UiState.q.key.value => "Words"
    case _                                   => Wording.capitalize(c.label.value)

  /** One active filter's chip; a chosen place is named by its label, never by its id. */
  def chip(c: UiState.ActiveChip): HtmlElement =
    val remove = Some { () => UiState.removeChip(UiState.RemoveChipKey(c.key.value), c.value); PlacesInput.sync() }
    if c.key.value == UiState.placeIds.key.value then
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
    else if c.key.value == UiState.status.key.value then
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
            push = UiState.SetPush(true)
          )
      )
    )

  /** The chips of every active filter, following the state. */
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

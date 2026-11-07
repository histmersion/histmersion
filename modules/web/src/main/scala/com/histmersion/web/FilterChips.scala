package com.histmersion.web

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*

/** Every active filter as a chip with its remove button, the period among them once it is not the default span — the
  * one list the filter panel, its folded rail's count and an empty result list show.
  */
object FilterChips:
  type ChipCount = ChipCount.T
  object ChipCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodCounted = PeriodCounted.T
  object PeriodCounted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: is the span `from`–`to` other than the default one? */

  def periodSet(from: Option[com.histmersion.api.Years.Astronomical], to: Option[com.histmersion.api.Years.Astronomical], defaultFrom: com.histmersion.api.Years.Astronomical, defaultTo: com.histmersion.api.Years.Astronomical): Boolean =
    from.getOrElse(defaultFrom) != defaultFrom || to.getOrElse(defaultTo) != defaultTo

  /** Pure: how many filters are active — the chips and, when set, the period. */
  def count(chips: List[UiState.ActiveChip], period: com.histmersion.web.FilterChips.PeriodCounted): com.histmersion.web.FilterChips.ChipCount = chips.size + (if period then 1 else 0)

  /** How many filters are active now. */
  def countNow(config: UiConfig): com.histmersion.web.FilterChips.ChipCount =
    count(
      UiState.activeChips(),
      periodSet(UiState.from.now(), UiState.to.now(), config.time.defaultFrom, config.time.defaultTo)
    )

  /** The label a chip's field reads as. */
  private def labelOf(c: UiState.ActiveChip): com.histmersion.web.FilterChips.Label = c.key match
    case k if k == UiState.q.key => "Words"
    case _                       => Wording.capitalize(c.label)

  /** One active filter's chip; a chosen place is named by its label, never by its id. */
  def chip(c: UiState.ActiveChip): HtmlElement =
    val remove = Some { () => UiState.removeChip(c.key, c.value); PlacesInput.sync() }
    if c.key == UiState.placeIds.key then
      span(
        child <-- PlacesInput
          .chipTextSignal(c.value)
          .map(t => Chip(t, prefix = Some(s"${labelOf(c)}: "), extraCls = "place", remove = remove))
      )
    else if c.key == UiState.status.key then
      Chip(Wording.statusOf(c.value), prefix = Some(s"${labelOf(c)}: "), remove = remove)
    else Chip(c.shown.getOrElse(Fmt.displayTag(c.value)), prefix = Some(s"${labelOf(c)}: "), remove = remove)

  private def periodChip(config: UiConfig): HtmlElement =
    Chip(
      Fmt.periodStr(
        UiState.from.now().getOrElse(config.time.defaultFrom),
        UiState.to.now().getOrElse(config.time.defaultTo)
      ),
      prefix = Some("Period: "),
      extraCls = "chip-period",
      remove = Some(() =>
        UiState
          .set(UiState.from := Some(config.time.defaultFrom), UiState.to := Some(config.time.defaultTo))(push = true)
      )
    )

  /** The chips of every active filter, following the state. */
  def list(config: UiConfig): HtmlElement =
    ul(
      cls := "filter-chips",
      aria.label := "Active filters",
      children <-- UiState.signal.map { _ =>
        val period = periodSet(UiState.from.now(), UiState.to.now(), config.time.defaultFrom, config.time.defaultTo)
        (if period then List(li(periodChip(config))) else Nil) ++ UiState.activeChips().map(c => li(chip(c)))
      }
    )

package com.histmersion.web

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*

/** Every active filter as a chip with its remove button, the period among them once it is not the default span — the
  * one list the filter panel, its folded rail's count and an empty result list show.
  */
object FilterChips:

  /** Pure: is the span `from`–`to` other than the default one? */
  type PeriodSetResult = PeriodSetResult.T
  object PeriodSetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodSetFrom = PeriodSetFrom.T
  object PeriodSetFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodSetTo = PeriodSetTo.T
  object PeriodSetTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodSetDefaultFrom = PeriodSetDefaultFrom.T
  object PeriodSetDefaultFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodSetDefaultTo = PeriodSetDefaultTo.T
  object PeriodSetDefaultTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountPeriod = CountPeriod.T
  object CountPeriod extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountNowResult = CountNowResult.T
  object CountNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelOfResult = LabelOfResult.T
  object LabelOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def periodSet(from: Option[com.histmersion.web.FilterChips.PeriodSetFrom], to: Option[com.histmersion.web.FilterChips.PeriodSetTo], defaultFrom: com.histmersion.web.FilterChips.PeriodSetDefaultFrom, defaultTo: com.histmersion.web.FilterChips.PeriodSetDefaultTo): com.histmersion.web.FilterChips.PeriodSetResult =
    from.getOrElse(defaultFrom) != defaultFrom || to.getOrElse(defaultTo) != defaultTo

  /** Pure: how many filters are active — the chips and, when set, the period. */
  def count(chips: List[UiState.ActiveChip], period: com.histmersion.web.FilterChips.CountPeriod): com.histmersion.web.FilterChips.CountResult = chips.size + (if period then 1 else 0)

  /** How many filters are active now. */
  def countNow(config: UiConfig): com.histmersion.web.FilterChips.CountNowResult =
    count(
      UiState.activeChips(),
      periodSet(UiState.from.now(), UiState.to.now(), config.time.defaultFrom, config.time.defaultTo)
    )

  /** The label a chip's field reads as. */
  private def labelOf(c: UiState.ActiveChip): com.histmersion.web.FilterChips.LabelOfResult = c.key match
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

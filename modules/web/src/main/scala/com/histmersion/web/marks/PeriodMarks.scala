package com.histmersion.web.marks

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.personal.{Bookmark, Target}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.Year
import com.histmersion.web.*
import com.histmersion.web.settings.SettingsStore
import com.histmersion.web.ui.Flash
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The person's own periods: years kept under a name (a bookmark of kind "period"), listed first in the timeline's
  * "Zoom to period" menu, beside the authorities' presets; and the years the timeline opens on (`ui.time.default-from`
  * and `-to` in the settings file).
  */
object PeriodMarks:

  type TargetFrom = TargetFrom.T
  object TargetFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetTo = TargetTo.T
  object TargetTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartChangesResultPart1 = StartChangesResultPart1.T
  object StartChangesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartChangesResultPart2 = StartChangesResultPart2.T
  object StartChangesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartChangesFrom = StartChangesFrom.T
  object StartChangesFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartChangesTo = StartChangesTo.T
  object StartChangesTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartAtFrom = _root_.com.histmersion.web.marks.PeriodMarks.StartChangesFrom
  val StartAtFrom: _root_.com.histmersion.web.marks.PeriodMarks.StartChangesFrom.type = _root_.com.histmersion.web.marks.PeriodMarks.StartChangesFrom
  type StartAtTo = _root_.com.histmersion.web.marks.PeriodMarks.StartChangesTo
  val StartAtTo: _root_.com.histmersion.web.marks.PeriodMarks.StartChangesTo.type = _root_.com.histmersion.web.marks.PeriodMarks.StartChangesTo
  type AddName = AddName.T
  object AddName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddFrom = _root_.com.histmersion.web.marks.PeriodMarks.TargetFrom
  val AddFrom: _root_.com.histmersion.web.marks.PeriodMarks.TargetFrom.type = _root_.com.histmersion.web.marks.PeriodMarks.TargetFrom
  type AddTo = _root_.com.histmersion.web.marks.PeriodMarks.TargetTo
  val AddTo: _root_.com.histmersion.web.marks.PeriodMarks.TargetTo.type = _root_.com.histmersion.web.marks.PeriodMarks.TargetTo
  type SectionNaming = SectionNaming.T
  object SectionNaming extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SectionCurrentResultPart1 = SectionCurrentResultPart1.T
  object SectionCurrentResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SectionCurrentResultPart2 = SectionCurrentResultPart2.T
  object SectionCurrentResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SectionChooseArg1 = SectionChooseArg1.T
  object SectionChooseArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SectionChooseArg2 = SectionChooseArg2.T
  object SectionChooseArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val mine: Signal[List[Bookmark]] = MyItems.bookmarks.signal.map(_.filter(_.target.kind == ItemKind.Period))

  /** A period's target. Pure. */
  def target(from: com.histmersion.web.marks.PeriodMarks.TargetFrom, to: com.histmersion.web.marks.PeriodMarks.TargetTo): Target =
    Target(ItemKind.Period, from = Some(Year(math.min(from, to).toInt)), to = Some(Year(math.max(from, to).toInt)))

  /** The settings the timeline opens on, for these years. Pure. */
  def startChanges(from: com.histmersion.web.marks.PeriodMarks.StartChangesFrom, to: com.histmersion.web.marks.PeriodMarks.StartChangesTo): List[(com.histmersion.web.marks.PeriodMarks.StartChangesResultPart1, com.histmersion.web.marks.PeriodMarks.StartChangesResultPart2)] =
    List("ui.time.default-from" -> math.min(from, to).toString, "ui.time.default-to" -> math.max(from, to).toString)

  def startAt(from: com.histmersion.web.marks.PeriodMarks.StartAtFrom, to: com.histmersion.web.marks.PeriodMarks.StartAtTo): Unit =
    if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
    else
      SettingsStore
        .save(startChanges(from, to).map((k, v) => ConfigEdit.Change(k, Some(v))))
        .onComplete(r =>
          Flash.say(
            r.fold(
              e => s"The start was not saved: ${SettingsStore.message(e)}",
              _ => s"The timeline opens on ${Fmt.periodStr(from, to)} from the next visit on."
            )
          )
        )

  def add(name: com.histmersion.web.marks.PeriodMarks.AddName, from: com.histmersion.web.marks.PeriodMarks.AddFrom, to: com.histmersion.web.marks.PeriodMarks.AddTo): Unit =
    if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
    else
      MyItems
        .add(target(from, to), Some(name.trim).filter(_.nonEmpty), name.trim)
        .foreach(_ => Flash.say(s"Kept “${name.trim}” in my periods."))

  /** "My periods" at the head of the presets menu: each a button that shows its years, × to remove it; and, while
    * `naming` holds a name, the form that keeps the years shown under it.
    */
  def section(naming: Var[Option[com.histmersion.web.marks.PeriodMarks.SectionNaming]], current: () => (com.histmersion.web.marks.PeriodMarks.SectionCurrentResultPart1, com.histmersion.web.marks.PeriodMarks.SectionCurrentResultPart2), choose: (com.histmersion.web.marks.PeriodMarks.SectionChooseArg1, com.histmersion.web.marks.PeriodMarks.SectionChooseArg2) => Unit): HtmlElement =
    div(
      cls := "tl-my-periods",
      child.maybe <-- mine
        .combineWith(naming.signal)
        .map((ps, n) =>
          Option.when(ps.nonEmpty || n.isDefined)(div(cls := "tl-preset-region tl-my-periods-title", "My periods"))
        ),
      child.maybe <-- naming.signal.map(_.map { name0 =>
        val name = Var(name0)
        form(
          cls := "tl-period-form",
          onSubmit.preventDefault --> { _ =>
            val (a, b) = current()
            add(name.now(), a, b)
            naming.set(None)
          },
          label(forId := "tl-period-name", cls := "muted", s"Keep ${Fmt.periodStr(current()._1, current()._2)} as: "),
          input(
            typ := "text",
            idAttr := "tl-period-name",
            cls := "field",
            maxLength := 200,
            controlled(value <-- name.signal, onInput.mapToValue --> name.writer),
            onMountCallback { ctx => ctx.thisNode.ref.focus(); ctx.thisNode.ref.select() }
          ),
          div(
            cls := "toolbar",
            button(typ := "submit", cls := "btn btn--primary btn--sm", "Keep"),
            button(typ := "button", cls := "btn btn--ghost btn--sm", "Cancel", onClick --> (_ => naming.set(None)))
          )
        )
      }),
      children <-- mine.map(_.map { b =>
        val (a, z) = (b.target.from.fold(0L)(_.value.toLong), b.target.to.fold(0L)(_.value.toLong))
        div(
          cls := "tl-my-period",
          button(
            typ := "button",
            cls := "tl-preset-item tl-my-period-item",
            span(
              cls := "tl-preset-line",
              span(cls := "tl-preset-label", b.name),
              span(cls := "tl-preset-years", Fmt.periodStr(a, z))
            ),
            onClick --> (_ => choose(a, z))
          ),
          button(
            typ := "button",
            cls := "btn btn--bare btn--icon btn--sm tl-my-period-remove",
            "×",
            aria.label := s"Remove my period ${b.name}",
            title := "Remove this period",
            onClick --> (_ => MyItems.remove(b): Unit)
          )
        )
      })
    )

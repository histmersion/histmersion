package com.histmersion
package web.marks

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.personal.{Bookmark, Target}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.Year
import com.histmersion.web.*
import com.histmersion.web.settings.SettingsStore
import com.histmersion.web.ui.Flash
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.Title
import com.histmersion.web.{:=, given}

/** The person's own periods: years kept under a name (a bookmark of kind "period"), listed first in the timeline's
  * "Zoom to period" menu, beside the authorities' presets; and the years the timeline opens on (`ui.time.default-from`
  * and `-to` in the settings file).
  */
object PeriodMarks:
  val mine: Signal[List[Bookmark]] = MyItems.bookmarks.signal.map(_.filter(_.target.kind == ItemKind.Period))

  /** A period's target. Pure. */
  def target(from: Year, to: Year): Target =
    Target(
      ItemKind.Period,
      from = Some(Year(math.min(from.value, to.value).toInt)),
      to = Some(Year(math.max(from.value, to.value).toInt))
    )

  /** The settings the timeline opens on, for these years. Pure. */
  def startChanges(from: Year, to: Year): List[(ConfigEdit.Change.Key, ConfigEdit.Change.Value)] =
    List(
      ConfigEdit.Change.Key("ui.time.default-from") -> ConfigEdit.Change.Value(math.min(from.value, to.value).toString),
      ConfigEdit.Change.Key("ui.time.default-to") -> ConfigEdit.Change.Value(math.max(from.value, to.value).toString)
    )

  def startAt(from: Year, to: Year): Unit =
    if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
    else
      SettingsStore
        .save(startChanges(from, to).map((k, v) => ConfigEdit.Change(k, Some(v))))
        .onComplete(r =>
          Flash.say(
            r.fold(
              e => Flash.Text(s"The start was not saved: ${SettingsStore.message(e).sentence}"),
              _ => Flash.Text(s"The timeline opens on ${Fmt.periodStr(from, to)} from the next visit on.")
            )
          )
        )

  def add(name: Bookmark.Label, from: Year, to: Year): Unit =
    if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
    else
      MyItems
        .add(
          target(from, to),
          Some(name.value.trim).filter(_.nonEmpty).map(Bookmark.Label(_)),
          Title(name.value.trim)
        )
        .foreach(_ => Flash.say(Flash.Text(s"Kept “${name.value.trim}” in my periods.")))

  /** "My periods" at the head of the presets menu: each a button that shows its years, × to remove it; and, while
    * `naming` holds a name, the form that keeps the years shown under it.
    */
  def section(
      naming: Var[Option[Bookmark.Label]],
      current: () => (Year, Year),
      choose: (Year, Year) => Unit
  ): HtmlElement =
    div(
      cls := "tl-my-periods",
      child.maybe <-- mine
        .combineWith(naming.signal)
        .map((ps, n) =>
          Option.when(ps.nonEmpty || n.isDefined)(div(cls := "tl-preset-region tl-my-periods-title", "My periods"))
        ),
      child.maybe <-- naming.signal.map(_.map { name0 =>
        val name = Var(name0.value: String)
        form(
          cls := "tl-period-form",
          onSubmit.preventDefault --> { _ =>
            val (a, b) = current()
            add(Bookmark.Label(name.now()), a, b)
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
        val (a, z) = (b.target.from.getOrElse(Year(0L)), b.target.to.getOrElse(Year(0L)))
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

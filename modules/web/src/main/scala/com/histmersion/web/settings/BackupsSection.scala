package com.histmersion
package web.settings

import com.histmersion.api.backup.Backup
import com.histmersion.web.{ApiClient, Auth}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.web.ErrorLine

/** Settings › Backups: how many backups are kept, a backup now, and the backups there are. Restoring one replaces
  * everything in the database, so it is done from the command line (`histmersion restore`), never with a click.
  */
object BackupsSection:
  def pane: HtmlElement =
    val list = Var(Option.empty[Either[SettingsStore.Problem, List[Backup.Info]]])
    val busy = Var(false)
    val said = Var(Option.empty[Either[SettingsStore.Problem, String]])
    def load(): Unit = ApiClient.backups().onComplete(r => list.set(Some(r.toEither.left.map(SettingsStore.message))))
    def now(): Unit =
      busy.set(true)
      ApiClient.backupNow().onComplete { r =>
        busy.set(false)
        said.set(
          Some(
            r.toEither.left
              .map(SettingsStore.message)
              .map(s =>
                if s.state == Backup.State.Done.wire then s"Backed up: ${s.file}"
                else s"The backup goes on in the background (${s.state})."
              )
          )
        )
        load()
      }
    div(
      cls := "settings-section",
      onMountCallback(_ => load()),
      SettingsForm(
        SettingsForm.ApplyTitle("How many to keep"),
        Some(SettingsForm.Intro("Each backup is a full copy of the database; the oldest are deleted after a new one.")),
        List(
          SettingsForm.Item(
            SettingsForm.Item.Key("backups.keep"),
            Some(SettingsForm.Item.Name("Backups kept")),
            Some(SettingsForm.Item.Help("0 keeps every one."))
          )
        )
      ),
      sectionTag(
        cls := "settings-card",
        div(
          cls := "settings-card-head",
          h2(cls := "settings-card-title", "Backups"),
          p(
            cls := "settings-card-intro",
            "To put one back, use the command line: histmersion restore. It replaces everything in the database."
          )
        ),
        div(
          cls := "settings-card-body",
          div(
            cls := "toolbar",
            button(
              typ := "button",
              cls := "btn btn--primary backup-now",
              child.text <-- busy.signal.map(b => if b then "Backing up…" else "Back up now"),
              disabled <-- busy.signal.combineWith(Auth.writable).map((b, w) => b || !w),
              onClick --> (_ => now())
            )
          ),
          child.maybe <-- said.signal.map(_.map {
            case Left(why) => ErrorLine.text(ErrorLine.Problem(why.value))
            case Right(m)  => p(cls := "settings-saved-line", role := "status", m)
          }),
          child <-- list.signal.map {
            case None             => p(cls := "muted", "Loading…")
            case Some(Left(m))    => ErrorLine.text(ErrorLine.Problem(s"Could not list the backups: $m"))
            case Some(Right(Nil)) => p(cls := "muted", "No backup yet.")
            case Some(Right(bs))  =>
              table(
                cls := "settings-table backups-table",
                thead(tr(th("Made"), th("Size"), th("File"))),
                tbody(
                  bs.map(b =>
                    tr(
                      td(b.createdAt.value.take(16).replace('T', ' ')),
                      td(Backup.humanBytes(b.bytes)),
                      td(cls := "muted", b.file.value)
                    )
                  )
                )
              )
          }
        )
      )
    )

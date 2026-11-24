package com.histmersion
package web.settings
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.settings.{ConfigEdit, ReloadReport}
import com.histmersion.web.{ApiClient, ApiFailure}
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.ErrorLine
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.web.{:=, given}

/** The settings as the server reads its settings file, for every Settings page: read once when a page opens and again
  * after each change; and the change itself — written into the file by the server, then applied.
  */
object SettingsStore:

  type SaveLiteralConsent = SaveLiteralConsent.T
  object SaveLiteralConsent extends Newtype[Boolean, Pure]

  private val page = Var(Option.empty[Either[Problem, ConfigEdit.Page]])
  private val result = Var(Option.empty[Either[Problem, ConfigEdit.Written]])

  def current: Signal[Option[Either[Problem, ConfigEdit.Page]]] = page.signal
  def now: Option[ConfigEdit.Page] = page.now().flatMap(_.toOption)

  /** What the last change did, or why it was refused. */
  def lastResult: Signal[Option[Either[Problem, ConfigEdit.Written]]] = result.signal

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  def load(): Unit =
    ApiClient.settings().onComplete(r => page.set(Some(r.toEither.left.map(message))))

  /** `changes` written against the version read; the page is read again either way. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def save(
      changes: List[ConfigEdit.Change],
      literalConsent: SaveLiteralConsent = SaveLiteralConsent(false)
  ): Future[ConfigEdit.Written] =
    val w = ConfigEdit.Write(
      changes,
      version = now.map(p => ConfigEdit.Write.Version(p.version.value)),
      literalConsent = ConfigEdit.Write.LiteralConsent(literalConsent.value)
    )
    val f = ApiClient.writeSettings(w)
    f.onComplete { r =>
      result.set(Some(r.toEither.left.map(message)))
      load()
    }
    f

  def clearResult(): Unit = result.set(None)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def message(e: Throwable): Problem = e match
    case f: ApiFailure => Problem.Unclassified(Legacy.Text(f.getMessage))
    case other         =>
      Problem.Unclassified(Legacy.Text(Option(other.getMessage).getOrElse("the server could not be reached")))

  /** A field of the page read now. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def field(key: ConfigEdit.Field.Key): Signal[Option[ConfigEdit.Field]] =
    page.signal.map(_.flatMap(_.toOption).flatMap(_.field(key)))

  /** What a change did, in a few lines: what changed, what waits for a restart (with the restart, where the server can
    * do it), the warnings, and a reload that went wrong.
    */
  def notice: HtmlElement =
    div(
      cls := "settings-notice",
      role := "status",
      child.maybe <-- result.signal.map(_.map {
        case Left(why) => ErrorLine.text(why)
        case Right(w)  => written(w)
      })
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def written(w: ConfigEdit.Written): HtmlElement =
    val reloadErrors = w.reload.toList.flatMap(_.errors)
    div(
      cls := "settings-saved",
      p(
        cls := "settings-saved-line",
        if w.changed.isEmpty then "Nothing to save: the settings file already says so."
        else
          s"Saved in the settings file (${w.file}). " + (if reloadErrors.isEmpty && w.restart.isEmpty then
                                                           "It applies now."
                                                         else "")
      ),
      Option.when(w.restart.nonEmpty)(restartLine(w)),
      w.warnings.map(x => p(cls := "settings-warning", x)),
      reloadErrors.map(e =>
        ErrorLine.text(Problem.Unclassified(Legacy.Text(s"The server did not take the change on: $e")))
      ),
      button(typ := "button", cls := "btn btn--ghost btn--sm", "Dismiss", onClick --> (_ => clearResult()))
    )

  private val restarting = Var(Option.empty[Either[Problem, String]])

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def restartLine(w: ConfigEdit.Written): HtmlElement =
    div(
      cls := "settings-restart",
      p(
        s"${
            if w.restart.size == 1 then "This setting takes" else "These settings take"
          } effect when the server restarts: " +
          w.restart.map(_.key.value.stripPrefix("histmersion.")).mkString(", ") + "."
      ),
      if w.restartable.value then
        div(
          cls := "toolbar",
          button(
            typ := "button",
            cls := "btn btn--primary restart-btn",
            "Restart the server now",
            onClick --> (_ =>
              ApiClient
                .restartServer()
                .onComplete(r => restarting.set(Some(r.toEither.left.map(message).map(_.notice.value))))
            )
          ),
          child.maybe <-- restarting.signal.map(_.map {
            case Left(why) => ErrorLine.text(why)
            case Right(m)  => span(cls := "muted", m)
          })
        )
      else p(cls := "muted", "Restart it the way you started it.")
    )

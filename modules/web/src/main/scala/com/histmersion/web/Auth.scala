package com.histmersion.web

import com.raquo.laminar.api.L.*

/** Who may write. The server gates POST/PUT/PATCH/DELETE behind `Authorization: Bearer <key>` when `auth.api-key` is
  * set, and says so in `/api/config` (`writeKeyRequired`); a local install has no key and everything is writable.
  *
  * The key itself is [[UiState.apiKey]] (this browser's storage, never the URL). [[ApiClient]] is the one place that
  * attaches it; this object only answers "may the UI offer a write?" and reacts to a refusal by dropping the key and
  * asking for it again.
  */
object Auth:

  /** What `/api/config` said; `false` until it has answered. */
  type InitWriteKeyRequired = InitWriteKeyRequired.T
  object InitWriteKeyRequired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanWriteResult = CanWriteResult.T
  object CanWriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanWriteRequired = CanWriteRequired.T
  object CanWriteRequired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanWriteKey = CanWriteKey.T
  object CanWriteKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderResultPart1 = HeaderResultPart1.T
  object HeaderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderResultPart2 = HeaderResultPart2.T
  object HeaderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderKey = HeaderKey.T
  object HeaderKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequiredSignalResult = RequiredSignalResult.T
  object RequiredSignalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WritableResult = WritableResult.T
  object WritableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WritableNowResult = WritableNowResult.T
  object WritableNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentHeaderResultPart1 = CurrentHeaderResultPart1.T
  object CurrentHeaderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentHeaderResultPart2 = CurrentHeaderResultPart2.T
  object CurrentHeaderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refused = Refused.T
  object Refused extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UseKeyKey = UseKeyKey.T
  object UseKeyKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyNeededWhat = KeyNeededWhat.T
  object KeyNeededWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val required = Var(false)

  def init(writeKeyRequired: com.histmersion.web.Auth.InitWriteKeyRequired): Unit = required.set(writeKeyRequired)

  /** Pure: writes can be attempted when the server wants no key, or when one is held. */
  def canWrite(required: com.histmersion.web.Auth.CanWriteRequired, key: Option[com.histmersion.web.Auth.CanWriteKey]): com.histmersion.web.Auth.CanWriteResult = !required || key.exists(_.trim.nonEmpty)

  /** Pure: the `Authorization` header a write carries — only when a key is held. */
  def header(key: Option[com.histmersion.web.Auth.HeaderKey]): Option[(com.histmersion.web.Auth.HeaderResultPart1, com.histmersion.web.Auth.HeaderResultPart2)] =
    key.map(_.trim).filter(_.nonEmpty).map(k => "Authorization" -> s"Bearer $k")

  def requiredSignal: Signal[com.histmersion.web.Auth.RequiredSignalResult] = required.signal
  def writable: Signal[com.histmersion.web.Auth.WritableResult] = required.signal.combineWith(UiState.apiKey.signal).map(canWrite)
  def writableNow: com.histmersion.web.Auth.WritableNowResult = canWrite(required.now(), UiState.apiKey.now())

  /** The header for the next write — read by [[ApiClient]] and nowhere else. */
  private[web] def currentHeader: Option[(com.histmersion.web.Auth.CurrentHeaderResultPart1, com.histmersion.web.Auth.CurrentHeaderResultPart2)] = header(UiState.apiKey.now())

  /** Set when the server refused the key we sent (or refused a write because we sent none). */
  val refused: Var[com.histmersion.web.Auth.Refused] = Var(false)

  def useKey(key: com.histmersion.web.Auth.UseKeyKey): Unit =
    refused.set(false)
    UiState.set(UiState.apiKey := Some(key.trim).filter(_.nonEmpty))()

  def forget(): Unit = UiState.set(UiState.apiKey := None)()

  /** A 401/403 on a write: the key is wrong or missing. Forget it, and open the key window. The server may also have
    * been given a key since the page loaded, so it is now known to be required.
    */
  private[web] def rejected(): Unit =
    refused.set(true)
    required.set(true)
    forget()
    WindowManager.open(WindowKey.ApiKey)

  /** The window where the key is entered. */
  def keyPane: HtmlElement =
    // the text being typed is the input's own; it becomes state only when the reader confirms it
    val entered = Var("")
    div(
      cls := "tool-pane key-pane",
      child <-- requiredSignal.map { req =>
        if !req then p(cls := "muted", "This server accepts changes without a key.")
        else
          p(
            cls := "muted",
            "This server only accepts changes with its API key (",
            code("auth.api-key"),
            "). The key is kept in this browser and sent with every change — never in a link."
          )
      },
      child.maybe <-- refused.signal.map(r =>
        Option.when(r)(p(cls := "form-error", role := "alert", "The server refused the key — enter it again."))
      ),
      child <-- UiState.apiKey.signal.map {
        case Some(_) =>
          div(
            cls := "form-row",
            span(cls := "key-held", "A key is set."),
            button(typ := "button", cls := "btn", "forget key", onClick --> (_ => forget()))
          )
        case None =>
          form(
            cls := "form-row",
            onSubmit.preventDefault --> { _ =>
              if entered.now().trim.nonEmpty then
                useKey(entered.now())
                entered.set("")
            },
            input(
              typ := "password",
              cls := "field",
              aria.label := "API key",
              placeholder := "API key",
              autoComplete := "off",
              controlled(value <-- entered.signal, onInput.mapToValue --> entered.writer)
            ),
            button(
              typ := "submit",
              cls := "btn btn--primary",
              disabled <-- entered.signal.map(_.trim.isEmpty),
              "use key"
            )
          )
      }
    )

  /** What a write control shows while no key is held: a button that opens the key window. */
  def keyNeeded(what: com.histmersion.web.Auth.KeyNeededWhat): HtmlElement =
    button(
      typ := "button",
      cls := "btn key-needed",
      Icon(Icon.Key),
      s" enter the API key to $what",
      onClick --> (_ => WindowManager.open(WindowKey.ApiKey))
    )

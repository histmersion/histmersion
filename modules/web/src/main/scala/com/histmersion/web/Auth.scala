package com.histmersion
package web

import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.lint.{LintRule, tolerated}

/** Whether this page must send the API key. A page opened from a network the server trusts (this machine, by default)
  * never needs it; from any other, the server answers nothing but the page itself without `Authorization: Bearer <key>`
  * — reads included — and `/api/config` says so (`keyRequired`).
  *
  * The key itself is [[UiState.apiKey]] (this browser's storage, never the URL). [[ApiClient]] is the one place that
  * attaches it; this object only answers "may the UI offer a write?" and reacts to a refusal by dropping the key and
  * asking for it again.
  */
object Auth:
  /** What `/api/config` said; `false` until it has answered. */
  private val required = Var(false)

  /** Pure: writes can be attempted when the server wants no key, or when one is held. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def canWrite(required: Boolean, key: Option[String]): Boolean = !required || key.exists(_.trim.nonEmpty)

  /** Pure: the `Authorization` header a write carries — only when a key is held. */
  def header(key: Option[String]): Option[(String, String)] =
    key.map(_.trim).filter(_.nonEmpty).map(k => "Authorization" -> s"Bearer $k")

  def requiredSignal: Signal[Boolean] = required.signal
  def writable: Signal[Boolean] = required.signal.combineWith(UiState.apiKey.signal).map(canWrite)
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def writableNow: Boolean = canWrite(required.now(), UiState.apiKey.now())

  /** The header for the next request — read by [[ApiClient]] and the map's own requests ([[mapRequest]]). */
  private[web] def currentHeader: Option[(String, String)] = header(UiState.apiKey.now())

  /** Pure: whether `url` is this page's own server's (`origin` is the page's), the only one the key is sent to. */
  def ownServer(url: String, origin: String): Boolean = url.startsWith("/") || url.startsWith(s"$origin/")

  /** What MapLibre asks its tiles and styles with: `url`, and the key when it is this server's. */
  private[web] def mapRequest(url: String): js.Object =
    currentHeader.filter(_ => ownServer(url, dom.window.location.origin)) match
      case Some((k, v)) => js.Dynamic.literal(url = url, headers = js.Dynamic.literal(k -> v))
      case None         => js.Dynamic.literal(url = url)

  /** Set when the server refused the key we sent (or refused a write because we sent none). */
  val refused: Var[Boolean] = Var(false)

  def useKey(key: String): Unit =
    refused.set(false)
    UiState.set(UiState.apiKey := Some(key.trim).filter(_.nonEmpty))()

  def forget(): Unit = UiState.set(UiState.apiKey := None)()

  /** A 401: the key is wrong or missing. Forget it, and open the key window. The server may also have been given a key
    * since the page loaded, so it is now known to be required.
    */
  private[web] def rejected(): Unit =
    refused.set(true)
    required.set(true)
    if started then
      forget()
      WindowManager.open(WindowKey.ApiKey)

  /** Whether the page has started (`/api/config` answered); before that a refusal is [[keyGate]]'s to show. */
  // `var`: set once, when the app starts
  private var started = false

  /** The page is up, with what `/api/config` said: a refusal from now on opens the key window. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start(keyRequired: UiConfig.KeyRequired): Unit =
    started = true
    required.set(keyRequired.value)

  /** What the page shows when the server refused even its settings: the key prompt alone, in `into`. A key that was
    * kept and refused is dropped; a key entered is kept and the page starts again with it (the address, and with it the
    * windows and filters it holds, stays as it was).
    */
  def keyGate(into: dom.Element): Unit =
    val held = UiState.apiKey.now().isDefined
    if held then UiState.keepKey(None)
    val entered = Var("")
    into.textContent = ""
    val _ = render(
      into,
      div(
        cls := "tool-pane key-pane key-gate",
        h1("Histmersion"),
        p(
          cls := "muted",
          "From your network this server answers only with its API key (",
          code("auth.api-key"),
          "). The key is kept in this browser and sent with every request — never in a link."
        ),
        if held then p(cls := "form-error", role := "alert", "The server refused the key — enter it again.")
        else emptyNode,
        form(
          cls := "form-row",
          onSubmit.preventDefault --> { _ =>
            if entered.now().trim.nonEmpty then
              UiState.keepKey(Some(entered.now().trim))
              dom.window.location.reload()
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
      )
    )

  /** The window where the key is entered. */
  def keyPane: HtmlElement =
    // the text being typed is the input's own; it becomes state only when the reader confirms it
    val entered = Var("")
    div(
      cls := "tool-pane key-pane",
      child <-- requiredSignal.map { req =>
        if !req then p(cls := "muted", "This server trusts your network: it needs no key from you.")
        else
          p(
            cls := "muted",
            "From your network this server answers only with its API key (",
            code("auth.api-key"),
            "). The key is kept in this browser and sent with every request — never in a link."
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
  def keyNeeded(what: String): HtmlElement =
    button(
      typ := "button",
      cls := "btn key-needed",
      Icon(Icon.Key),
      s" enter the API key to $what",
      onClick --> (_ => WindowManager.open(WindowKey.ApiKey))
    )

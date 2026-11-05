package com.histmersion.web.settings

import com.histmersion.api.Wording
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.web.Auth
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Settings › Keys and passwords: every key and password a setting refers to — only whether it is set and how it is
  * referred to, never its value. A plugin's is written as a REFERENCE (an environment variable, a password manager's
  * entry, or — when you say so — the value itself); one that is the value itself is the server's environment's alone.
  */
object KeysSection:

  /** The ways a reference is written: its scheme, the name shown, what the entry box asks for. */
  type ReferenceResultPart1 = ReferenceResultPart1.T
  object ReferenceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferenceResultPart2 = ReferenceResultPart2.T
  object ReferenceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferenceEntry = ReferenceEntry.T
  object ReferenceEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedPart1 = NamedPart1.T
  object NamedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedPart2 = NamedPart2.T
  object NamedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameResult = NameResult.T
  object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameRelative = NameRelative.T
  object NameRelative extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateResult = StateResult.T
  object StateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Form(val scheme: Form.Scheme, val label: Form.Label, val hint: Form.Hint):
    case Env extends Form("env", "Environment variable", "its name, e.g. HISTMERSION_CORE_KEY")
    case Keepassxc extends Form("keepassxc", "KeePassXC entry", "Group/Entry#attribute")
    case OnePass extends Form("1password", "1Password item", "op://Vault/Item/field")
    case Bitwarden extends Form("bitwarden", "Bitwarden item", "the item's name or id[#field]")
    case Value extends Form("value", "The value itself, in the settings file", "the key or password")

  object Form:
    type Scheme = Scheme.T
    object Scheme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hint = Hint.T
    object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type OfScheme = OfScheme.T
    object OfScheme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(scheme: com.histmersion.web.settings.KeysSection.Form.OfScheme): Form = values.find(_.scheme == scheme).getOrElse(Env)

  /** The reference a form and its entry write, or why not. Pure. */
  def reference(form: Form, entry: com.histmersion.web.settings.KeysSection.ReferenceEntry): Either[com.histmersion.web.settings.KeysSection.ReferenceResultPart1, com.histmersion.web.settings.KeysSection.ReferenceResultPart2] =
    val t = entry.trim
    if t.isEmpty then Left(s"Write ${form.hint}.")
    else if form == Form.Env && !t.matches("[A-Za-z_][A-Za-z0-9_]*") then
      Left(s"$t is not an environment variable's name.")
    else Right(s"${form.scheme}:$t")

  /** The keys that hold the value itself, by what they open. */
  private val Named: Map[com.histmersion.web.settings.KeysSection.NamedPart1, com.histmersion.web.settings.KeysSection.NamedPart2] = Map(
    "auth.api-key" -> "This server's own key for changes",
    "db.password" -> "The database's password",
    "query.password" -> "The read-only query login's password",
    "acquire.core.api-key" -> "CORE (the key itself)",
    "acquire.semantic-scholar.api-key" -> "Semantic Scholar",
    "agents.providers.anthropic.api-key" -> "Anthropic",
    "agents.providers.openai.api-key" -> "OpenAI",
    "agents.providers.local.api-key" -> "A local AI server"
  )

  /** A credential's name for a person: its plugin and what it is (`1Password: service account token`), or what it
    * opens. Pure.
    */
  def name(relative: com.histmersion.web.settings.KeysSection.NameRelative): com.histmersion.web.settings.KeysSection.NameResult =
    relative.split('.').toList match
      case "plugins" :: id :: rest if rest.nonEmpty =>
        s"${Wording.plugin(id)}: ${Wording.humanize(rest.mkString(" ")).toLowerCase.replace("api key", "API key")}"
      case _ => Named.getOrElse(relative, Wording.humanize(relative.replace('.', ' ')))

  /** How a credential is held, in words. Pure. */
  def state(c: ConfigEdit.Credential): com.histmersion.web.settings.KeysSection.StateResult = c.form match
    case "unset" => "Not set"
    case "env"   => "Set · from an environment variable"
    case "vault" =>
      s"Set · from ${c.shown.flatMap(_.split(':').headOption).map(v => Wording.plugin(v)).getOrElse("a password manager")}"
    case "value" => "Set · the value is written in the settings file"
    case _       => "Set"

  def pane: HtmlElement =
    div(
      cls := "settings-section keys-section",
      sectionTag(
        cls := "settings-card",
        div(
          cls := "settings-card-head",
          h2(cls := "settings-card-title", "This browser's key for making changes"),
          p(
            cls := "settings-card-intro",
            "When the server asks for a key before any change, this browser keeps it here."
          )
        ),
        div(cls := "settings-card-body", Auth.keyPane)
      ),
      child <-- SettingsStore.current.map {
        case None            => p(cls := "muted", "Loading…")
        case Some(Left(m))   => com.histmersion.web.ErrorLine.text(s"Could not read the settings: $m")
        case Some(Right(pg)) =>
          val creds = pg.fields.filter(_.credential.isDefined)
          div(
            sectionTag(
              cls := "settings-card",
              div(
                cls := "settings-card-head",
                h2(cls := "settings-card-title", "Keys the plugins use"),
                p(
                  cls := "settings-card-intro",
                  "Written in the settings file as a reference to where the key is kept — " +
                    "an environment variable of the server, or an entry of KeePassXC, 1Password or Bitwarden (turn that " +
                    "one on in Plugins). Histmersion never shows the key itself."
                )
              ),
              div(
                cls := "settings-card-body",
                creds.filter(_.credential.exists(_.rule == "reference")).map(referenceRow)
              )
            ),
            sectionTag(
              cls := "settings-card",
              div(
                cls := "settings-card-head",
                h2(cls := "settings-card-title", "Keys kept in the server's environment"),
                p(
                  cls := "settings-card-intro",
                  "These are the key itself, so they are never written into the settings " +
                    "file: set the environment variable named for each where the server is started."
                )
              ),
              div(cls := "settings-card-body", creds.filter(_.credential.exists(_.rule == "environment")).map(envRow))
            )
          )
      },
      SettingsStore.notice
    )

  private def envRow(f: ConfigEdit.Field): HtmlElement =
    div(
      cls := "setting-row key-row",
      dataAttr("key") := f.relative,
      div(
        cls := "setting-head",
        span(cls := "setting-name", name(f.relative)),
        span(cls := "setting-key muted", f.relative)
      ),
      p(cls := "key-state", f.credential.map(state).getOrElse("")),
      f.envVar.map(v => p(cls := "muted", s"Environment variable: $v"))
    )

  private def referenceRow(f: ConfigEdit.Field): HtmlElement =
    val form = Var(Form.Env)
    val entry = Var("")
    val consent = Var(false)
    val problem = Var(Option.empty[String])
    def save(): Unit =
      reference(form.now(), entry.now()) match
        case Left(why) => problem.set(Some(why))
        case Right(r)  =>
          problem.set(None)
          SettingsStore
            .save(List(ConfigEdit.Change(f.key, Some(r))), literalConsent = consent.now())
            .foreach(_ => entry.set(""))
    val id = "key-" + f.key.replaceAll("[^A-Za-z0-9_-]", "-")
    div(
      cls := "setting-row key-row",
      dataAttr("key") := f.relative,
      div(
        cls := "setting-head",
        span(cls := "setting-name", name(f.relative)),
        span(cls := "setting-key muted", f.relative)
      ),
      p(
        cls := "key-state",
        f.credential.map(c => state(c) + c.shown.filter(_ => c.form != "value").fold("")(s => s" ($s)")).getOrElse("")
      ),
      div(
        cls := "key-edit toolbar",
        label(forId := s"$id-form", cls := "sr-only", "Where the key is kept"),
        select(
          idAttr := s"$id-form",
          cls := "field key-form",
          Form.values.toList.map(x => option(value := x.scheme, x.label)),
          controlled(value <-- form.signal.map(_.scheme), onChange.mapToValue --> (v => form.set(Form.of(v))))
        ),
        label(forId := s"$id-entry", cls := "sr-only", "The reference"),
        input(
          idAttr := s"$id-entry",
          cls := "field key-entry",
          typ <-- form.signal.map(x => if x == Form.Value then "password" else "text"),
          placeholder <-- form.signal.map(_.hint),
          controlled(value <-- entry.signal, onInput.mapToValue --> entry.writer)
        ),
        button(
          typ := "button",
          cls := "btn btn--primary key-save",
          "Save",
          disabled <-- Auth.writable.map(!_),
          onClick --> (_ => save())
        ),
        Option.when(f.credential.exists(_.isSet))(
          button(
            typ := "button",
            cls := "btn btn--ghost key-remove",
            "Remove",
            disabled <-- Auth.writable.map(!_),
            onClick --> (_ => SettingsStore.save(List(ConfigEdit.Change(f.key, None))))
          )
        )
      ),
      child.maybe <-- form.signal.map(x =>
        Option.when(x == Form.Value)(
          label(
            cls := "key-consent",
            input(typ := "checkbox", controlled(checked <-- consent.signal, onClick.mapToChecked --> consent.writer)),
            " I understand: anyone who can read the settings file can read this key."
          )
        )
      ),
      child.maybe <-- problem.signal.map(_.map(com.histmersion.web.ErrorLine.text))
    )

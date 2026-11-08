package com.histmersion
package web.settings

import com.histmersion.api.Wording
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.web.Auth
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.ErrorLine

/** Settings › Keys and passwords: every key and password a setting refers to — only whether it is set and how it is
  * referred to, never its value. A plugin's is written as a REFERENCE (an environment variable, a password manager's
  * entry, or — when you say so — the value itself); one that is the value itself is the server's environment's alone.
  */
object KeysSection:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Reference = Reference.T
  object Reference extends Newtype[String, Pure]
  type State = State.T
  object State extends Newtype[String, Pure]

  /** The ways a reference is written: its scheme, the name shown, what the entry box asks for. */
  type ReferenceEntry = ReferenceEntry.T
  object ReferenceEntry extends Newtype[String, Pure]

  enum Form(val scheme: Form.Scheme, val label: Form.Label, val hint: Form.Hint) derives CanEqual:
    case Env
        extends Form(
          Form.Scheme("env"),
          Form.Label("Environment variable"),
          Form.Hint("its name, e.g. HISTMERSION_CORE_KEY")
        )
    case Keepassxc
        extends Form(Form.Scheme("keepassxc"), Form.Label("KeePassXC entry"), Form.Hint("Group/Entry#attribute"))
    case OnePass
        extends Form(Form.Scheme("1password"), Form.Label("1Password item"), Form.Hint("op://Vault/Item/field"))
    case Bitwarden
        extends Form(Form.Scheme("bitwarden"), Form.Label("Bitwarden item"), Form.Hint("the item's name or id[#field]"))
    case Value
        extends Form(
          Form.Scheme("value"),
          Form.Label("The value itself, in the settings file"),
          Form.Hint("the key or password")
        )

  object Form:
    type Scheme = Scheme.T
    object Scheme extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Hint = Hint.T
    object Hint extends Newtype[String, Pure]

    def of(scheme: Scheme): Form = values.find(_.scheme == scheme).getOrElse(Env)

  /** The reference a form and its entry write, or why not. Pure. */
  def reference(form: Form, entry: ReferenceEntry): Either[Problem, Reference] =
    val t = entry.value.trim
    if t.isEmpty then Left(Problem(s"Write ${form.hint}."))
    else if form == Form.Env && !t.matches("[A-Za-z_][A-Za-z0-9_]*") then
      Left(Problem(s"$t is not an environment variable's name."))
    else Right(Reference(s"${form.scheme}:$t"))

  /** The keys that hold the value itself, by what they open. */
  private val Named: Map[String, String] = Map(
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
  def name(relative: ConfigEdit.Field.LocalKey): Name =
    relative.value.split('.').toList match
      case "plugins" :: id :: rest if rest.nonEmpty =>
        Name(
          s"${Wording.plugin(Wording.PluginId(id))}: ${Wording.humanize(Wording.Id(rest.mkString(" "))).toLowerCase.replace("api key", "API key")}"
        )
      case _ => Name(Named.getOrElse(relative.value, Wording.humanize(Wording.Id(relative.value.replace('.', ' ')))))

  /** How a credential is held, in words. Pure. */
  def state(c: ConfigEdit.Credential): State = c.form.value match
    case "unset" => State("Not set")
    case "env"   => State("Set · from an environment variable")
    case "vault" =>
      State(
        s"Set · from ${c.shown.flatMap(_.value.split(':').headOption).map(v => Wording.plugin(Wording.PluginId(v))).getOrElse("a password manager")}"
      )
    case "value" => State("Set · the value is written in the settings file")
    case _       => State("Set")

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
        case Some(Left(m))   => ErrorLine.text(ErrorLine.Problem(s"Could not read the settings: $m"))
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
                creds.filter(_.credential.exists(_.rule.value == "reference")).map(referenceRow)
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
              div(
                cls := "settings-card-body",
                creds.filter(_.credential.exists(_.rule.value == "environment")).map(envRow)
              )
            )
          )
      },
      SettingsStore.notice
    )

  private def envRow(f: ConfigEdit.Field): HtmlElement =
    div(
      cls := "setting-row key-row",
      dataAttr("key") := f.relative.value,
      div(
        cls := "setting-head",
        span(cls := "setting-name", name(f.relative).value),
        span(cls := "setting-key muted", f.relative.value)
      ),
      p(cls := "key-state", f.credential.map(c => state(c).value).getOrElse("")),
      f.envVar.map(v => p(cls := "muted", s"Environment variable: $v"))
    )

  private def referenceRow(f: ConfigEdit.Field): HtmlElement =
    val form = Var(Form.Env)
    val entry = Var("")
    val consent = Var(false)
    val problem = Var(Option.empty[String])
    def save(): Unit =
      reference(form.now(), ReferenceEntry(entry.now())) match
        case Left(why) => problem.set(Some(why.value))
        case Right(r)  =>
          problem.set(None)
          SettingsStore
            .save(
              List(ConfigEdit.Change(ConfigEdit.Change.Key(f.key.value), Some(ConfigEdit.Change.Value(r.value)))),
              literalConsent = SettingsStore.SaveLiteralConsent(consent.now())
            )
            .foreach(_ => entry.set(""))
    val id = "key-" + f.key.value.replaceAll("[^A-Za-z0-9_-]", "-")
    div(
      cls := "setting-row key-row",
      dataAttr("key") := f.relative.value,
      div(
        cls := "setting-head",
        span(cls := "setting-name", name(f.relative).value),
        span(cls := "setting-key muted", f.relative.value)
      ),
      p(
        cls := "key-state",
        f.credential
          .map(c => state(c).value + c.shown.filter(_ => c.form.value != "value").fold("")(s => s" ($s)"))
          .getOrElse("")
      ),
      div(
        cls := "key-edit toolbar",
        label(forId := s"$id-form", cls := "sr-only", "Where the key is kept"),
        select(
          idAttr := s"$id-form",
          cls := "field key-form",
          Form.values.toList.map(x => option(value := x.scheme.value, x.label.value)),
          controlled(
            value <-- form.signal.map(_.scheme.value),
            onChange.mapToValue --> (v => form.set(Form.of(Form.Scheme(v))))
          )
        ),
        label(forId := s"$id-entry", cls := "sr-only", "The reference"),
        input(
          idAttr := s"$id-entry",
          cls := "field key-entry",
          typ <-- form.signal.map(x => if x == Form.Value then "password" else "text"),
          placeholder <-- form.signal.map(_.hint.value),
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
            onClick --> (_ => SettingsStore.save(List(ConfigEdit.Change(ConfigEdit.Change.Key(f.key.value), None))))
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
      child.maybe <-- problem.signal.map(_.map(m => ErrorLine.text(ErrorLine.Problem(m))))
    )

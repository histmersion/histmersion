package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Settings: the config file's settings as Settings and `histmersion config get` show them, and the changes a
  * person makes to them. Settings live in the config file, never in the database: the server writes the
  * file (keeping its comments, order, includes and anything written by hand) and reloads it. A credential is
  * never shown — only whether it is set and how it is referred to. */
object ConfigEdit:

  /** Where a setting's value comes from. */
  enum Source(val wire: String, val label: String):
    case Default     extends Source("default", "the default")
    case File        extends Source("file", "the settings file")
    case Environment extends Source("environment", "an environment variable")

  object Source:
    def parse(s: String): Option[Source] = values.find(_.wire == s.trim.toLowerCase)

  /** A credential's state: `rule` `environment` (the value itself, kept in the server's environment only) or
    * `reference` (a plugin's, written as `env:NAME` or `<vault>:<entry>`); `form` how it is held now
    * (`unset`, `env`, `vault`, `value`, `plain`); `shown` the reference where it may be shown (`env:NAME`,
    * `keepassxc:Entry`, `value:***`) — never a value. */
  final case class Credential(rule: String, form: String, shown: Option[String] = None)
      derives KindlingsCodecAsObject, KindlingsSchema:
    def isSet: Boolean = form != "unset"

  /** One setting.
    *
    * @param kind      `text`, `number`, `flag`, `list` or `any` (a plugin's own key)
    * @param doc       what reference.conf says of it
    * @param default   its default, as HOCON writes it
    * @param value     what the settings file writes, as HOCON writes it (never for a credential)
    * @param effective what applies now — environment, file, default (never for a credential)
    * @param restart   why a running server takes it on only after a restart
    * @param locked    why Settings cannot change it (a substitution, an included file); edit the file then */
  final case class Field(
      key: String,
      section: String,
      kind: String,
      doc: String = "",
      default: Option[String] = None,
      value: Option[String] = None,
      effective: Option[String] = None,
      source: String = Source.Default.wire,
      envVar: Option[String] = None,
      restart: Option[String] = None,
      credential: Option[Credential] = None,
      locked: Option[String] = None
  ) derives KindlingsCodecAsObject, KindlingsSchema:
    /** The key without `histmersion.`. */
    def relative: String = key.stripPrefix("histmersion.")

  /** A top-level block of settings (`backups`), with what reference.conf says of it. */
  final case class Section(id: String, doc: String = "", fields: List[Field] = Nil) derives KindlingsCodecAsObject, KindlingsSchema

  /** The settings, as the server reads its settings file now.
    *
    * @param file        the settings file (`None`: the server has none to write)
    * @param exists      the file is there
    * @param version     the file's version: a change names it, so a change made in another window or by hand
    *                    in the meantime is refused rather than lost
    * @param problems    what is wrong with the file (an unknown key, a file that does not read)
    * @param restartable the server can be restarted from here (it runs under a service that starts it again)
    * @param setupDue    the first-run setup has not been done */
  final case class Page(
      file: Option[String] = None,
      exists: Boolean = false,
      version: String = "none",
      sections: List[Section] = Nil,
      problems: List[String] = Nil,
      restartable: Boolean = false,
      setupDue: Boolean = false
  ) derives KindlingsCodecAsObject, KindlingsSchema:
    def fields: List[Field] = sections.flatMap(_.fields)
    def field(key: String): Option[Field] =
      val full = if key.startsWith("histmersion.") then key else s"histmersion.$key"
      fields.find(_.key == full)

  object Page:
    given Schema[Page] = summon[KindlingsSchema[Page]].schema

  /** One change: `value` as a person typed it (read as the setting's kind), `None` to take the setting out
    * of the file (back to its default). */
  final case class Change(key: String, value: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema

  /** Changes made together, against the file's `version` (none: as it is now). `literalConsent`: the person
    * confirmed that a credential is to be written as `value:…`. `reload`: apply them to the running server. */
  final case class Write(changes: List[Change], version: Option[String] = None, literalConsent: Boolean = false,
      reload: Boolean = true) derives KindlingsCodecAsObject, KindlingsSchema

  object Write:
    given Schema[Write] = summon[KindlingsSchema[Write]].schema

  final case class RestartNote(key: String, why: String) derives KindlingsCodecAsObject, KindlingsSchema

  /** What a write did: the settings it changed, those the file already said, the old file kept beside it, the
    * file's new version, the changes that wait for a restart, the warnings, and the reload. */
  final case class Written(
      file: String,
      changed: List[String] = Nil,
      unchanged: List[String] = Nil,
      backup: Option[String] = None,
      version: String = "none",
      created: Boolean = false,
      restart: List[RestartNote] = Nil,
      warnings: List[String] = Nil,
      reload: Option[ReloadReport] = None,
      restartable: Boolean = false
  ) derives KindlingsCodecAsObject, KindlingsSchema

  object Written:
    given Schema[Written] = summon[KindlingsSchema[Written]].schema

  /** The answer to a restart: started (the server stops in a moment and its service starts it again), or why not. */
  final case class Restart(accepted: Boolean, message: String) derives KindlingsCodecAsObject, KindlingsSchema

  object Restart:
    given Schema[Restart] = summon[KindlingsSchema[Restart]].schema

  /** The error code of a change refused because the file changed since it was read. */
  val CodeChanged = "settings_changed"
  /** The error code of a change refused: a value, a credential, a locked setting. */
  val CodeRefused = "settings_refused"
  /** The error code of a server that cannot restart itself, or has no settings file. */
  val CodeUnavailable = "settings_unavailable"

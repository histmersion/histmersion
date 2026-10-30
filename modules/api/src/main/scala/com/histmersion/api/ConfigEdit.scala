package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object ConfigEdit:

  enum Source(val wire: String, val label: String):
    case Default extends Source("default", "the default")
    case File extends Source("file", "the settings file")
    case Environment extends Source("environment", "an environment variable")

  object Source:
    def parse(s: String): Option[Source] = values.find(_.wire == s.trim.toLowerCase)

  final case class Credential(rule: String, form: String, shown: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isSet: Boolean = form != "unset"

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def relative: String = key.stripPrefix("histmersion.")

  final case class Section(id: String, doc: String = "", fields: List[Field] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema

  final case class Page(
      file: Option[String] = None,
      exists: Boolean = false,
      version: String = "none",
      sections: List[Section] = Nil,
      problems: List[String] = Nil,
      restartable: Boolean = false,
      setupDue: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def fields: List[Field] = sections.flatMap(_.fields)
    def field(key: String): Option[Field] =
      val full = if key.startsWith("histmersion.") then key else s"histmersion.$key"
      fields.find(_.key == full)

  object Page:
    given Schema[Page] = summon[KindlingsSchema[Page]].schema

  final case class Change(key: String, value: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema

  final case class Write(
      changes: List[Change],
      version: Option[String] = None,
      literalConsent: Boolean = false,
      reload: Boolean = true
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Write:
    given Schema[Write] = summon[KindlingsSchema[Write]].schema

  final case class RestartNote(key: String, why: String) derives KindlingsCodecAsObject, KindlingsSchema

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Written:
    given Schema[Written] = summon[KindlingsSchema[Written]].schema

  final case class Restart(accepted: Boolean, message: String) derives KindlingsCodecAsObject, KindlingsSchema

  object Restart:
    given Schema[Restart] = summon[KindlingsSchema[Restart]].schema

  val CodeChanged = "settings_changed"

  val CodeRefused = "settings_refused"

  val CodeUnavailable = "settings_unavailable"

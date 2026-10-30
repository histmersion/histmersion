package com.histmersion.config.edit

/** A setting as it may be SHOWN — to a person, a client or an agent: its values as HOCON writes them, and for a
  * credential none of them (neither what the file writes, nor what applies, nor the default), only the rule it
  * is written by (`environment`, `reference`), how it is held (`unset`, `env`, `vault`, `value`, `plain`) and the
  * reference where that may be shown. */
final case class SettingShown(key: String, section: String, kind: String, doc: String, default: Option[String],
    value: Option[String], effective: Option[String], source: String, envVar: Option[String], restart: Option[String],
    credentialRule: Option[String], credentialForm: Option[String], credentialShown: Option[String], locked: Option[String])

object SettingShown:
  def of(r: SettingReading): SettingShown =
    val f = r.field
    val secret = r.credential.isDefined
    SettingShown(
      key = f.key.value, section = f.section, kind = f.kind.wire, doc = f.doc,
      default = Option.unless(secret)(f.default.map(_.hocon)).flatten,
      value = Option.unless(secret)(r.inFile.map(_.hocon)).flatten,
      effective = Option.unless(secret)(r.effective.map(_.hocon)).flatten,
      source = r.source match
        case SettingSource.Default     => "default"
        case SettingSource.File        => "file"
        case SettingSource.Environment => "environment",
      envVar = f.envVar, restart = f.restart,
      credentialRule = r.credential.map(_ => f.secret match
        case SecretRule.FromEnvironment => "environment"
        case _                          => "reference"),
      credentialForm = r.credential.map(_.wire),
      credentialShown = r.shown,
      locked = r.locked.map(_.message))

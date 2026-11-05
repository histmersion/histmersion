package com.histmersion.config.edit

/** A setting as it may be SHOWN — to a person, a client or an agent: its values as HOCON writes them, and for a
  * credential none of them (neither what the file writes, nor what applies, nor the default), only the rule it is
  * written by (`environment`, `reference`), how it is held (`unset`, `env`, `vault`, `value`, `plain`) and the
  * reference where that may be shown.
  */
final case class SettingShown(
    key: SettingShown.Key,
    section: SettingShown.Section,
    kind: SettingShown.Kind,
    doc: SettingShown.Doc,
    default: Option[SettingShown.Default],
    value: Option[SettingShown.Value],
    effective: Option[SettingShown.Effective],
    source: SettingShown.Source,
    envVar: Option[SettingShown.EnvVar],
    restart: Option[SettingShown.Restart],
    credentialRule: Option[SettingShown.CredentialRule],
    credentialForm: Option[SettingShown.CredentialForm],
    credentialShown: Option[SettingShown.CredentialShown],
    locked: Option[SettingShown.Locked]
)

object SettingShown:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Section = Section.T
  object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Doc = Doc.T
  object Doc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Default = Default.T
  object Default extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Effective = Effective.T
  object Effective extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvVar = EnvVar.T
  object EnvVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Restart = Restart.T
  object Restart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CredentialRule = CredentialRule.T
  object CredentialRule extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CredentialForm = CredentialForm.T
  object CredentialForm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CredentialShown = CredentialShown.T
  object CredentialShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Locked = Locked.T
  object Locked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(r: SettingReading): SettingShown =
    val f = r.field
    val secret = r.credential.isDefined
    SettingShown(
      key = f.key.value,
      section = f.section,
      kind = f.kind.wire,
      doc = f.doc,
      default = Option.unless(secret)(f.default.map(_.hocon)).flatten,
      value = Option.unless(secret)(r.inFile.map(_.hocon)).flatten,
      effective = Option.unless(secret)(r.effective.map(_.hocon)).flatten,
      source = r.source match
        case SettingSource.Default     => "default"
        case SettingSource.File        => "file"
        case SettingSource.Environment => "environment",
      envVar = f.envVar,
      restart = f.restart,
      credentialRule = r.credential.map(_ =>
        f.secret match
          case SecretRule.FromEnvironment => "environment"
          case _                          => "reference"
      ),
      credentialForm = r.credential.map(_.wire),
      credentialShown = r.shown,
      locked = r.locked.map(_.message)
    )

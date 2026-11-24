package com.histmersion
package config.edit

import com.histmersion.util.problem.{Problem, Problems}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** A setting as it may be SHOWN — to a person, a client or an agent: its values as HOCON writes them, and for a
  * credential none of them (neither what the file writes, nor what applies, nor the default), only the rule it is
  * written by (`environment`, `reference`), how it is held (`unset`, `env`, `vault`, `value`, `plain`) and the
  * reference where that may be shown.
  */
@movesTo(FeaturePlace.UtilSettings)
final case class SettingShown(
    key: SettingKey,
    section: SettingField.Section,
    kind: SettingKind.Wire,
    doc: SettingField.Doc,
    default: Option[SettingValue.Hocon],
    value: Option[SettingValue.Hocon],
    effective: Option[SettingValue.Hocon],
    source: SettingShown.Source,
    envVar: Option[SettingField.EnvVar],
    restart: Option[SettingField.Restart],
    credentialRule: Option[SettingShown.CredentialRule],
    credentialForm: Option[SettingShown.CredentialForm],
    credentialShown: Option[SettingReading.Shown],
    locked: Option[Problems]
)

object SettingShown:
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type CredentialRule = CredentialRule.T
  object CredentialRule extends Newtype[String, Pure]
  type CredentialForm = CredentialForm.T
  object CredentialForm extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(r: SettingReading): SettingShown =
    val f = r.field
    val secret = r.credential.isDefined
    SettingShown(
      key = f.key,
      section = f.section,
      kind = f.kind.wire,
      doc = f.doc,
      default = Option.unless(secret)(f.default.map(_.hocon)).flatten,
      value = Option.unless(secret)(r.inFile.map(_.hocon)).flatten,
      effective = Option.unless(secret)(r.effective.map(_.hocon)).flatten,
      source = r.source match
        case SettingSource.Default     => Source("default")
        case SettingSource.File        => Source("file")
        case SettingSource.Environment => Source("environment"),
      envVar = f.envVar,
      restart = f.restart,
      credentialRule = r.credential.map(_ =>
        f.secret match
          case SecretRule.FromEnvironment => CredentialRule("environment")
          case _                          => CredentialRule("reference")
      ),
      credentialForm = r.credential.map(c => CredentialForm(c.wire)),
      credentialShown = r.shown,
      locked = r.locked.map(_.message)
    )

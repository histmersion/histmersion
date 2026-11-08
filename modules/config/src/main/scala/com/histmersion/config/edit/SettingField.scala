package com.histmersion
package config.edit

import io.github.iltotore.iron.constraint.any.Pure

/** One setting the config file may hold, as reference.conf documents it.
  *
  * @param default
  *   what applies when neither the file nor the environment sets it (`None`: nothing, or not a plain value)
  * @param envVar
  *   the environment variable that overrides it, if reference.conf names one
  * @param restart
  *   why a running server takes it on only when restarted, if it does
  */
final case class SettingField(
    key: SettingKey,
    kind: SettingKind,
    default: Option[SettingValue],
    doc: SettingField.Doc,
    envVar: Option[SettingField.EnvVar],
    restart: Option[SettingField.Restart],
    secret: SecretRule
):
  /** The top-level block it is in (`backups` of `histmersion.backups.keep`). */
  def section: SettingField.Section = SettingField.Section(key.segments.lift(1).map(_.value).getOrElse(""))

object SettingField:
  type Section = Section.T
  object Section extends Newtype[String, Pure]

  type Doc = Doc.T
  object Doc extends Newtype[String, Pure]
  type EnvVar = EnvVar.T
  object EnvVar extends Newtype[String, Pure]
  type Restart = Restart.T
  object Restart extends Newtype[String, Pure]

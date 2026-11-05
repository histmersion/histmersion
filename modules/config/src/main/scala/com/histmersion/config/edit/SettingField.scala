package com.histmersion.config.edit

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
  def section: String = key.segments.lift(1).getOrElse("")

object SettingField:
  type Doc = Doc.T
  object Doc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvVar = EnvVar.T
  object EnvVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Restart = Restart.T
  object Restart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

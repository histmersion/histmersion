package com.histmersion.config.edit

import org.ekrich.config.Config

/** What a write did to the config file.
  *
  * @param file
  *   where the file is, as messages name it
  * @param changed
  *   the settings it changed
  * @param unchanged
  *   the settings the file already said so (nothing was written for them)
  * @param backup
  *   the previous file, kept beside it (`histmersion.conf.bak`); none when nothing was written
  * @param created
  *   the file was not there before
  * @param restart
  *   the changed settings a running server takes on only after a restart, with why
  * @param warnings
  *   what works but should be known: a `value:` credential, an environment variable that overrides the new value
  */
final case class WriteOutcome(
    file: WriteOutcome.File,
    changed: List[SettingKey],
    unchanged: List[SettingKey],
    backup: Option[WriteOutcome.Backup],
    version: FileVersion,
    created: WriteOutcome.Created,
    restart: List[(SettingKey, WriteOutcome.RestartPart2)],
    warnings: List[WriteOutcome.Warnings]
):
  def wrote: Boolean = changed.nonEmpty

object WriteOutcome:
  /** What `edited` did to `file`, now at `version`: the restart-only settings among the changed ones, and the warnings
    * — `notes`, and an environment variable of `env` that sets a changed setting too (and wins over the file).
    */
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Backup = Backup.T
  object Backup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Created = Created.T
  object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestartPart2 = RestartPart2.T
  object RestartPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(
      file: String,
      schema: ConfigSchema,
      env: Config,
      version: FileVersion,
      edited: EditedText,
      backup: Option[String],
      notes: List[String],
      created: Boolean = false
  ): WriteOutcome =
    val fields = edited.changed.flatMap(schema.field)
    val shadowed = fields.flatMap(f =>
      f.envVar
        .filter(v => env.hasPath(v))
        .map(v =>
          s"The environment variable $v also sets ${f.key.relative}, and wins over the settings file while it is set."
        )
    )
    WriteOutcome(
      file,
      edited.changed,
      edited.unchanged,
      backup,
      version,
      created,
      restart = fields.flatMap(f => f.restart.map(f.key -> _)),
      warnings = notes ++ shadowed
    )

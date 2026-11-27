package com.histmersion
package config.edit

import org.ekrich.config.Config
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

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
@movesTo(FeaturePlace.UtilSettings)
final case class WriteOutcome(
    file: SettingsFile.Shown,
    changed: List[SettingKey],
    unchanged: List[SettingKey],
    backup: Option[WriteOutcome.Backup],
    version: FileVersion,
    created: WriteOutcome.Created,
    restart: List[(SettingKey, SettingField.Restart)],
    warnings: List[SettingPolicy.Note]
):
  def wrote: Boolean = changed.nonEmpty

object WriteOutcome:
  /** What `edited` did to `file`, now at `version`: the restart-only settings among the changed ones, and the warnings
    * — `notes`, and an environment variable of `env` that sets a changed setting too (and wins over the file).
    */
  type Backup = Backup.T
  object Backup extends Newtype[String, Pure]
  type Created = Created.T
  object Created extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(
      file: SettingsFile.Shown,
      schema: ConfigSchema,
      env: Config,
      version: FileVersion,
      edited: EditedText,
      backup: Option[Backup],
      notes: List[SettingPolicy.Note],
      created: Created = Created(false)
  ): WriteOutcome =
    val fields = edited.changed.flatMap(schema.field)
    val shadowed = fields.flatMap(f =>
      f.envVar
        .filter(v => env.hasPath(v.value))
        .map(v =>
          SettingPolicy.Note(
            s"The environment variable $v also sets ${f.key.relative}, and wins over the settings file while it is set."
          )
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

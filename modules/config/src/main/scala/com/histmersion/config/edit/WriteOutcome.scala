package com.histmersion.config.edit

import org.ekrich.config.Config

import java.nio.file.Path

/** What a write did to the config file.
  *
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
    file: Path,
    changed: List[SettingKey],
    unchanged: List[SettingKey],
    backup: Option[Path],
    version: FileVersion,
    created: Boolean,
    restart: List[(SettingKey, String)],
    warnings: List[String]
):
  def wrote: Boolean = changed.nonEmpty

object WriteOutcome:
  /** What `edited` did to the file at `path`: the restart-only settings among the changed ones, and the warnings —
    * `notes`, and an environment variable of `env` that sets a changed setting too (and wins over the file).
    */
  def of(
      path: Path,
      schema: ConfigSchema,
      env: Config,
      version: FileVersion,
      edited: EditedText,
      backup: Option[Path],
      notes: List[String]
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
      path,
      edited.changed,
      edited.unchanged,
      backup,
      version,
      created = false,
      restart = fields.flatMap(f => f.restart.map(f.key -> _)),
      warnings = notes ++ shadowed
    )

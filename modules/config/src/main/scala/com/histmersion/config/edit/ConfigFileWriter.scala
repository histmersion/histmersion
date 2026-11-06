package com.histmersion.config.edit

import org.ekrich.config.Config

/** The user's config file, changed in place: the change is made to the file's text ([[ConfigTextEdit]]: comments,
  * order, includes and unknown keys kept), checked against the schema ([[SettingPolicy]]), written beside the file,
  * read back WITH its includes (the change must be what the file now says), loaded as the server loads its config
  * (`env` the environment it reads), and only then moved over the file in one step — the previous text kept as
  * `<file>.bak` ([[SettingsFile.replace]]). Nothing here needs the server or the database: the command line uses it
  * over the file on its own disk.
  */
final class ConfigFileWriter(val file: SettingsFile, env: Config, schema: ConfigSchema):

  def read(): Either[SettingError, ConfigFileState] = file.read()

  /** Make `edits`; `expected` is the version they were made against (`None`: whatever is there now). */
  def write(
      edits: List[SettingEdit],
      expected: Option[FileVersion],
      literalConsent: com.histmersion.config.edit.ConfigFileWriter.WriteLiteralConsent
  ): Either[SettingError, WriteOutcome] =
    ConfigFileWriter.lock.synchronized {
      for
        notes <- SettingPolicy.all(edits, schema, literalConsent)
        current <- read()
        _ <- Either.cond(expected.forall(_ == current.version), (), SettingError.Changed)
        edited <- ConfigTextEdit(
          current.text.getOrElse(ConfigFileWriter.NewFileHeader),
          current.parsed,
          current.here,
          edits
        )
        outcome <-
          if edited.changed.isEmpty then
            Right(WriteOutcome.of(file.shown, schema, env, current.version, edited, None, notes))
          else
            commit(current, edited, ConfigTextEdit.intended(edits)).map(bak =>
              WriteOutcome.of(
                file.shown,
                schema,
                env,
                FileVersion.of(Some(edited.text)),
                edited,
                bak,
                notes,
                created = current.text.isEmpty
              )
            )
      yield outcome
    }

  /** Write `edited` beside the file, check it, keep the old text as `.bak`, and move the new one in. */
  private def commit(
      current: ConfigFileState,
      edited: EditedText,
      intended: Map[com.histmersion.config.edit.ConfigFileWriter.CommitIntendedPart1, Option[SettingValue]]
  ): Either[SettingError, Option[com.histmersion.config.edit.ConfigFileWriter.CommitResultPart2]] =
    file.replace(edited.text, existed = current.text.isDefined) { staged =>
      for
        parsed <- staged.parsed.left.map(e => SettingError.Invalid(e.message))
        _ <- ConfigTextEdit.verify(parsed, staged.own, edited, intended)
        _ <- staged.load(env).left.map(SettingError.Invalid(_))
      yield ()
    }

object ConfigFileWriter:
  /** One write at a time in this process: two at once would each read the same old text. */
  type WriteLiteralConsent = WriteLiteralConsent.T
  object WriteLiteralConsent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommitResultPart2 = CommitResultPart2.T
  object CommitResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommitIntendedPart1 = CommitIntendedPart1.T
  object CommitIntendedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewFileHeader = NewFileHeaderValue
  type NewFileHeaderValue = NewFileHeaderValue.T
  object NewFileHeaderValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val lock = new Object

  /** The first lines of a config file Settings creates. */
  val NewFileHeader: com.histmersion.config.edit.ConfigFileWriter.NewFileHeader =
    """# Histmersion's settings. Settings in the web UI and `histmersion config set` change this file and keep
      |# everything else in it as it is: comments, order, and anything written by hand.
      |""".stripMargin

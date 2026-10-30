package com.histmersion.config.edit

import com.histmersion.config.AppConfig
import org.ekrich.config.{Config, ConfigException, ConfigFactory, ConfigParseOptions}

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}
import java.nio.file.attribute.PosixFileAttributeView

/** The user's config file on disk, changed in place: the change is made to the file's text ([[ConfigTextEdit]]:
  * comments, order, includes and unknown keys kept), checked against the schema ([[SettingPolicy]]), written to a
  * temporary file beside it, read back WITH its includes (the change must be what the file now says), loaded as the
  * server loads its config (`env` the environment it reads), and only then moved over the file in one step — the
  * previous text kept as `<file>.bak`. Nothing here needs the server or the database: the command line uses it
  * directly.
  */
final class ConfigFileWriter(val path: Path, env: Config, schema: ConfigSchema):

  /** The file a symbolic link at `path` points to (the new text is written beside it), else `path`. */
  private def target: Path = if Files.isSymbolicLink(path) then path.toRealPath() else path

  def read(): Either[SettingError, ConfigFileState] =
    try
      if !Files.exists(path) then Right(ConfigFileState(path, None, FileVersion.Missing, ConfigFactory.empty))
      else if !Files.isRegularFile(path) then Left(SettingError.NoFile(s"$path is not a file"))
      else
        val text = Files.readString(path, StandardCharsets.UTF_8)
        parse(path).map(c => ConfigFileState(path, Some(text), FileVersion.of(Some(text)), c))
    catch case e: IOException => Left(SettingError.Io(e.getMessage))

  /** Make `edits`; `expected` is the version they were made against (`None`: whatever is there now). */
  def write(
      edits: List[SettingEdit],
      expected: Option[FileVersion],
      literalConsent: Boolean
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
          if edited.changed.isEmpty then Right(WriteOutcome.of(path, schema, env, current.version, edited, None, notes))
          else
            commit(current, edited, ConfigTextEdit.intended(edits)).map(bak =>
              WriteOutcome
                .of(path, schema, env, current.version, edited, bak, notes)
                .copy(version = FileVersion.of(Some(edited.text)), created = current.text.isEmpty)
            )
      yield outcome
    }

  /** Write `edited` beside the file, check it, keep the old text as `.bak`, and move the new one in. */
  private def commit(
      current: ConfigFileState,
      edited: EditedText,
      intended: Map[String, Option[SettingValue]]
  ): Either[SettingError, Option[Path]] =
    val dir = target.toAbsolutePath.getParent
    try
      Files.createDirectories(dir)
      val temp = Files.createTempFile(dir, s".${path.getFileName}.", ".tmp")
      try
        Files.writeString(temp, edited.text, StandardCharsets.UTF_8)
        for
          parsed <- parse(temp).left.map(e => SettingError.Invalid(e.message))
          _ <- ConfigTextEdit.verify(parsed, o => ConfigFileState.sameFile(o, temp), edited, intended)
          _ <- AppConfig
            .loadWithUserFile(env, Some(temp))
            .left
            .map(m => SettingError.Invalid(m.replace(temp.toString, path.toString)))
          bak <- backup(current)
          _ <- move(temp, current)
        yield bak
      finally
        val _ = Files.deleteIfExists(temp)
    catch case e: IOException => Left(SettingError.Io(e.getMessage))

  private def backup(current: ConfigFileState): Either[SettingError, Option[Path]] =
    if current.text.isEmpty then Right(None)
    else
      val bak = path.resolveSibling(s"${path.getFileName}.bak")
      Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
      Right(Some(bak))

  /** The new file in place in one step, with the old one's permissions (a file holding a reference to a password may be
    * readable by its owner alone).
    */
  private def move(temp: Path, current: ConfigFileState): Either[SettingError, Unit] =
    if current.text.isDefined then
      Option(Files.getFileAttributeView(path, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)).foreach { v =>
        Files.setPosixFilePermissions(temp, v.readAttributes.permissions)
      }
    try Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    catch case _: AtomicMoveNotSupportedException => Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    Right(())

  private def parse(file: Path): Either[SettingError, Config] =
    try Right(ConfigFactory.parseFile(file.toFile, ConfigParseOptions.defaults.setAllowMissing(false)))
    catch case e: ConfigException => Left(SettingError.Unreadable(e.getMessage.replace(file.toString, path.toString)))

object ConfigFileWriter:
  /** One write at a time in this process: two at once would each read the same old text. */
  private val lock = new Object

  /** The first lines of a config file Settings creates. */
  val NewFileHeader: String =
    """# Histmersion's settings. Settings in the web UI and `histmersion config set` change this file and keep
      |# everything else in it as it is: comments, order, and anything written by hand.
      |""".stripMargin

package com.histmersion
package util.settings

import com.histmersion.config.{AppConfig, ConfigProblem, LoadedConfig, SetupConfig}
import com.histmersion.config.edit.*
import org.ekrich.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}

import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The settings file — read and changed. Settings live there and nowhere else (never in the database), so a person can
  * repair them with a text editor while the server, the database or this page is broken.
  */
trait SettingsService:
  def schema: ConfigSchema

  /** The settings now; `prefix` keeps those under it (`acquire`). */
  def read(prefix: Option[SettingKey], extra: List[SettingKey] = Nil): Either[SettingError, SettingsSnapshot]

  /** Change the file (comments, order, includes and anything written by hand kept), against `expected`. */
  def write(
      edits: List[SettingEdit],
      expected: Option[FileVersion],
      literalConsent: SettingPolicy.LiteralConsent
  ): Either[SettingError, WriteOutcome]

  /** The first-run setup has been neither finished nor skipped, and the file sets nothing of its own. */
  def setupDue: Boolean

object SettingsService:
  type ConfigPath = ConfigPath.T
  object ConfigPath extends RefinedType[String, Pure]

  /** The settings file of `loaded` — the one the server read, `file` (`None`: it read none) — with the environment it
    * read it with.
    */

  def apply(loaded: () => LoadedConfig, file: () => Option[SettingsFile]): SettingsService =
    Implementation(loaded, file, ConfigSchema.reference)

  private def noFile = SettingError.NoFile(
    SettingError.NoFile.Why(
      "This server was started without a settings file, so Settings cannot change anything. " +
        "Set HISTMERSION_CONFIG to the file to use and start it again."
    )
  )

  /** The snapshot of a file read as `state`, its settings loaded into `loaded` (or why they would not load). */
  private[settings] def snapshot(
      schema: ConfigSchema,
      state: ConfigFileState,
      loaded: Either[ConfigProblem, LoadedConfig],
      env: Config,
      prefix: Option[SettingKey],
      completed: SetupConfig.Completed,
      file: Option[SettingsSnapshot.File],
      extra: List[SettingKey]
  ): SettingsSnapshot =
    val merged =
      loaded.fold(_ => AppConfig.loadWithUserText(env, "").fold(_ => ConfigFactory.empty, _.merged), _.merged)
    val problems = loaded.fold(p => List(p.value), l => l.file.warnings.map(_.value)).map(SettingsSnapshot.Problems(_))
    SettingsSnapshot(
      file,
      SettingsSnapshot.Exists(state.text.isDefined),
      state.version,
      SettingsReader.read(schema, state, merged, env, prefix, extra),
      problems,
      setupDue = SettingsSnapshot.SetupDue(!completed.value && !ConfigSchema.setsAnything(state.parsed))
    )

  final private class Implementation(
      loaded: () => LoadedConfig,
      file: () => Option[SettingsFile],
      val schema: ConfigSchema
  ) extends SettingsService:
    private def writer: Option[ConfigFileWriter] =
      val l = loaded()
      file().map(ConfigFileWriter(_, l.env, schema))

    def read(prefix: Option[SettingKey], extra: List[SettingKey]): Either[SettingError, SettingsSnapshot] =
      val l = loaded()
      writer
        .toRight(noFile)
        .flatMap(w =>
          w.read().map { state =>
            val now = AppConfig.loadWithUserFile(l.env, l.file.path)
            snapshot(
              schema,
              state,
              now,
              l.env,
              prefix,
              now.fold(_ => l.config.setup.completed, _.config.setup.completed),
              Some(SettingsSnapshot.File(w.file.shown.value)),
              extra
            )
          }
        )

    def write(
        edits: List[SettingEdit],
        expected: Option[FileVersion],
        literalConsent: SettingPolicy.LiteralConsent
    ): Either[SettingError, WriteOutcome] =
      writer.toRight(noFile).flatMap(_.write(edits, expected, literalConsent))

    def setupDue: Boolean =
      !loaded().config.setup.completed.value && writer
        .flatMap(_.read().toOption)
        .exists(s => !ConfigSchema.setsAnything(s.parsed))

  /** The same settings over a file's text held in memory (`None`: no file yet), changed by the same rules — the schema,
    * the text edit that keeps the rest of the file, the read-back and the load — but never written to disk; a file it
    * names in an include is not read.
    */
  final class InMemory(
      text: AtomicReference[Option[ConfigFileState.Text]],
      env: Config = ConfigFactory.empty,
      val schema: ConfigSchema = ConfigSchema.reference
  ) extends SettingsService:
    val path: SettingsSnapshot.File = SettingsSnapshot.File(InMemory.FileName)

    def current: Option[ConfigFileState.Text] = text.get

    private def state: Either[SettingError, ConfigFileState] =
      text.get match
        case None    => Right(ConfigFileState(None, FileVersion.Missing, ConfigFactory.empty, here))
        case Some(t) =>
          try Right(ConfigFileState(Some(t), FileVersion.of(Some(t)), parse(t), here))
          catch case e: org.ekrich.config.ConfigException => Left(SettingError.Unreadable(SettingError.Unreadable.Why(e.getMessage)))

    private def parse(t: ConfigFileState.Text): Config =
      ConfigFactory.parseString(
        t.value,
        ConfigParseOptions.defaults.setOriginDescription(path.value).setSyntax(ConfigSyntax.CONF)
      )

    private def here(o: org.ekrich.config.ConfigOrigin): Boolean = o.description.startsWith(path.value)

    def read(prefix: Option[SettingKey], extra: List[SettingKey]): Either[SettingError, SettingsSnapshot] =
      state.map { s =>
        val loaded = AppConfig.loadWithUserText(env, s.text.fold("")(_.value))
        snapshot(
          schema,
          s,
          loaded,
          env,
          prefix,
          loaded.fold(_ => SetupConfig.Completed(false), _.config.setup.completed),
          Some(path),
          extra
        )
      }

    def write(
        edits: List[SettingEdit],
        expected: Option[FileVersion],
        literalConsent: SettingPolicy.LiteralConsent
    ): Either[SettingError, WriteOutcome] =
      synchronized {
        for
          notes <- SettingPolicy.all(edits, schema, literalConsent)
          current <- state
          _ <- Either.cond(expected.forall(_ == current.version), (), SettingError.Changed)
          edited <- ConfigTextEdit(current.text.getOrElse(ConfigFileWriter.NewFileHeader), current.parsed, here, edits)
          _ <-
            if edited.changed.isEmpty then Right(())
            else
              for
                parsed <-
                  (try Right(parse(edited.text))
                  catch
                    case e: org.ekrich.config.ConfigException =>
                      Left(SettingError.Invalid(SettingError.Invalid.Why(e.getMessage)))
                  )
                _ <- ConfigTextEdit.verify(parsed, here, edited, ConfigTextEdit.intended(edits))
                _ <- AppConfig.loadWithUserText(env, edited.text.value).left.map(e => SettingError.Invalid(SettingError.Invalid.Why(e.value)))
              yield text.set(Some(edited.text))
        yield
          if edited.changed.isEmpty then WriteOutcome.of(SettingsFile.Shown(path.value), schema, env, current.version, edited, None, notes)
          else
            WriteOutcome.of(
              SettingsFile.Shown(path.value),
              schema,
              env,
              FileVersion.of(Some(edited.text)),
              edited,
              current.text.map(_ => WriteOutcome.Backup(s"${path.value}.bak")),
              notes,
              created = WriteOutcome.Created(current.text.isEmpty)
            )
      }

    def setupDue: Boolean = read(None).fold(_ => false, _.setupDue.value)

  object InMemory:
    /** The name the file held in memory goes by. */
    val FileName = "histmersion.conf"

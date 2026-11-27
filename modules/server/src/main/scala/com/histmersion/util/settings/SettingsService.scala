package com.histmersion
package util.settings

import com.histmersion.util.problem.{External, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.config.{AppConfig, LoadedConfig, SetupConfig}
import com.histmersion.config.edit.*
import org.ekrich.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}

import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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

  /** The settings text `file` holds does not parse: `e`, the parser's words. */
  private[settings] def unparsable(
      file: SettingsSnapshot.File,
      e: org.ekrich.config.ConfigException
  ): Problem.Io.Corrupt =
    Problem.Io.Corrupt(
      Problem.Io.Format.Hocon,
      Problem.Where.of(file),
      Problem.Io.Damage.Unreadable(External.Reported.of(Option(e.getMessage).getOrElse("")))
    )
  type ConfigPath = ConfigPath.T
  object ConfigPath extends Newtype[String, Pure]

  /** The settings file of `loaded` — the one the server read, `file` (`None`: it read none) — with the environment it
    * read it with.
    */

  def apply(
      loaded: () => LoadedConfig,
      file: () => Option[SettingsFile],
      memory: Option[ByteCount] = None
  ): SettingsService =
    Implementation(loaded, file, ConfigSchema.reference, memory)

  private def noFile = SettingError.NoFile(Problem.Config.Unavailable(Problem.Config.Capability.SettingsFile))

  /** The snapshot of a file read as `state`, its settings loaded into `loaded` (or why they would not load); `memory`
    * is the machine's, which an `auto` size of the bundled Postgres comes from.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[settings] def snapshot(
      schema: ConfigSchema,
      state: ConfigFileState,
      loaded: Either[Problems, LoadedConfig],
      env: Config,
      prefix: Option[SettingKey],
      completed: SetupConfig.Completed,
      file: Option[SettingsSnapshot.File],
      extra: List[SettingKey],
      memory: Option[ByteCount] = None
  ): SettingsSnapshot =
    val merged =
      loaded.fold(_ => AppConfig.loadWithUserText(env, "").fold(_ => ConfigFactory.empty, _.merged), _.merged)
    val problems =
      loaded.fold(p => p.sentences.toList, l => l.file.warnings.map(_.sentence)).map(SettingsSnapshot.Problems(_))
    SettingsSnapshot(
      file,
      SettingsSnapshot.Exists(state.text.isDefined),
      state.version,
      SettingsReader.read(schema, state, merged, env, prefix, extra),
      problems,
      setupDue = SettingsSnapshot.SetupDue(!completed.value && !ConfigSchema.setsAnything(state.parsed)),
      automatic = loaded.toOption.toList.flatMap(_.config.runtime.postgres.automatic(memory))
    )

  final private class Implementation(
      loaded: () => LoadedConfig,
      file: () => Option[SettingsFile],
      val schema: ConfigSchema,
      memory: Option[ByteCount]
  ) extends SettingsService:
    private def writer: Option[ConfigFileWriter] =
      val l = loaded()
      file().map(ConfigFileWriter(_, l.env, schema))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
              extra,
              memory
            )
          }
        )

    def write(
        edits: List[SettingEdit],
        expected: Option[FileVersion],
        literalConsent: SettingPolicy.LiteralConsent
    ): Either[SettingError, WriteOutcome] =
      writer.toRight(noFile).flatMap(_.write(edits, expected, literalConsent))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
      val schema: ConfigSchema = ConfigSchema.reference,
      memory: Option[ByteCount] = None
  ) extends SettingsService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val path: SettingsSnapshot.File = SettingsSnapshot.File(InMemory.FileName)

    def current: Option[ConfigFileState.Text] = text.get

    private def state: Either[SettingError, ConfigFileState] =
      text.get match
        case None    => Right(ConfigFileState(None, FileVersion.Missing, ConfigFactory.empty, here))
        case Some(t) =>
          try Right(ConfigFileState(Some(t), FileVersion.of(Some(t)), parse(t), here))
          catch
            case e: org.ekrich.config.ConfigException =>
              Left(SettingError.Unreadable(unparsable(path, e)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def parse(t: ConfigFileState.Text): Config =
      ConfigFactory.parseString(
        t.value,
        ConfigParseOptions.defaults.setOriginDescription(path.value).setSyntax(ConfigSyntax.CONF)
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def here(o: org.ekrich.config.ConfigOrigin): Boolean = o.description.startsWith(path.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
          extra,
          memory
        )
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def write(
        edits: List[SettingEdit],
        expected: Option[FileVersion],
        literalConsent: SettingPolicy.LiteralConsent
    ): Either[SettingError, WriteOutcome] =
      synchronized {
        for
          notes <- SettingPolicy.all(edits, schema, literalConsent)
          current <- state
          _ <- expected.filterNot(_ == current.version).map(SettingError.Changed(_, current.version)).toLeft(())
          edited <- ConfigTextEdit(current.text.getOrElse(ConfigFileWriter.NewFileHeader), current.parsed, here, edits)
          _ <-
            if edited.changed.isEmpty then Right(())
            else
              for
                parsed <-
                  (try Right(parse(edited.text))
                  catch
                    case e: org.ekrich.config.ConfigException =>
                      Left(SettingError.Invalid(unparsable(path, e).input))
                  )
                _ <- ConfigTextEdit.verify(parsed, here, edited, ConfigTextEdit.intended(edits))
                _ <- AppConfig
                  .loadWithUserText(env, edited.text.value)
                  .left
                  .map(e => SettingError.Invalid(e))
              yield text.set(Some(edited.text))
        yield
          if edited.changed.isEmpty then
            WriteOutcome.of(SettingsFile.Shown(path.value), schema, env, current.version, edited, None, notes)
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

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def setupDue: Boolean = read(None).fold(_ => false, _.setupDue.value)

  object InMemory:
    /** The name the file held in memory goes by. */
    val FileName = "histmersion.conf"

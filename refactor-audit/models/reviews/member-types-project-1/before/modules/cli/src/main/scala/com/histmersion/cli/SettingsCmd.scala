package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.config.edit.*
import org.ekrich.config.ConfigFactory
import com.histmersion.api.settings.{ConfigEdit, ConfigEditMarkdown}
import com.histmersion.util.console.Console

/** `histmersion config get|set|unset|restart` — the settings file. Through the server, which writes the file it reads
  * and reloads it; with `--local`, or when the server does not answer, the file on THIS machine is read and changed
  * directly (the same checks: the server, the database and the web UI may all be down).
  */
object SettingsCmd:

  private val local = Opts
    .flag(
      "local",
      "Read or change the settings file on this machine directly, without the server (the default when it does not answer)"
    )
    .orFalse

  private val noReload = Opts.flag("no-reload", "Change the file only; do not make the server apply it").orFalse

  private val literal = Opts
    .flag(
      "literal-secret",
      "Allow a credential written as value:<the value itself> (anyone who can read the file can read it)"
    )
    .orFalse

  /** The server is down: the file on this machine instead (said once, on stderr). */
  private def orLocal[A](viaServer: => Either[Cli.Error, A], viaFile: => Either[Cli.Error, A])(using
      console: Console
  ): Either[Cli.Error, A] =
    viaServer match
      case Left(e) if e.exitCode == ExitCode.Unreachable =>
        Cli.err.println(s"(${e.message.takeWhile(_ != '(').trim}: the settings file on this machine instead)")
        viaFile
      case other => other

  def get(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "get",
    "A setting (or every setting, or those under --prefix): what the settings file says, what applies and from where, " +
      "its default and what it does — a credential only as set or not"
  ) {
    (
      Opts.argument[String]("key").orNone,
      Opts.option[String]("prefix", "The settings under this block, e.g. acquire").orNone,
      local,
      Output.jsonFlag
    ).mapN { (key, prefix, onlyLocal, json) => (c: Client) =>
      val asked = key.orElse(prefix)
      val page = if onlyLocal then Local.page(asked) else orLocal(c.settings(asked), Local.page(asked))
      page.flatMap { p =>
        key match
          case Some(k) =>
            p.field(k).toRight(Cli.Error(s"There is no setting called $k.", ExitCode.Usage)).map { f =>
              Output.emit(json, f)(ConfigEditMarkdown.field)
            }
          case None => Right(Output.emit(json, p)(ConfigEditMarkdown.page))
      }
    }
  }

  private def change(
      key: String,
      value: Option[String],
      onlyLocal: Boolean,
      reload: Boolean,
      consent: Boolean,
      json: Boolean,
      c: Client
  )(using console: Console): Either[Cli.Error, Unit] =
    val w = ConfigEdit.Write(List(ConfigEdit.Change(key, value)), literalConsent = consent, reload = reload)
    val viaFile = Local.write(w).map(out => if reload then out.modify(_.reload).setTo(c.configReload.toOption) else out)
    val done = if onlyLocal then viaFile else orLocal(c.writeSettings(w), viaFile)
    done.map(out => Output.emit(json, out)(ConfigEditMarkdown.written))

  def set(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "set",
    "Change a setting in the settings file, in place (comments, order, includes and anything written by hand are kept, " +
      "the old file is kept as .bak), checked first; then the server applies it"
  ) {
    (Opts.argument[String]("key"), Opts.argument[String]("value"), local, noReload, literal, Output.jsonFlag).mapN {
      (key, value, onlyLocal, nr, consent, json) => (c: Client) =>
        change(key, Some(value), onlyLocal, !nr, consent, json, c)
    }
  }

  def unset(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "unset",
    "Take a setting out of the settings file (back to its default), in place; then the server applies it"
  ) {
    (Opts.argument[String]("key"), local, noReload, Output.jsonFlag).mapN { (key, onlyLocal, nr, json) => (c: Client) =>
      change(key, None, onlyLocal, !nr, consent = false, json, c)
    }
  }

  def restart(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "restart",
    "Restart the server, for the settings only a restart applies (where it runs under its service: LaunchAgent, systemd)"
  ) {
    Output.jsonFlag.map { json => (c: Client) =>
      c.restartServer.map(r => Output.emit(json, r)(ConfigEditMarkdown.restart))
    }
  }

  /** The settings file on this machine, read and changed by the config module's own writer. */
  private object Local:
    private def env = ConfigFactory.systemEnvironment()

    private def file: Either[Cli.Error, LocalSettingsFile] =
      UserPaths
        .configFile(AppConfig.lookup(env))
        .toRight(Cli.Error(s"no settings file location: HOME is unset (set ${UserPaths.ConfigEnv})", ExitCode.Usage))
        .map(LocalSettingsFile(_))

    private def writer: Either[Cli.Error, ConfigFileWriter] =
      file.map(ConfigFileWriter(_, env, ConfigSchema.reference))

    private def failed(e: SettingError) = Cli.Error(e.message, ExitCode.Refused)

    def page(prefix: Option[String]): Either[Cli.Error, ConfigEdit.Page] =
      for
        f <- file
        p <- prefix.fold(Right(None))(k => SettingKey.parse(k).map(Some(_))).left.map(Cli.Error(_, ExitCode.Usage))
        state <- f.read().left.map(failed)
      yield
        val loaded = AppConfig.loadWithUserFile(env, Some(f.path))
        val merged = loaded.fold(_ => ConfigFactory.empty, _.merged)
        val shown = SettingsReader.read(ConfigSchema.reference, state, merged, env, p).map(SettingShown.of)
        val fields = shown.map(s =>
          ConfigEdit.Field(
            s.key,
            s.section,
            s.kind,
            s.doc,
            s.default,
            s.value,
            s.effective,
            s.source,
            s.envVar,
            s.restart,
            s.credentialForm.map(f =>
              ConfigEdit.Credential(s.credentialRule.getOrElse("reference"), f, s.credentialShown)
            ),
            s.locked
          )
        )
        ConfigEdit.Page(
          Some(f.shown),
          state.text.isDefined,
          state.version.value,
          fields.map(_.section).distinct.map(id => ConfigEdit.Section(id, fields = fields.filter(_.section == id))),
          loaded.fold(List(_), _.file.warnings)
        )

    def write(w: ConfigEdit.Write): Either[Cli.Error, ConfigEdit.Written] =
      for
        fw <- writer
        edits <- SettingEdit.parseAll(w.changes.map(c => c.key -> c.value), ConfigSchema.reference).left.map(failed)
        out <- fw.write(edits, None, w.literalConsent).left.map(failed)
      yield ConfigEdit.Written(
        out.file,
        out.changed.map(_.value),
        out.unchanged.map(_.value),
        out.backup,
        out.version.value,
        out.created,
        out.restart.map((k, why) => ConfigEdit.RestartNote(k.value, why)),
        out.warnings
      )

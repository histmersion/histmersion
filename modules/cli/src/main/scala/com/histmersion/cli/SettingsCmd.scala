package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.config.edit.*
import org.ekrich.config.ConfigFactory
import com.histmersion.api.settings.{ConfigEdit, ConfigEditMarkdown}
import com.histmersion.util.console.Console
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion config get|set|unset|restart` — the settings file. Through the server, which writes the file it reads
  * and reloads it; with `--local`, or when the server does not answer, the file on THIS machine is read and changed
  * directly (the same checks: the server, the database and the web UI may all be down).
  */
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion settings` arguments")
object SettingsCmd:

  /** The server is down: the file on this machine instead (said once, on stderr). */
  private def orLocal[A](viaServer: => Either[Cli.Error, A], viaFile: => Either[Cli.Error, A])(using
      console: Console
  ): Either[Cli.Error, A] =
    viaServer match
      case Left(e) if e.exitCode == ExitCode.Unreachable =>
        Cli.err.println(
          s"(${e.problems.sentences.mkString("; ").takeWhile(_ != '(').trim}: the settings file on this machine instead)"
        )
        viaFile
      case other => other

  private def change(
      key: String,
      value: Option[String],
      onlyLocal: Boolean,
      reload: Boolean,
      consent: Boolean,
      json: Cli.JsonOutput,
      c: Client,
      local: ConfigEdit.Write => Either[Cli.Error, ConfigEdit.Written]
  )(using console: Console): Either[Cli.Error, Unit] =
    val w = ConfigEdit.Write(
      List(ConfigEdit.Change(ConfigEdit.Change.Key(key), value.map(ConfigEdit.Change.Value(_)))),
      literalConsent = ConfigEdit.Write.LiteralConsent(consent),
      reload = ConfigEdit.Write.Reload(reload)
    )
    def viaFile = local(w).map(out => if reload then out.modify(_.reload).setTo(c.configReload.toOption) else out)
    val done = if onlyLocal then viaFile else orLocal(c.writeSettings(w), viaFile)
    done.map(out => Output.emit(json, out)(ConfigEditMarkdown.written))

  /** The settings file on this machine, read and changed by the config module's own writer. */
  private object Local:

    private def env = ConfigFactory.systemEnvironment()

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    private def file: Either[Problems, LocalSettingsFile] =
      UserPaths
        .configFile(k => AppConfig.lookup(env)(k.value).map(Environment.VariablesValue(_)))
        .toRight(
          Problem.Unclassified(Legacy.Text(s"no settings file location: HOME is unset (set ${UserPaths.ConfigEnv})"))
        )
        .map(LocalSettingsFile(_))

    private def writer: Either[Problems, ConfigFileWriter] =
      file.map(ConfigFileWriter(_, env, ConfigSchema.reference))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    private def failed(e: SettingError) = Cli.Error(e.message)

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def page(prefix: Option[Settings.Prefix]): Either[Cli.Error, ConfigEdit.Page] =
      for
        f <- file.left.map(Cli.Error(_))
        p <- prefix
          .fold(Right(None))(k => SettingKey.parse(SettingKey.ParseRaw(k.value)).map(Some(_)))
          .left
          .map(p => Cli.Error(p))
        state <- f.read().left.map(failed)
      yield
        val loaded = AppConfig.loadWithUserFile(env, Some(f.path))
        val merged = loaded.fold(_ => ConfigFactory.empty, _.merged)
        val shown = SettingsReader.read(ConfigSchema.reference, state, merged, env, p).map(SettingShown.of)
        val fields = shown.map(s =>
          ConfigEdit.Field(
            ConfigEdit.Field.Key(s.key.value),
            ConfigEdit.Field.Section(s.section.value),
            ConfigEdit.Field.Kind(s.kind.value),
            ConfigEdit.Field.Doc(s.doc.value),
            s.default.map(v => ConfigEdit.Field.Default(v.value)),
            s.value.map(v => ConfigEdit.Field.Value(v.value)),
            s.effective.map(v => ConfigEdit.Field.Effective(v.value)),
            ConfigEdit.Field.Source(s.source.value),
            s.envVar.map(v => ConfigEdit.Field.EnvVar(v.value)),
            s.restart.map(v => ConfigEdit.Field.Restart(v.value)),
            s.credentialForm.map(f =>
              ConfigEdit.Credential(
                ConfigEdit.Credential.Rule(s.credentialRule.fold("reference")(_.value)),
                ConfigEdit.Credential.Form(f.value),
                s.credentialShown.map(v => ConfigEdit.Credential.Shown(v.value))
              )
            ),
            s.locked.map(v => ConfigEdit.Field.Locked(v.sentences.mkString("; ")))
          )
        )
        ConfigEdit.Page(
          Some(ConfigEdit.Page.File(f.shown.value)),
          ConfigEdit.Page.Exists(state.text.isDefined),
          ConfigEdit.Page.Version(state.version.value),
          fields.map(_.section).distinct.map(id => ConfigEdit.Section(id, fields = fields.filter(_.section == id))),
          loaded.fold(
            l => l.sentences.toList.map(ConfigEdit.Page.Problems(_)),
            _.file.warnings.map(w => ConfigEdit.Page.Problems(w.sentence))
          )
        )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def write(w: ConfigEdit.Write): Either[Cli.Error, ConfigEdit.Written] =
      for
        fw <- writer.left.map(Cli.Error(_))
        edits <- SettingEdit
          .parseAll(
            w.changes.map(c => SettingKey.ParseRaw(c.key.value) -> c.value.map(v => SettingValue.Typed(v.value))),
            ConfigSchema.reference
          )
          .left
          .map(failed)
        out <- fw.write(edits, None, SettingPolicy.LiteralConsent(w.literalConsent.value)).left.map(failed)
      yield ConfigEdit.Written(
        ConfigEdit.Written.File(out.file.value),
        out.changed.map(k => ConfigEdit.Written.Changed(k.value)),
        out.unchanged.map(k => ConfigEdit.Written.Unchanged(k.value)),
        out.backup.map(b => ConfigEdit.Written.Backup(b.value)),
        ConfigEdit.Written.Version(out.version.value),
        ConfigEdit.Written.Created(out.created.value),
        out.restart.map((k, why) =>
          ConfigEdit.RestartNote(ConfigEdit.RestartNote.Key(k.value), ConfigEdit.RestartNote.Why(why.value))
        ),
        out.warnings.map(n => ConfigEdit.Written.Warnings(n.value))
      )

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Get(key: Option[String], prefix: Option[String], onlyLocal: Boolean, json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case SetCmd(key: String, value: String, onlyLocal: Boolean, nr: Boolean, consent: Boolean, json: Cli.JsonOutput)
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Unset(key: String, onlyLocal: Boolean, nr: Boolean, json: Cli.JsonOutput)
    case Restart(json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c, Local.write)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client, local: ConfigEdit.Write => Either[Cli.Error, ConfigEdit.Written])(using
      console: Console
  ):
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Get(key, prefix, onlyLocal, json) =>
          val asked = key.orElse(prefix)
          val askedPrefix = asked.map(Settings.Prefix(_))
          val page =
            if onlyLocal then Local.page(askedPrefix) else orLocal(c.settings(askedPrefix), Local.page(askedPrefix))
          page.flatMap { p =>
            key match
              case Some(k) =>
                p.field(ConfigEdit.Field.Key(k))
                  .toRight(
                    Cli.Error(Problem.Unclassified(Legacy.Text(s"There is no setting called $k.")))
                  )
                  .map { f =>
                    Output.emit(json, f)(ConfigEditMarkdown.field)
                  }
              case None => Right(Output.emit(json, p)(ConfigEditMarkdown.page))
          }
        case Command.SetCmd(key, value, onlyLocal, nr, consent, json) =>
          change(key, Some(value), onlyLocal, !nr, consent, json, c, local)
        case Command.Unset(key, onlyLocal, nr, json) =>
          change(key, None, onlyLocal, !nr, consent = false, json, c, local)
        case Command.Restart(json) =>
          c.restartServer.map(r => Output.emit(json, r)(ConfigEditMarkdown.restart))

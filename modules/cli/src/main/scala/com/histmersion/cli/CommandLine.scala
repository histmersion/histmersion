package com.histmersion
package cli

import com.histmersion.api.BBox
import com.histmersion.api.automation.Creation
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import hearth.typename.TypeName as Named
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import org.rogach.scallop.*
import org.rogach.scallop.exceptions.{
  ExcessArguments,
  RequiredOptionNotFound,
  ScallopException,
  UnknownOption,
  WrongOptionFormat
}

import java.nio.file.Path
import com.histmersion.cli.commandline.TopArgs
import com.histmersion.cli.commandline.RenameArgs
import com.histmersion.cli.commandline.DeleteArgs
import com.histmersion.cli.commandline.BundleArgs
import com.histmersion.cli.commandline.MergeArgs
import com.histmersion.cli.commandline.ArticleArgs
import com.histmersion.cli.commandline.PlacesArgs
import com.histmersion.cli.commandline.PeriodsArgs
import com.histmersion.cli.commandline.EventsArgs
import com.histmersion.cli.commandline.AttestationsArgs
import com.histmersion.cli.commandline.CandidatesArgs
import com.histmersion.cli.commandline.GoalsArgs
import com.histmersion.cli.commandline.AgentStateArgs
import com.histmersion.cli.commandline.AgentArgs
import com.histmersion.cli.commandline.MediaArgs
import com.histmersion.cli.commandline.AssetLinksArgs
import com.histmersion.cli.commandline.UpkeepArgs
import com.histmersion.cli.commandline.AcquireArgs
import com.histmersion.cli.commandline.LibraryArgs
import com.histmersion.cli.commandline.IngestArgs
import com.histmersion.cli.commandline.ActivityArgs
import com.histmersion.cli.commandline.LogArgs
import com.histmersion.cli.commandline.StateArgs
import com.histmersion.cli.commandline.BookmarksArgs
import com.histmersion.cli.commandline.NoteArgs
import com.histmersion.cli.commandline.ConfigArgs
import com.histmersion.cli.commandline.BasemapArgs
import com.histmersion.cli.commandline.PluginArgs
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line as scallop parses it. Scallop's configuration is mutable — options are declared on an object and
  * read back after `verify()` — so it stays in this file, the core every command group shares, and in the groups'
  * adapters (package `commandline`, one per group): only they declare `opt`, `trailArg` and subcommands, and they hand
  * the rest of the CLI a typed [[Cli.Command]] (or the help, or a [[Problem]]). Every argument is parsed once, there,
  * into its newtype; scallop's exit and output go through its per-instance hooks, so the parser never ends the process.
  */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter: the one file that declares scallop options and parses a command line into a typed command"
)
object CommandLine:
  type HelpText = HelpText.T
  object HelpText extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

    extension (help: HelpText)
      /** Whether the help says `words` anywhere. */
      def mentions(words: String): Boolean = help.value.contains(words)

      /** The help as it is printed. */
      def printed: String = help.value

  /** What the command line asks for. */
  enum Outcome:
    case Run(server: Option[Client.ServerUrl], caller: AgentIdentity, command: Cli.Command, json: Cli.JsonOutput)
    case Help(text: HelpText)
    case Failed(problems: Problems, help: HelpText, json: Cli.JsonOutput)

  /** A top-level word of the command line: a command group, or a command of its own. */
  private[cli] trait Entry:
    /** The word. */
    def name: String

    /** What it is for: the line the root's help lists it with. */
    def help: String

    /** Its configuration, fresh for one parse. */
    def conf(): Subcommand

  /** The top-level words this parser owns, in the order the root's help lists them. */
  private[cli] def entries: List[Entry] = List(
    TopArgs.VersionEntry,
    TopArgs.HealthEntry,
    TopArgs.AboutEntry,
    TopArgs.SeedEntry,
    TopArgs.ReindexEntry,
    TopArgs.SearchEntry,
    TopArgs.FacetsEntry,
    TopArgs.GetEntry,
    TopArgs.PassagesEntry,
    TopArgs.GeoWhenEntry,
    TopArgs.HistogramEntry,
    TopArgs.NeighborsEntry,
    TopArgs.GraphEntry,
    TopArgs.ContradictionsEntry,
    TopArgs.PutEntry,
    TopArgs.PatchEntry,
    RenameArgs,
    DeleteArgs,
    TopArgs.ExportEntry,
    TopArgs.BackupEntry,
    TopArgs.BackupsEntry,
    TopArgs.RestoreEntry,
    TopArgs.MaintenanceEntry,
    BundleArgs.BundleEntry,
    MergeArgs.MergeEntry,
    ArticleArgs,
    PlacesArgs.PlacesEntry,
    PeriodsArgs.PeriodsEntry,
    PeriodsArgs.InitEntry,
    EventsArgs.EventsEntry,
    AttestationsArgs.AttestationsEntry,
    CandidatesArgs.CandidatesEntry,
    GoalsArgs.GoalsEntry,
    AgentStateArgs.AgentStateEntry,
    AgentArgs.AgentEntry,
    MediaArgs.MediaEntry,
    AssetLinksArgs.AssetLinksEntry,
    UpkeepArgs.LintEntry,
    UpkeepArgs.QueueEntry,
    UpkeepArgs.QueryEntry,
    UpkeepArgs.SchemaEntry,
    AcquireArgs.DiscoverEntry,
    AcquireArgs.RefsEntry,
    AcquireArgs.AssessEntry,
    AcquireArgs.FollowupsEntry,
    AcquireArgs.IngestEntry,
    AcquireArgs.ArchiveEntry,
    LibraryArgs.LibraryEntry,
    IngestArgs.ReocrEntry,
    IngestArgs.AssetsEntry,
    IngestArgs.JobsEntry,
    ActivityArgs.ActivityEntry,
    LogArgs.LogEntry,
    StateArgs.StateEntry,
    BookmarksArgs.BookmarksEntry,
    NoteArgs.NoteEntry,
    ConfigArgs.ConfigEntry,
    BasemapArgs.BasemapEntry,
    PluginArgs.PluginEntry
  )

  /** Parse `args`: the typed command, the help asked for, or what is wrong with them. */
  def parse(args: Seq[String]): Outcome =
    val root = Root(args)
    root.verify()
    root.outcome

  // ── converters: every newtype parses as its base type, once ──────────────────────────────────────

  /** A newtype's converter: its base type's, the constraint checked. */
  given newtype: [T] => (m: RefinedType.Mirror[T]) => (base: ValueConverter[m.BaseType]) => ValueConverter[T] =
    base.flatMap(v => m.ops.either(v).map(t => Some(t.asInstanceOf[T])))

  private def jsonOutput(asked: Boolean): Cli.JsonOutput = (if asked then Cli.JsonOutput.Json else Cli.JsonOutput.Text)

  /** A value read by `read`, refused with its message: a choice among words, an alternative of a sealed type. */
  private[cli] def reading[T](read: String => Either[String, T]): ValueConverter[T] =
    stringConverter.flatMap(raw => read(raw).map(Some(_)))

  /** An option given any number of times, one value each time (decline's `options`), not scallop's greedy list. */
  private def repeated[T](one: ValueConverter[T]): ValueConverter[List[T]] = Repeated(one)

  final private class Repeated[T](one: ValueConverter[T]) extends ValueConverter[List[T]]:
    def parse(s: List[(String, List[String])]): Either[String, Option[List[T]]] =
      s.foldLeft[Either[String, List[T]]](Right(Nil))((acc, given1) =>
        acc.flatMap(done => one.parse(List(given1)).map(v => done ++ v.toList))
      ).map(vs => Option.when(vs.nonEmpty)(vs))
    val argType: ArgType.V = ArgType.SINGLE

  /** Trailing arguments, any number: each one through `one`. */
  final private class Operands[T](one: ValueConverter[T]) extends ValueConverter[List[T]]:
    def parse(s: List[(String, List[String])]): Either[String, Option[List[T]]] =
      s.flatMap(_._2)
        .foldLeft[Either[String, List[T]]](Right(Nil))((acc, raw) =>
          acc.flatMap(done => one.parse(List("" -> List(raw))).map(v => done ++ v.toList))
        )
        .map(vs => Option.when(vs.nonEmpty)(vs))
    val argType: ArgType.V = ArgType.LIST

  /** What help calls a value of `T` (decline's metavars, so the help reads as it did). */
  trait Metavar[T]:
    def name: String

  object Metavar:
    def of[T](text: String): Metavar[T] = new Metavar[T]:
      def name: String = text
    given Metavar[String] = of("string")
    given Metavar[Int] = of("integer")
    given Metavar[Long] = of("integer")
    given Metavar[Double] = of("floating-point")
    given Metavar[Path] = of("path")
    given newtype: [T] => (m: RefinedType.Mirror[T]) => (base: Metavar[m.BaseType]) => Metavar[T] = of(base.name)

  /** Help in decline's layout — usage, description, options and flags, subcommands — so the help text, its snapshot and
    * the generated CLI reference read as they did.
    */
  final private class Layout(val path: String, root: Option[String] = None) extends ScallopHelpFormatter:
    override def formatHelp(s: Scallop, subcommandPrefix: String): String =
      def isFlag(o: SimpleOption) = o.converter.asInstanceOf[AnyRef] eq flagConverter
      val options = s.opts.collect { case o: SimpleOption if o.name != "help" && o.name != "version" => o }
      val operands = s.opts.collect { case o: TrailingArgsOption => o }
      def usage(o: SimpleOption): String =
        val one = if isFlag(o) then s"--${o.name}" else s"--${o.name} <${o.argName}>"
        o.converter match
          case _: Repeated[?] => if o.required then s"$one [$one]..." else s"[$one]..."
          case _              => if o.required then one else s"[$one]"
      def operand(o: TrailingArgsOption): String =
        val one = o.converter match
          case _: Operands[?] => s"<${o.name}>..."
          case _              => s"<${o.name}>"
        if o.required then one else s"[$one]"
      val own = options.map(usage) ++ operands.map(operand)
      val usages =
        if root.isDefined then
          s.subbuilders.map((name, _) => s"    ${((path :: own) :+ name).mkString(" ")}").mkString("Usage:\n", "\n", "")
        else if s.subbuilders.nonEmpty then
          (s.subbuilders.map((name, sub) =>
            sub.helpFormatter match
              case l: Layout => s"    ${l.path}"
              case _         => s"    $path $name"
          ) ++
            Option.when(own.nonEmpty)(s"    ${(path :: own).mkString(" ")}")).mkString("Usage:\n", "\n", "")
        else (s"Usage: $path" :: own).mkString(" ")
      def spelled(o: SimpleOption) =
        val long = if isFlag(o) then s"--${o.name}" else s"--${o.name} <${o.argName}>"
        o.short.fold(long)(c => if isFlag(o) then s"$long, -$c" else s"$long, -$c <${o.argName}>")
      val flags =
        ("    --help\n        Display this help text." :: options.map(o => s"    ${spelled(o)}\n        ${o.descr}"))
          .mkString("Options and flags:\n", "\n", "")
      val subcommands =
        if s.subbuilders.isEmpty then Nil
        else
          List(
            s.subbuilders.map((name, sub) => s"    $name\n        ${sub.descr}").mkString("Subcommands:\n", "\n", "")
          )
      (usages :: root.getOrElse(s.descr) :: flags :: subcommands).filter(_.nonEmpty).mkString("\n\n")

  /** A path the command reads later, refused here when the platform cannot name it. */
  private[cli] def pathOf(field: String, raw: Option[String]): Either[Problem, Option[Path]] =
    raw match
      case None    => Right(None)
      case Some(r) =>
        scala.util
          .Try(Path.of(r))
          .toEither
          .map(Some(_))
          .left
          .map(e =>
            Problem.Invalid.Malformed(
              typeName[Path],
              Problem.Constraint(Problem.Constraint.Name("Path"), Problem.Constraint.Description(e.getMessage)),
              Problem.Raw(r),
              Vector(Problem.Field(field))
            )
          )

  // ── problems ──────────────────────────────────────────────────────────────────────────────────

  /** The newtype each option and argument of one subcommand parses into, by name, so a value scallop refuses becomes a
    * typed [[Problem.Invalid.Malformed]].
    */
  final private[cli] class Types:
    private var byName = Map.empty[String, Problem.TypeName]
    private var optionNames = Vector.empty[String]

    def option(name: String, typeName: Problem.TypeName): Unit =
      byName += name -> typeName
      optionNames :+= s"--$name"

    def argument(name: String, typeName: Problem.TypeName): Unit = byName += name -> typeName

    def of(name: String): Option[Problem.TypeName] = byName.get(name)

    /** The options declared here, as the command line spells them. */
    def options: Vector[String] = optionNames

  /** What scallop names that is no value of ours: an option or an argument nobody declared. */
  type OptionName = OptionName.T
  object OptionName extends Newtype[String, Pure]
  type Operand = Operand.T
  object Operand extends Newtype[String, Pure]

  private[cli] def typeName[T](using n: Named[T]): Problem.TypeName = Problem.TypeName.of(Newtype.TypeName.of(n))

  /** Scallop's error, typed; `types` are those of the subcommand the command line names, innermost first. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def problem(e: ScallopException, types: List[Types]): Problem = e match
    case RequiredOptionNotFound(name) => Problem.Invalid.Missing.here(Problem.Field(name))
    case UnknownOption(name)          =>
      Problem.Invalid.Unknown.here(
        typeName[OptionName],
        Problem.Raw(s"--$name"),
        types.headOption.toVector.flatMap(_.options).sorted.map(Problem.Raw(_))
      )
    case WrongOptionFormat(name, raw, message) =>
      val t = types.flatMap(_.of(name)).headOption.getOrElse(typeName[String])
      Problem.Invalid.Malformed(
        t,
        Problem.Constraint(Problem.Constraint.Name(t.value), Problem.Constraint.Description(message)),
        Problem.Raw(raw),
        Vector(Problem.Field(name))
      )
    case ExcessArguments(extra) =>
      Problem.Invalid.Unknown.here(typeName[Operand], Problem.Raw(extra.mkString(" ")), Vector.empty)
    case other => Problem.Unclassified(Legacy.Text(other.getMessage))

  // ── the configuration tree ────────────────────────────────────────────────────────────────────

  /** What every level declares options with: no guessed names (that reads the class by reflection), no short names. */
  private[cli] trait Declares extends ScallopConf:
    val types: Types = Types()

    /** An option with a value, given at most once. */
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    def value[T](name: String, descr: String, required: Boolean = false)(using
        c: ValueConverter[T],
        n: Named[T],
        m: Metavar[T]
    ): ScallopOption[T] =
      types.option(name, typeName[T])
      opt[T](name = name, descr = descr, argName = m.name, required = required)(using c)

    /** An option given any number of times. */
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    def values[T](name: String, descr: String, required: Boolean = false)(using
        c: ValueConverter[T],
        n: Named[T],
        m: Metavar[T]
    ): ScallopOption[List[T]] =
      types.option(name, typeName[T])
      opt[List[T]](name = name, descr = descr, argName = m.name, required = required)(using repeated(c))

    /** The subcommands declared on this one. */
    def children: Seq[ScallopConfBase] = subconfigs

    /** Declare subcommands, in the order the help lists them. A [[Leaf]] that holds some runs alone too: its own
      * options are the command when none of them is named (`candidates [--goal g]` beside `candidates sweep`).
      */
    def holds(confs: Subcommand*): Unit = confs.foreach(adopt)

    protected def adopt(conf: Subcommand): Unit =
      conf match
        case leaf: Leaf => leaf.declareJson()
        case _          => ()
      addSubcommand(conf)

    /** A flag: present or not; `short`, a one-letter spelling (`-y`). */
    def flag(name: String, descr: String, short: Char = '\u0000'): ScallopOption[Boolean] =
      types.option(name, typeName[Boolean])
      if short == '\u0000' then opt[Boolean](name = name, descr = descr)(using flagConverter)
      else opt[Boolean](name = name, descr = descr, short = short, noshort = false)(using flagConverter)

    /** `--json`, as every command that offers it spells it. */
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def json(): () => Cli.JsonOutput =
      val f = flag("json", Output.JsonHelp)
      () => jsonOutput(f.getOrElse(false))

    def noWait(descr: String): () => Cli.Following =
      val f = flag("no-wait", descr)
      () => if f.getOrElse(false) then Cli.Following.Return else Cli.Following.Follow

    def dryRun(descr: String): () => Cli.Writes =
      val f = flag("dry-run", descr)
      () => if f.getOrElse(false) then Cli.Writes.DryRun else Cli.Writes.Apply

    def applyFlag(descr: String): () => Cli.Writes =
      val f = flag("apply", descr)
      () => if f.getOrElse(false) then Cli.Writes.Apply else Cli.Writes.DryRun

    /** A positional argument. */
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    def arg[T](name: String, required: Boolean = true)(using
        c: ValueConverter[T],
        n: Named[T]
    ): ScallopOption[T] =
      types.argument(name, typeName[T])
      trailArg[T](name = name, required = required)(using c)

    /** Positional arguments, any number (at least one when `required`). */
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    def args[T](name: String, required: Boolean = false)(using
        c: ValueConverter[T],
        n: Named[T]
    ): ScallopOption[List[T]] =
      types.argument(name, typeName[T])
      trailArg[List[T]](name = name, required = required)(using Operands(c))

    /** The shared [[Filter]] flags (`search`, `facets`, `histogram`, `geo-when`, `article list`, `events search`), in
      * their one order; the filter is read after the command line is verified.
      */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def filterFlags(): () => Filter =
      def many(p: Filter.Docs.Param) = values[String](p.rest.value, s"${p.doc} (repeatable)")
      def year(name: String, doc: String) = value[Year](name, doc)
      val tags = many(Filter.Docs.tags)
      val authors = many(Filter.Docs.authors)
      val places = many(Filter.Docs.places)
      val topics = many(Filter.Docs.topics)
      val status = many(Filter.Docs.status)
      val yearFrom = year("year-from", Filter.Docs.yearFrom.doc.value)
      val yearTo = year("year-to", Filter.Docs.yearTo.doc.value)
      val from = year(Filter.Docs.from.rest.value, Filter.Docs.from.doc.value)
      val to = year(Filter.Docs.to.rest.value, Filter.Docs.to.doc.value)
      val bbox = value[BBox.Text]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)")
      val sort = value[Sort.Wire](Filter.Docs.sort.rest.value, Filter.Docs.sort.doc.value)
      val limit = value[Filter.Limit](Filter.Docs.limit.rest.value, Filter.Docs.limit.doc.value)
      val offset = value[Filter.Offset](Filter.Docs.offset.rest.value, Filter.Docs.offset.doc.value)
      val title = value[Filter.Title](Filter.Docs.title.rest.value, Filter.Docs.title.doc.value)
      val goal = value[Filter.Goal](Filter.Docs.goal.rest.value, Filter.Docs.goal.doc.value)
      () =>
        val b = bbox.toOption.flatMap(BBox.parse)
        Filter.of(
          None,
          tags.getOrElse(Nil).map(Filter.Tags(_)),
          authors.getOrElse(Nil).map(Filter.Authors(_)),
          places.getOrElse(Nil).map(Filter.Places(_)),
          topics.getOrElse(Nil).map(Filter.Topics(_)),
          status.getOrElse(Nil).map(Filter.Status(_)),
          yearFrom.toOption,
          yearTo.toOption,
          from.toOption,
          to.toOption,
          b.map(_.minLon),
          b.map(_.minLat),
          b.map(_.maxLon),
          b.map(_.maxLat),
          sort.toOption,
          limit.toOption,
          offset.toOption,
          title.toOption,
          goal.toOption
        )

  /** One subcommand that is a whole command: it turns its verified options into the command. `parent` is the command
    * line before it (`histmersion article`).
    */
  abstract private[cli] class Leaf(parent: String, name: String, help: String) extends Subcommand(name) with Declares:
    guessOptionName = false
    noshort = true
    descr(help)
    helpFormatter = Layout(s"$parent $name")

    def command: Either[Problems, Cli.Command]

    private var output: Option[ScallopOption[Boolean]] = None

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    override def json(): () => Cli.JsonOutput =
      val f = flag("json", Output.JsonHelp)
      output = Some(f)
      () => jsonOutput(f.getOrElse(false))

    private[cli] def declareJson(): Unit =
      if output.isEmpty && types.of("json").isEmpty then output = Some(flag("json", Output.FailureJsonHelp))

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def jsonRequested: Cli.JsonOutput = jsonOutput(output.exists(_.getOrElse(false)))

  /** A subcommand that only holds others: `histmersion article`. */
  abstract private[cli] class Group(parent: String, name: String, help: String) extends Subcommand(name) with Declares:
    guessOptionName = false
    noshort = true
    descr(help)
    helpFormatter = Layout(s"$parent $name")

  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  final private class Root(args: Seq[String]) extends ScallopConf(args) with Declares:
    private var printed = Vector.empty[String]
    private var exited: Option[Int] = None
    private var failure: Option[Problem] = None

    guessOptionName = false
    noshort = true
    printedName = "histmersion"
    stdoutPrintln = line => printed :+= line
    stderrPrintln = line => printed :+= line
    exitHandler = code => exited = Some(code)
    errorMessageHandler = message => failure = Some(Problem.Unclassified(Legacy.Text(message)))
    helpFormatter = Layout("histmersion", Some("Histmersion — CLI over the Histmersion server (MCP twin)"))

    private val server =
      value[Client.ServerUrl](
        "server",
        "Histmersion server URL (default: config cli.serverUrl / $HISTMERSION_SERVER_URL)"
      )
    private val byPlugin =
      value[Creation.Plugin](
        "by-plugin",
        "An automated caller: the plugin it runs in, named on what it creates (else $HISTMERSION_AGENT_PLUGIN)"
      )
    private val byWorkflow =
      value[Creation.Workflow]("by-workflow", "An automated caller: its workflow (else $HISTMERSION_AGENT_WORKFLOW)")
    private val byAgent =
      value[Creation.Definition](
        "by-agent",
        "The agent's definition, with --by-provider and --by-model (else $HISTMERSION_AGENT_DEFINITION)"
      )
    private val byProvider =
      value[Creation.Provider]("by-provider", "The agent's AI provider (else $HISTMERSION_AGENT_PROVIDER)")
    private val byModel = value[Creation.Model]("by-model", "The agent's model (else $HISTMERSION_AGENT_MODEL)")
    entries.foreach(e => adopt(e.conf()))

    private def caller: AgentIdentity =
      AgentIdentity(byPlugin.toOption, byWorkflow.toOption, byAgent.toOption, byProvider.toOption, byModel.toOption)

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    private val askedJson = jsonOutput(args.contains("--json"))

    /** Scallop's own errors, typed. Help and version still go through the output and exit hooks. */
    override protected def onError(e: Throwable): Unit = e match
      case e: ScallopException => failure = Some(problem(e, confsOf(args).reverse.map(_.types) :+ types))
      case other               => super.onError(other)

    def outcome: Outcome =
      failure match
        case Some(problem)            => Outcome.Failed(problem, HelpText(helpOf(args)), askedJson)
        case None if exited.isDefined => Outcome.Help(HelpText(printed.mkString("\n")))
        case None                     =>
          subcommands.lastOption match
            case Some(leaf: Leaf) =>
              leaf.command.fold(
                problem => Outcome.Failed(problem, HelpText(helpOf(args)), leaf.jsonRequested),
                Outcome.Run(server.toOption, caller, _, leaf.jsonRequested)
              )
            case _ =>
              Outcome.Failed(Problem.Invalid.Missing.here(Problem.Field("command")), HelpText(helpOf(args)), askedJson)

    /** The subcommands `args` name, outermost first. */
    private def confsOf(args: Seq[String]): List[Declares] =
      args
        .foldLeft((List.empty[Declares], subconfigs)) { case ((path, here), word) =>
          here.collectFirst { case c: Declares if c.commandNameAndAliases.contains(word) => c } match
            case Some(c) => (path :+ c, c.children)
            case None    => (path, here)
        }
        ._1

    /** The help of the deepest subcommand `args` name. */
    private def helpOf(args: Seq[String]): String =
      val path = confsOf(args).map(_.commandNameAndAliases.head)
      builder.findSubbuilder(path.mkString("\u0000")).fold(builder.getFullHelpString())(_.getFullHelpString())

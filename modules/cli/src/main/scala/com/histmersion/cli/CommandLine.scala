package com.histmersion
package cli

import com.histmersion.api.{Api, BBox}
import com.histmersion.api.aliases.{LabelRename, LabelRenames}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.api.research.{Article, Articles, ReviewGoal}
import com.histmersion.util.problem.{Legacy, Problem}
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
import scala.annotation.tailrec

/** The command line as scallop parses it. Scallop's configuration is mutable — options are declared on an object and
  * read back after `verify()` — so it lives here and nowhere else: only this file declares `opt`, `trailArg` and
  * subcommands, and it hands the rest of the CLI a typed [[ArticleCommand]] (or the help, or a [[Problem]]). Every
  * argument is parsed once, here, into its newtype; scallop's exit and output go through its per-instance hooks, so
  * the parser never ends the process.
  *
  * The groups listed in [[Groups]] are parsed here; every other command is still decline's ([[Cli]]).
  */
object CommandLine:
  type HelpText = HelpText.T
  object HelpText extends Newtype[String, Pure]

  /** What the command line asks for. */
  enum Outcome derives CanEqual:
    case Run(server: Option[Client.ServerUrl], token: Option[Client.Token], command: ArticleCommand)
    case Help(text: HelpText)
    case Failed(problem: Problem, help: HelpText)

  /** The command groups this parser owns. */
  val Groups: Set[String] = Set("article")

  private val Globals = Set("--server", "--token")

  /** Whether the command line names a group this parser owns (after the global options). */
  def handles(args: Seq[String]): Boolean = firstWord(args.toList).exists(Groups)

  @tailrec private def firstWord(args: List[String]): Option[String] = args match
    case flag :: _ :: rest if Globals(flag)                           => firstWord(rest)
    case flag :: rest if Globals.exists(g => flag.startsWith(g + "=")) => firstWord(rest)
    case word :: _                                                     => Some(word)
    case Nil                                                           => None

  /** Parse `args`: the typed command, the help asked for, or what is wrong with them. */
  def parse(args: Seq[String]): Outcome =
    val root = Root(args)
    root.verify()
    root.outcome

  // ── converters: every newtype parses as its base type, once ──────────────────────────────────────

  /** A newtype's converter: its base type's, the constraint checked. */
  given newtype: [T] => (m: RefinedType.Mirror[T]) => (base: ValueConverter[m.BaseType]) => ValueConverter[T] =
    base.flatMap(v => m.ops.either(v).map(t => Some(t.asInstanceOf[T])))

  private given path: ValueConverter[Path] = singleArgConverter(Path.of(_))

  /** An option given any number of times, one value each time (decline's `options`), not scallop's greedy list. */
  private def repeated[T](one: ValueConverter[T]): ValueConverter[List[T]] = Repeated(one)

  private final class Repeated[T](one: ValueConverter[T]) extends ValueConverter[List[T]]:
    def parse(s: List[(String, List[String])]): Either[String, Option[List[T]]] =
      s.foldLeft[Either[String, List[T]]](Right(Nil))((acc, given1) =>
        acc.flatMap(done => one.parse(List(given1)).map(v => done ++ v.toList))
      ).map(vs => Option.when(vs.nonEmpty)(vs))
    val argType: ArgType.V = ArgType.SINGLE

  /** What help calls a value of `T` (decline's metavars, so the help reads as it did). */
  trait Metavar[T]:
    def name: String

  object Metavar:
    private def of[T](text: String): Metavar[T] = new Metavar[T]:
      def name: String = text
    given Metavar[String] = of("string")
    given Metavar[Int] = of("integer")
    given Metavar[Long] = of("integer")
    given Metavar[Path] = of("path")
    given newtype: [T] => (m: RefinedType.Mirror[T]) => (base: Metavar[m.BaseType]) => Metavar[T] = of(base.name)

  /** Help in decline's layout — usage, description, options and flags, subcommands — so the help text, its snapshot
    * and the generated CLI reference do not change with the parser.
    */
  private final class Layout(path: String) extends ScallopHelpFormatter:
    override def formatHelp(s: Scallop, subcommandPrefix: String): String =
      def isFlag(o: SimpleOption) = o.converter.asInstanceOf[AnyRef] eq flagConverter
      val options = s.opts.collect { case o: SimpleOption if o.name != "help" && o.name != "version" => o }
      val operands = s.opts.collect { case o: TrailingArgsOption => o }
      def usage(o: SimpleOption): String =
        val one = if isFlag(o) then s"--${o.name}" else s"--${o.name} <${o.argName}>"
        o.converter match
          case _: Repeated[?] => s"[$one]..."
          case _              => if o.required then one else s"[$one]"
      val usages =
        if s.subbuilders.nonEmpty then s.subbuilders.map((name, _) => s"    $path $name").mkString("Usage:\n", "\n", "")
        else
          (s"Usage: $path" :: options.map(usage) ++ operands.map(o => if o.required then s"<${o.name}>" else s"[<${o.name}>]"))
            .mkString(" ")
      val flags = ("    --help\n        Display this help text." :: options.map(o =>
        s"    ${if isFlag(o) then s"--${o.name}" else s"--${o.name} <${o.argName}>"}\n        ${o.descr}"
      )).mkString("Options and flags:\n", "\n", "")
      val subcommands =
        if s.subbuilders.isEmpty then Nil
        else List(s.subbuilders.map((name, sub) => s"    $name\n        ${sub.descr}").mkString("Subcommands:\n", "\n", ""))
      (usages :: s.descr :: flags :: subcommands).mkString("\n\n")

  /** A path the command reads later, refused here when the platform cannot name it. */
  private def pathOf(field: String, raw: Option[String]): Either[Problem, Option[Path]] =
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
  private final class Types:
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

  private def typeName[T](using n: Named[T]): Problem.TypeName = Problem.TypeName.of(Newtype.TypeName.of(n))

  /** Scallop's error, typed; `types` are those of the subcommand the command line names, innermost first. */
  private def problem(e: ScallopException, types: List[Types]): Problem = e match
    case RequiredOptionNotFound(name) => Problem.Invalid.Missing(Problem.Field(name))
    case UnknownOption(name)          =>
      Problem.Invalid.Unknown(
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
      Problem.Invalid.Unknown(typeName[Operand], Problem.Raw(extra.mkString(" ")), Vector.empty)
    case other => Problem.Unclassified(Legacy.Text(other.getMessage))

  // ── the configuration tree ────────────────────────────────────────────────────────────────────

  /** What every level declares options with: no guessed names (that reads the class by reflection), no short names. */
  private trait Declares extends ScallopConf:
    val types: Types = Types()

    protected def value[T](name: String, descr: String)(using
        c: ValueConverter[T],
        n: Named[T],
        m: Metavar[T]
    ): ScallopOption[T] =
      types.option(name, typeName[T])
      opt[T](name = name, descr = descr, argName = m.name)(using c)

    protected def values[T](name: String, descr: String)(using
        c: ValueConverter[T],
        n: Named[T],
        m: Metavar[T]
    ): ScallopOption[List[T]] =
      types.option(name, typeName[T])
      opt[List[T]](name = name, descr = descr, argName = m.name)(using repeated(c))

    /** The subcommands declared on this one. */
    def children: Seq[ScallopConfBase] = subconfigs

    protected def flag(name: String, descr: String): ScallopOption[Boolean] =
      types.option(name, typeName[Boolean])
      opt[Boolean](name = name, descr = descr)(using flagConverter)

    protected def arg[T](name: String, descr: String = "", required: Boolean = true)(using
        c: ValueConverter[T],
        n: Named[T]
    ): ScallopOption[T] =
      types.argument(name, typeName[T])
      trailArg[T](name = name, descr = descr, required = required)(using c)

  /** One subcommand that is a whole command: it turns its verified options into the command. */
  private abstract class Leaf(name: String, help: String) extends Subcommand(name) with Declares:
    guessOptionName = false
    noshort = true
    descr(help)
    helpFormatter = Layout(s"histmersion article $name")

    def command: Either[Problem, ArticleCommand]

  private final class Root(args: Seq[String]) extends ScallopConf(args) with Declares:
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
    helpFormatter = Layout("histmersion")

    private val server =
      value[Client.ServerUrl]("server", "Histmersion server URL (default: config cli.serverUrl / $HISTMERSION_SERVER_URL)")
    private val token = value[Client.Token]("token", "API key for authenticated servers (or $HISTMERSION_TOKEN)")

    addSubcommand(ArticleGroup())

    /** Scallop's own errors, typed. Help and version still go through the output and exit hooks. */
    override protected def onError(e: Throwable): Unit = e match
      case e: ScallopException => failure = Some(problem(e, confsOf(args).reverse.map(_.types) :+ types))
      case other               => super.onError(other)

    def outcome: Outcome =
      failure match
        case Some(problem) => Outcome.Failed(problem, HelpText(helpOf(args)))
        case None if exited.isDefined => Outcome.Help(HelpText(printed.mkString("\n")))
        case None                     =>
          subcommands.lastOption match
            case Some(leaf: Leaf) =>
              leaf.command.fold(
                problem => Outcome.Failed(problem, HelpText(helpOf(args))),
                Outcome.Run(server.toOption, token.toOption, _)
              )
            case _ => Outcome.Failed(Problem.Invalid.Missing(Problem.Field("command")), HelpText(helpOf(args)))

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

  // ── `histmersion article …` ───────────────────────────────────────────────────────────────────────

  private final class ArticleGroup() extends Subcommand("article") with Declares:
    guessOptionName = false
    noshort = true
    private val help =
      "The compiled wiki: list/get/put/patch/lint/check/coverage/review/rename/export/import/versions articles"
    descr(help)
    helpFormatter = Layout("histmersion article")
    List(
      ArticleList(),
      ArticleGet(),
      ArticlePut(),
      ArticlePatch(),
      ArticleLint(),
      ArticleCheck(),
      ArticleCoverage(),
      ArticleReview(),
      ArticleDelete(),
      ArticleExport(),
      ArticleImport(),
      ArticleVersions(),
      ArticleVersion(),
      ArticleRename()
    ).foreach(addSubcommand)

  private val JsonHelp = "Print the raw JSON response instead of Markdown"
  private val StrictHelp =
    "Refuse the write when the Markdown has a formatting error (default: write, then list them)"

  private final class ArticleList()
      extends Leaf("list", "List/search compiled articles (the filter flags select the sources they cite)"):
    private val query = arg[String]("query", required = false)
    private val kind = value[Api.ArticleQuery.Kind]("kind", s"Article kind (${Articles.Kinds.mkString("|")})")
    private def many[T: {ValueConverter, Named, Metavar}](p: Filter.Docs.Param) = values[T](p.rest.value, s"${p.doc} (repeatable)")
    private def year(p: Filter.Docs.Param) = value[Year](p.rest.value, p.doc.value)
    private val tags = many[Filter.Tags](Filter.Docs.tags)
    private val authors = many[Filter.Authors](Filter.Docs.authors)
    private val places = many[Filter.Places](Filter.Docs.places)
    private val topics = many[Filter.Topics](Filter.Docs.topics)
    private val status = many[Filter.Status](Filter.Docs.status)
    private val yearFrom = value[Year]("year-from", Filter.Docs.yearFrom.doc.value)
    private val yearTo = value[Year]("year-to", Filter.Docs.yearTo.doc.value)
    private val from = year(Filter.Docs.from)
    private val to = year(Filter.Docs.to)
    private val bbox = value[BBox.Text]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)")
    private val sort = value[Sort.Wire](Filter.Docs.sort.rest.value, Filter.Docs.sort.doc.value)
    private val limit = value[Filter.Limit](Filter.Docs.limit.rest.value, Filter.Docs.limit.doc.value)
    private val offset = value[Filter.Offset](Filter.Docs.offset.rest.value, Filter.Docs.offset.doc.value)
    private val title = value[Filter.Title](Filter.Docs.title.rest.value, Filter.Docs.title.doc.value)
    private val goal = value[Filter.Goal](Filter.Docs.goal.rest.value, Filter.Docs.goal.doc.value)
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      val b = bbox.toOption.flatMap(BBox.parse)
      val filter = Filter.of(
        None,
        tags.getOrElse(Nil),
        authors.getOrElse(Nil),
        places.getOrElse(Nil),
        topics.getOrElse(Nil),
        status.getOrElse(Nil),
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
      Right(
        ArticleCommand.Listing(
          query.toOption.map(_.trim).filter(_.nonEmpty).map(Api.ArticleQuery.Text(_)),
          kind.toOption,
          filter,
          Cli.JsonOutput(json.getOrElse(false))
        )
      )

  private final class ArticleGet()
      extends Leaf("get", "Show one article (parts: body,summary,coverage,cited,backlinks — default all)"):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val parts =
      value[String]("parts", "Comma-separated parts to return: body,summary,coverage,cited,backlinks (default: all)")
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      Right(ArticleCommand.Get(slug(), ArticleCommand.parts(parts.toOption), Cli.JsonOutput(json.getOrElse(false))))

  private final class ArticlePut()
      extends Leaf("put", "Send ONE article note file (frontmatter + body) as a full create/replace"):
    private val file = arg[Path]("file.md")
    private val kind = value[Article.Kind.Wire]("kind", "Kind when the frontmatter has no `type:`")
    private val strict = flag("strict", StrictHelp)

    def command: Either[Problem, ArticleCommand] =
      Right(ArticleCommand.Put(file(), kind.toOption, Cli.Strict(strict.getOrElse(false))))

  private final class ArticlePatch()
      extends Leaf("patch", "Extend or correct one article — only the parts you pass change"):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val json =
      value[ArticlePatches.PatchJson]("json", "A whole ArticlePatch as JSON (the flags below override its parts)")
    private val title = value[Article.Title]("title", "New title")
    private val lede = value[Article.SummaryText]("summary", "New summary (the one-paragraph lede)")
    private val kind = value[Article.Kind.Wire]("kind", s"New kind (${Articles.Kinds.mkString("|")})")
    private val bodyFile = value[ArticlePatches.TextFile]("body-file", "File whose Markdown REPLACES the body")
    private val appendFile = value[ArticlePatches.TextFile]("append-file", "File whose Markdown is APPENDED to the body")
    private val tagsAdd = value[ArticlePatches.CsvText]("tags-add", "Comma-separated tags to add")
    private val tagsRemove = value[ArticlePatches.CsvText]("tags-remove", "Comma-separated tags to remove")
    private val tagsReplace =
      value[ArticlePatches.CsvText]("tags-replace", "Comma-separated tags replacing the current set")
    private val regions = value[ArticlePatches.CsvText]("regions", "Comma-separated regions replacing the current list")
    private val period =
      value[ArticlePatches.FilterFlags.Period]("period", "start,end as signed years, BCE negative (e.g. -1300,-1100)")
    private val ifUpdatedAt = value[Article.UpdatedAt]("if-updated-at", "Precondition: the updatedAt you last read")
    private val strict = flag("strict", StrictHelp)
    private val filterQuery = value[ArticlePatches.FilterFlags.Query](
      "filter-query",
      "Coverage filter: the free-text query the article is compiled from (replaces the recorded filter)"
    )
    private val filterTopics =
      value[ArticlePatches.CsvText]("filter-topics", "Coverage filter: comma-separated topics the sources all have")
    private val filterTags =
      value[ArticlePatches.CsvText]("filter-tags", "Coverage filter: comma-separated tags the sources all have")
    private val filterStatus =
      value[ArticlePatches.CsvText]("filter-status", "Coverage filter: comma-separated statuses, any of")
    private val filterPlaces =
      value[ArticlePatches.CsvText]("filter-places", "Coverage filter: comma-separated places, any of")
    private val filterPeriod = value[ArticlePatches.FilterFlags.Period](
      "filter-period",
      "Coverage filter: start,end as signed years, BCE negative"
    )
    private val extraQuery = values[ArticlePatches.FilterFlags.ExtraQueries](
      "extra-query",
      "A further search the writer ran, reported in the Method note (repeatable)"
    )
    private val clearFilter =
      flag("clear-filter", "Remove the recorded filter (the Not used list and Method note stop being generated)")
    private val reason = values[ArticlePatches.ReasonText](
      "reason",
      "<citekey>=<why the article leaves it out> — sets one source's reason; <citekey>= removes it (repeatable)"
    )
    private val critiqueReason = values[String](
      "critique-reason",
      "<src>><dst>=<why the critique does not bear on the article> — sets the reason of one critique-checklist pair " +
        "(the critic first); <src>><dst>= removes it (repeatable)"
    )

    def command: Either[Problem, ArticleCommand] =
      Right(
        ArticleCommand.Patch(
          slug(),
          ArticleCommand.Edit(
            json.toOption,
            title.toOption,
            lede.toOption,
            kind.toOption,
            bodyFile.toOption,
            appendFile.toOption,
            tagsAdd.toOption,
            tagsRemove.toOption,
            tagsReplace.toOption,
            regions.toOption,
            period.toOption,
            ifUpdatedAt.toOption
          ),
          Cli.Strict(strict.getOrElse(false)),
          ArticlePatches.FilterFlags(
            filterQuery.toOption,
            filterTopics.toOption,
            filterTags.toOption,
            filterStatus.toOption,
            filterPlaces.toOption,
            filterPeriod.toOption,
            extraQuery.getOrElse(Nil),
            ArticlePatches.FilterFlags.Clear(clearFilter.getOrElse(false))
          ),
          reason.getOrElse(Nil),
          critiqueReason.getOrElse(Nil).map(text => Problem.Unclassified(Legacy.Text(text)))
        )
      )

  private final class ArticleLint()
      extends Leaf(
        "lint",
        "Formatting problems of one article's body and summary (--fix applies the wording-safe repairs)"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val fix = flag("fix", "Apply the repairs that never change wording, then report what is left")
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      Right(
        ArticleCommand.Lint(slug(), ArticleCommand.Fix(fix.getOrElse(false)), Cli.JsonOutput(json.getOrElse(false)))
      )

  private final class ArticleCheck()
      extends Leaf(
        "check",
        "Dry-run edits to an article (or a whole draft body) and list the problems the review checks would find — writes nothing"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    // decline's `<string>`, until the help may change: the paths are read in `command`
    private val editsFile = value[String](
      "edits-file",
      "A JSON list of edits: [{\"op\": \"replace|insertAfter|insertBefore|delete\", \"anchor\": \"…\", \"text\": \"…\"}]"
    )
    private val bodyFile = value[String]("body-file", "Instead of edits: a whole draft body (Markdown) to check")
    private val kind = value[Article.Kind.Wire](
      "kind",
      "Check the section set of this kind instead of the article's (index = none, for a fragment)"
    )
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      for
        edits <- pathOf("edits-file", editsFile.toOption)
        body <- pathOf("body-file", bodyFile.toOption)
        draft <- ArticleCommand.draft(edits, body)
      yield ArticleCommand.Check(slug(), draft, kind.toOption, Cli.JsonOutput(json.getOrElse(false)))

  private final class ArticleCoverage()
      extends Leaf(
        "coverage",
        "The article's filter against the corpus: sources cited, not used (with reasons), statuses, edges, drift, the critique checklist (disagreements checked), and the generated sections"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      Right(ArticleCommand.Coverage(slug(), Cli.JsonOutput(json.getOrElse(false))))

  private final class ArticleReview()
      extends Leaf(
        "review",
        "The article's review state (unreviewed, queued, in review, passed, majors open, stopped), the goals linking it, and every review finding by run and round with what became of it"
      ):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      Right(ArticleCommand.Review(slug(), Cli.JsonOutput(json.getOrElse(false))))

  private final class ArticleDelete()
      extends Leaf("delete", "Remove one article (its links go with it)"):
    private val slug = arg[ReviewGoal.Slug]("slug")

    def command: Either[Problem, ArticleCommand] = Right(ArticleCommand.Delete(slug()))

  private final class ArticleExport()
      extends Leaf("export", "Write each article to <dir>/<kind>/<slug>.md"):
    private val dir = arg[Path]("dir")

    def command: Either[Problem, ArticleCommand] = Right(ArticleCommand.Export(dir()))

  private final class ArticleImport()
      extends Leaf("import", "Import <dir>/<kind>/*.md (and any *.md in <dir> itself)"):
    private val dir = arg[Path]("dir")
    private val force = flag("force", "Overwrite articles newer than the files")

    def command: Either[Problem, ArticleCommand] =
      Right(ArticleCommand.Import(dir(), Api.Overwrite(force.getOrElse(false))))

  private final class ArticleVersions()
      extends Leaf("versions", "Show the edit history of one article"):
    private val slug = arg[ReviewGoal.Slug]("slug")

    def command: Either[Problem, ArticleCommand] = Right(ArticleCommand.Versions(slug()))

  private final class ArticleVersion()
      extends Leaf("version", "Show one historical version's full body"):
    private val slug = arg[ReviewGoal.Slug]("slug")
    private val id = arg[Article.VersionId]("id")

    def command: Either[Problem, ArticleCommand] = Right(ArticleCommand.Version(slug(), id()))

  private final class ArticleRename()
      extends Leaf("rename", LabelRenameCmd.help(Bundle.Kind.Article)):
    private val from = arg[LabelRenames.Label]("old")
    private val to = arg[LabelRenames.Label]("new")
    private val json = flag("json", JsonHelp)

    def command: Either[Problem, ArticleCommand] =
      LabelRenames
        .validate(Bundle.Kind.Article, from(), to())
        .left
        .map(_.message)
        .map(t =>
          ArticleCommand.Rename(
            ReviewGoal.Slug(from().value.trim),
            LabelRename.To(t.value),
            Cli.JsonOutput(json.getOrElse(false))
          )
        )

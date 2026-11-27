package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.library.Tree
import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown
import scala.annotation.tailrec
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.{Api, Markdown}
import com.histmersion.config.*
import java.nio.file.{Files, Path}
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.api.backup.{Backup, RestoreRequest}
import com.histmersion.api.checks.FormatMarkdown
import com.histmersion.api.instance.{AboutMarkdown, Version}
import com.histmersion.api.library.{PassagesMarkdown, SeedRequest, SeedResult}
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.api.library.{Citekey, Edge}
import com.histmersion.api.library.Histogram
import com.histmersion.api.library.Source
import com.histmersion.api.library.Facet
import sttp.client4.*
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.Paging
import com.histmersion.api.library.Title
import com.histmersion.api.library.Filter
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line of [[Cli]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion top`: declares its options, parses them into typed commands"
)
object TopArgs:
  object VersionEntry extends CommandLine.Entry:
    val name = "version"
    def help: String = "Print the CLI and server versions"
    def conf(): Subcommand = LeafVersion()

  object HealthEntry extends CommandLine.Entry:
    val name = "health"
    def help: String = "Server health (db, schema version)"
    def conf(): Subcommand = LeafHealth()

  object AboutEntry extends CommandLine.Entry:
    val name = "about"
    def help: String =
      "Server build (version, commit), health, job queue, last backup, store counts and storage on disk"
    def conf(): Subcommand = LeafAbout()

  object SeedEntry extends CommandLine.Entry:
    val name = "seed"
    def help: String =
      "Import a wiki/ or wiki/sources directory (articles beside it come too) — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir)"
    def conf(): Subcommand = LeafSeed()

  object ReindexEntry extends CommandLine.Entry:
    val name = "reindex"
    def help: String = "Rebuild the full-text chunk index for every source"
    def conf(): Subcommand = LeafReindex()

  object SearchEntry extends CommandLine.Entry:
    val name = "search"
    def help: String =
      "Search the corpus (websearch syntax: \"phrase\", -not, or): sources with the findings that matched"
    def conf(): Subcommand = LeafSearch()

  object FacetsEntry extends CommandLine.Entry:
    val name = "facets"
    def help: String = s"Vocabulary of a dimension (${Facet.Field.all.mkString("|")}) with distinct-source counts"
    def conf(): Subcommand = LeafFacets()

  object GetEntry extends CommandLine.Entry:
    val name = "get"
    def help: String = "Show one source as Markdown (section: summary|findings|notes|toc|body|all)"
    def conf(): Subcommand = LeafGet()

  object PassagesEntry extends CommandLine.Entry:
    val name = "passages"
    def help: String =
      "Find a word, phrase or regex inside one source's full text: the passages around each match, with offsets for get --offset"
    def conf(): Subcommand = LeafPassages()

  object GeoWhenEntry extends CommandLine.Entry:
    val name = "geo-when"
    def help: String = "Findings (the map feed) by place, deep time, bbox — the same filters as `search`"
    def conf(): Subcommand = LeafGeoWhen()

  object HistogramEntry extends CommandLine.Entry:
    val name = "histogram"
    def help: String =
      "When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search"
    def conf(): Subcommand = LeafHistogram()

  object NeighborsEntry extends CommandLine.Entry:
    val name = "neighbors"
    def help: String = "Typed references to/from a source (references, challenges, refutes, updates, superseded_by)"
    def conf(): Subcommand = LeafNeighbors()

  object GraphEntry extends CommandLine.Entry:
    val name = "graph"
    def help: String = "Source dependency graph: BFS traversal returning D3-ready nodes + links"
    def conf(): Subcommand = LeafGraph()

  object ContradictionsEntry extends CommandLine.Entry:
    val name = "contradictions"
    def help: String =
      "Disagreement edges (challenges, refutes, updates, superseded_by) and contested/superseded/debunked/retracted statuses"
    def conf(): Subcommand = LeafContradictions()

  object PutEntry extends CommandLine.Entry:
    val name = "put"
    def help: String = "Send ONE note file (frontmatter + body) as a full create/replace"
    def conf(): Subcommand = LeafPut()

  object PatchEntry extends CommandLine.Entry:
    val name = "patch"
    def help: String = "Selectively enrich one source — only the parts you pass are changed"
    def conf(): Subcommand = LeafPatch()

  object ExportEntry extends CommandLine.Entry:
    val name = "export"
    def help: String =
      "Write sources to <dir>/sources/, articles to <dir>/<kind>/, bookkeeping to <dir>/meta/ — the seed layout"
    def conf(): Subcommand = LeafExport()

  object BackupEntry extends CommandLine.Entry:
    val name = "backup"
    def help: String = "Take a pg_dump backup of the server's database (optionally copy it here)"
    def conf(): Subcommand = LeafBackup()

  object BackupsEntry extends CommandLine.Entry:
    val name = "backups"
    def help: String = "List the backups on the server, newest first"
    def conf(): Subcommand = LeafBackups()

  object RestoreEntry extends CommandLine.Entry:
    val name = "restore"
    def help: String = "Restore a server-side backup (pg_restore --clean); refuses a non-empty database without --force"
    def conf(): Subcommand = LeafRestore()

  object MaintenanceEntry extends CommandLine.Entry:
    val name = "maintenance"
    def help: String = "Database upkeep on the server: vacuum"
    def conf(): Subcommand = GroupMaintenance()

  final private class LeafVersion()
      extends Leaf("histmersion", "version", "Print the CLI and server versions")
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Version())

  final private class LeafHealth()
      extends Leaf("histmersion", "health", "Server health (db, schema version)")
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Health())

  final private class LeafAbout()
      extends Leaf(
        "histmersion",
        "about",
        "Server build (version, commit), health, job queue, last backup, store counts and storage on disk"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.About(json_()))

  final private class LeafSeed()
      extends Leaf(
        "histmersion",
        "seed",
        "Import a wiki/ or wiki/sources directory (articles beside it come too) — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir)"
      )
      with SharedFlags:
    private val dir_ = arg[String]("dir", required = false)
    private val force_ = flag("force", "Overwrite rows newer than the files")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Seed(dir_.getOrElse(""), force_.getOrElse(false)))

  final private class LeafReindex()
      extends Leaf("histmersion", "reindex", "Rebuild the full-text chunk index for every source")
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Reindex())

  final private class LeafSearch()
      extends Leaf(
        "histmersion",
        "search",
        "Search the corpus (websearch syntax: \"phrase\", -not, or): sources with the findings that matched"
      )
      with SharedFlags:
    private val q_ = arg[String]("query", required = false)
    private val group_ =
      value[String]("group", s"Group the results into a tree instead of listing them: ${Cli.GroupAxes.mkString(" | ")}")
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    private val depth_ = value[Int]("depth", s"Levels of nesting for --group (1-${Tree.MaxDepth})")
    private val asOf_ = value[Long](
      "as-of",
      "Signed year the gazetteer hierarchy is read at for --group region (default: the middle of the window)"
    )
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    private val samples_ =
      value[Int]("samples", s"Sample sources listed under each leaf of the tree (0-${Tree.MaxSamples})")
    private val unit_ = value[String]("unit", "What --group counts: sources (default) | findings")
    private val brief_ = flag("brief", "Compact listing: citekey, title, year, status, period, one why-line")
    private val f0_ = filterFlags()
    private val json_ = json()

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Search(
        q_.toOption,
        group_.toOption,
        depth_.toOption,
        asOf_.toOption,
        samples_.toOption,
        unit_.toOption,
        brief_.getOrElse(false),
        f0_(),
        json_()
      )
    )

  final private class LeafFacets()
      extends Leaf(
        "histmersion",
        "facets",
        s"Vocabulary of a dimension (${Facet.Field.all.mkString("|")}) with distinct-source counts"
      )
      with SharedFlags:
    private val field_ = arg[String]("field")
    private val prefix_ = value[String]("prefix", "Case-insensitive prefix match")
    private val q_ = arg[String]("query", required = false)
    private val f_ = filterFlags()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Facets(field_(), prefix_.toOption, q_.toOption, f_(), json_())
    )

  final private class LeafGet()
      extends Leaf("histmersion", "get", "Show one source as Markdown (section: summary|findings|notes|toc|body|all)")
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val full_ = flag("full", "Include full text")
    private val section_ = value[String](
      "section",
      s"Section to return: ${Source.Section.all.mkString("|")} (default: summary+findings+notes)"
    )
    private val heading_ = value[String]("heading", "Return body chunks under this heading (case-insensitive prefix)")
    private val offset_ = value[Int]("offset", "Start the body at this character offset (0-based)")
    private val chars_ = value[Int]("chars", "Return at most this many characters of the body")
    private val json_ = json()

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Get(
        ck_(),
        full_.getOrElse(false),
        section_.toOption,
        heading_.toOption,
        offset_.toOption,
        chars_.toOption,
        json_()
      )
    )

  final private class LeafPassages()
      extends Leaf(
        "histmersion",
        "passages",
        "Find a word, phrase or regex inside one source's full text: the passages around each match, with offsets for get --offset"
      )
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val q_ = arg[String]("query")
    private val regex_ =
      flag("regex", "Treat the query as a Java regular expression (default: literal, case- and accent-insensitive)")
    private val limit_ = value[Int]("limit", "Show at most this many passages (default 10, max 50)")
    private val context_ = value[Int]("context", "Characters of context around each match (default 600, max 3000)")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Passages(ck_(), q_(), regex_.getOrElse(false), limit_.toOption, context_.toOption)
    )

  final private class LeafGeoWhen()
      extends Leaf(
        "histmersion",
        "geo-when",
        "Findings (the map feed) by place, deep time, bbox — the same filters as `search`"
      )
      with SharedFlags:
    private val region_ = value[String]("region", "Region-name substring (alias of --place)")
    private val f_ = filterFlags()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.GeoWhen(region_.toOption, f_(), json_()))

  final private class LeafHistogram()
      extends Leaf(
        "histmersion",
        "histogram",
        "When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search"
      )
      with SharedFlags:
    private val q_ = arg[String]("query", required = false)
    private val region_ = value[String]("region", "Region-name substring (alias of --place)")
    private val unit_ = value[String]("unit", "sources (default) | findings")
    private val f0_ = filterFlags()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Histogram(q_.toOption, region_.toOption, unit_.toOption, f0_(), json_())
    )

  final private class LeafNeighbors()
      extends Leaf(
        "histmersion",
        "neighbors",
        "Typed references to/from a source (references, challenges, refutes, updates, superseded_by)"
      )
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val kinds_ = values[String]("kind", "Filter by edge kind (repeatable)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Neighbors(ck_(), kinds_.getOrElse(Nil), json_()))

  final private class LeafGraph()
      extends Leaf("histmersion", "graph", "Source dependency graph: BFS traversal returning D3-ready nodes + links")
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val depth_ = value[Int]("depth", "BFS depth 0–4 (default 2)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Graph(ck_(), depth_.toOption, json_()))

  final private class LeafContradictions()
      extends Leaf(
        "histmersion",
        "contradictions",
        "Disagreement edges (challenges, refutes, updates, superseded_by) and contested/superseded/debunked/retracted statuses"
      )
      with SharedFlags:
    private val ck_ = arg[String]("citekey", required = false)
    private val since_ = value[Long]("since", "Only show contradictions from citing papers published from this year on")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Contradictions(ck_.toOption, since_.toOption, json_())
    )

  final private class LeafPut()
      extends Leaf("histmersion", "put", "Send ONE note file (frontmatter + body) as a full create/replace")
      with SharedFlags:
    private val file_ = arg[String]("file.md")

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Put(file_()))

  final private class LeafPatch()
      extends Leaf("histmersion", "patch", "Selectively enrich one source — only the parts you pass are changed")
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val json_ = value[String]("json", "A whole SourcePatch as JSON (the flags below override its parts)")
    private val tAdd_ = value[String]("tags-add", "Comma-separated tags to add")
    private val tRem_ = value[String]("tags-remove", "Comma-separated tags to remove")
    private val tRep_ = value[String]("tags-replace", "Comma-separated tags replacing the current set")
    private val tops_ = value[String]("topics", "Comma-separated topics replacing the current list")
    private val regs_ = value[String]("regions", "Comma-separated regions replacing the current list")
    private val summary_ = value[String]("summary", "New summary")
    private given ValueConverter[Source.Status] = reading(v =>
      val wanted = Source.Status.Wire(v.trim.toLowerCase)
      Source.Status.values
        .find(_.wire == wanted)
        .toRight(s"unknown status '$v' (one of: ${Source.Status.values.map(_.wire).mkString(", ")})")
    )
    private given Metavar[Source.Status] = Metavar.of("string")
    private val status_ = value[Source.Status]("status", Source.Status.values.map(_.wire).mkString("|"))
    private val title_ = value[String]("title", "New title")
    private val notesFile_ = value[String]("notes-file", "File whose Markdown becomes this source's notes article")
    private val period_ = value[String]("period", "start,end as signed years, BCE negative (e.g. -3300,-2600)")
    private val bodyFile_ =
      value[String]("body-file", "File whose text REPLACES the converted body (e.g. a chapter cut out of a volume)")
    private val addF_ = values[String]("add-finding", "A FindingInput as JSON (repeatable)")
    private val remF_ = values[String]("remove-finding", "Finding id to drop, e.g. 'haak2015_massive#2' (repeatable)")
    private val ifUpd_ =
      value[String]("if-updated-at", "Precondition: the updatedAt you last read (fails when the row moved on)")
    private val addE_ = values[String](
      "add-edge",
      s"A typed edge FROM this source, <kind>:<citekey>, e.g. challenges:lord1968_oralpoet (repeatable; kinds: ${Edge.Kind.allWire.mkString(", ")})"
    )
    private val remE_ = values[String]("remove-edge", "An edge FROM this source to drop, <kind>:<citekey> (repeatable)")
    private val strict_ = flag("strict", "Refuse the patch when its summary or notes have a formatting error")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Patch(
        ck_(),
        json_.toOption,
        tAdd_.toOption,
        tRem_.toOption,
        tRep_.toOption,
        tops_.toOption,
        regs_.toOption,
        summary_.toOption,
        status_.toOption,
        title_.toOption,
        notesFile_.toOption,
        period_.toOption,
        bodyFile_.toOption,
        addF_.getOrElse(Nil),
        remF_.getOrElse(Nil),
        ifUpd_.toOption,
        addE_.getOrElse(Nil),
        remE_.getOrElse(Nil),
        strict_.getOrElse(false)
      )
    )

  final private class LeafExport()
      extends Leaf(
        "histmersion",
        "export",
        "Write sources to <dir>/sources/, articles to <dir>/<kind>/, bookkeeping to <dir>/meta/ — the seed layout"
      )
      with SharedFlags:
    private val dir_ = arg[String]("dir")
    private val only_ = args[String]("citekey")
    private val srcDir_ = value[String]("sources-dir", "Where source notes go (default: <dir>/sources)")
    private val artDir_ = value[String]("articles-dir", "Where article notes go (default: <dir>)")
    private val metaDir_ =
      value[String]("meta-dir", "Where ACQUIRE.md/TASKS.md/LOG.md/GOALS.md go (default: <dir>/meta)")
    private val noMeta_ = flag("no-meta", "Do not write the bookkeeping queues and the research goals")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Export(
        dir_(),
        only_.getOrElse(Nil),
        srcDir_.toOption,
        artDir_.toOption,
        metaDir_.toOption,
        noMeta_.getOrElse(false)
      )
    )

  final private class LeafBackup()
      extends Leaf("histmersion", "backup", "Take a pg_dump backup of the server's database (optionally copy it here)")
      with SharedFlags:
    private val into_ = value[String]("download", "Also stream the finished dump into this directory")

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Backup(into_.toOption))

  final private class LeafBackups()
      extends Leaf("histmersion", "backups", "List the backups on the server, newest first")
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Cli.TopCommand.Backups(json_()))

  final private class LeafRestore()
      extends Leaf(
        "histmersion",
        "restore",
        "Restore a server-side backup (pg_restore --clean); refuses a non-empty database without --force"
      )
      with SharedFlags:
    private val file_ = arg[String]("file")
    private val force_ = flag("force", "Overwrite a database that still holds sources")
    private val yes_ = flag("yes", "Do not ask for confirmation")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      Cli.TopCommand.Restore(file_(), force_.getOrElse(false), yes_.getOrElse(false))
    )

  final private class GroupMaintenance()
      extends Group("histmersion", "maintenance", "Database upkeep on the server: vacuum"):
    holds(LeafVacuum())

  final private class LeafVacuum()
      extends Leaf(
        "histmersion maintenance",
        "vacuum",
        "VACUUM the tables a re-seed rewrites (or --table …) as a server job, outside any transaction; --analyze also refreshes planner statistics"
      ):
    private val analyze_ = flag("analyze", "VACUUM (ANALYZE): also refresh the planner's statistics")
    private val tables_ =
      values[String]("table", "A table to vacuum (repeatable; default: config maintenance.vacuum-tables)")
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] =
      Right(Cli.TopCommand.Vacuum(analyze = analyze_.getOrElse(false), tables_.getOrElse(Nil), following_()))

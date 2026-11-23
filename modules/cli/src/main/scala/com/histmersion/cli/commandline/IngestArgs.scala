package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.Api
import com.histmersion.config.*
import java.nio.file.{Files, Path}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.{AuthorName, Citekey, Doi, Title}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.SpatialHierarchyResult.Job
import com.histmersion.util.console.Console
import com.histmersion.api.Paging
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[IngestCmd]]. */
object IngestArgs:
  object ReocrEntry extends CommandLine.Entry:
    val name = "reocr"
    def help: String = "Convert a source's stored original again (e.g. under the slow, accurate OCR path)"
    def conf(): Subcommand = LeafReocr()

  object AssetsEntry extends CommandLine.Entry:
    val name = "assets"
    def help: String = "The figures stored with a source, and the URLs they are served at"
    def conf(): Subcommand = LeafAssets()

  object JobsEntry extends CommandLine.Entry:
    val name = "jobs"
    def help: String = "Recent background jobs (OCR, imports, agent runs, …), filtered on the server, " +
      "or one job by id; `jobs pause|resume|cancel <id>` controls one"
    def conf(): Subcommand = GroupJobs()

  final private class LeafReocr()
      extends Leaf(
        "histmersion",
        "reocr",
        "Convert a source's stored original again (e.g. under the slow, accurate OCR path)"
      )
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val quality_ = flag("quality", "Slow, accurate OCR (docling)")
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(
      IngestCmd.Command.Reocr(ck_(), quality = quality_.getOrElse(false), following_())
    )

  final private class LeafAssets()
      extends Leaf("histmersion", "assets", "The figures stored with a source, and the URLs they are served at")
      with SharedFlags:
    private val ck_ = arg[String]("citekey")

    def command: Either[Problem, Cli.Command] = Right(IngestCmd.Command.Assets(ck_()))

  /** `jobs [<id>] [filters]` lists (or shows one); `jobs pause|resume|cancel <id>` control one. */
  final private class GroupJobs() extends Leaf("histmersion", "jobs", JobsEntry.help) with SharedFlags:
    private val id_ = arg[String]("id", required = false)
    private val filter_ = jobFilter()
    private val limit_ = value[Int]("limit", "Max rows")
    private val before_ = beforeFlag("job")
    private val json_ = json()
    holds(
      LeafControl("pause", IngestCmd.ControlHelp.pause),
      LeafControl("resume", IngestCmd.ControlHelp.resume),
      LeafControl("cancel", IngestCmd.ControlHelp.cancel)
    )

    def command: Either[Problem, Cli.Command] =
      Right(IngestCmd.Command.Jobs(id_.toOption, filter_(), limit_.toOption, before_(), json_()))

  final private class LeafControl(action: String, help: String)
      extends Leaf("histmersion jobs", action, help)
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] =
      Right(IngestCmd.Command.Control(Api.JobControl.Action(action), id_(), json_()))

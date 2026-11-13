package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.{Api, Dataset}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.instance.CorpusCounts.Periods as PeriodsApi
import com.histmersion.config.*
import java.nio.file.{Files, Path}
import com.histmersion.api.catalogue.PeriodsMarkdown
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[PeriodsCmd]]. */
object PeriodsArgs:
  object PeriodsEntry extends CommandLine.Entry:
    val name = "periods"
    def help: String = "Named periods (the timeline presets): search, show, seed and fetch from PeriodO"
    def conf(): Subcommand = GroupPeriods()

  object InitEntry extends CommandLine.Entry:
    val name = "init"
    def help: String = "Import the bundled gazetteer and period presets (a server job); --fetch adds the network sources"
    def conf(): Subcommand = LeafInit()

  final private class LeafSearch() extends Leaf("histmersion periods", "search", "Find named periods by label, region or time span") with SharedFlags:
    private val q_ = arg[String]("query", required = false)
    private val region_ = value[String]("region", "Exact region (case-insensitive), e.g. Crete")
    private val from_ = value[Long]("from", "Signed year the period must reach (BCE negative)")
    private val to_ = value[Long]("to", "Signed year the period must start by")

    def command: Either[Problem, Cli.Command] = Right(PeriodsCmd.Command.Search(q_.toOption, region_.toOption, from_.toOption, to_.toOption))

  final private class LeafShow() extends Leaf("histmersion periods", "show", "One period: its span, authority, what it is part of and its divisions") with SharedFlags:
    private val id_ = arg[String]("id")

    def command: Either[Problem, Cli.Command] = Right(PeriodsCmd.Command.Show(id_()))

  final private class LeafSeed() extends Leaf("histmersion periods", "seed", "Import the period presets bundled with the server (a curated PeriodO subset); idempotent") with SharedFlags:
    private val file_ = value[String]("file", "Send this PeriodO dataset instead (e.g. db/periods/periodo-seed.json)")
    private val replace_ = flag("replace", "Also delete the seed periods the bundled seed no longer has (fetched ones too; your own are kept)")

    def command: Either[Problem, Cli.Command] = Right(PeriodsCmd.Command.Seed(file_.toOption, replace_.getOrElse(false)))

  final private class LeafImport() extends Leaf("histmersion periods", "import", "Upsert a PeriodO dataset of your own (a dump or a subset); its periods are this collection's and travel in a bundle") with SharedFlags:
    private val file_ = arg[String]("file.json")
    private val cols_ = values[String]("collection", "Keep only this PeriodO authority id (repeatable)")
    private val dataset_ = datasetFlag()

    def command: Either[Problem, Cli.Command] = Right(PeriodsCmd.Command.Import(file_(), cols_.getOrElse(Nil), dataset_()))

  final private class LeafFetch() extends Leaf("histmersion periods", "fetch", "Download PeriodO and import collections, each under one node; re-runnable and idempotent") with SharedFlags:
    private val source_ = arg[String]("source")
    private val cols_ = values[String]("collection", "A PeriodO authority id, e.g. p0ds9qj (repeatable; default: config periods.periodo-collections)")

    def command: Either[Problem, Cli.Command] = Right(PeriodsCmd.Command.Fetch(source_(), cols_.getOrElse(Nil)))

  final private class GroupPeriods() extends Group("histmersion", "periods", "Named periods (the timeline presets): search, show, seed and fetch from PeriodO"):
    holds(LeafSearch(), LeafShow(), LeafSeed(), LeafImport(), LeafFetch())

  final private class LeafInit() extends Leaf("histmersion", "init", "Import the bundled gazetteer and period presets (a server job); --fetch adds the network sources") with SharedFlags:
    private val fetch_ = flag("fetch", "Also download the network gazetteers and PeriodO (config init.fetch-sources)")
    private val noWait_ = flag("no-wait", "Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(PeriodsCmd.Command.Init(fetch_.getOrElse(false), noWait_.getOrElse(false)))

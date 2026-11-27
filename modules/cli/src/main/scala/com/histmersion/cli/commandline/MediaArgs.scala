package com.histmersion
package cli
package commandline

import com.histmersion.api.media.MediaMarkdown
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.media.{
  AssetKind,
  AssetPatch,
  MediaCoversReport,
  MediaCoversRequest,
  MediaImportRequest,
  MediaItem,
  MediaProvider
}
import com.histmersion.api.catalogue.{Event, Latitude, Longitude, Place, Year}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.{Citekey, RepairAcquire}
import com.histmersion.util.console.Console
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.Paging
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line of [[MediaCmd]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion media`: declares its options, parses them into typed commands"
)
object MediaArgs:
  object MediaEntry extends CommandLine.Entry:
    val name = "media"
    def help: String =
      "Pictures: import from providers, covers and illustrations of sources, list, show, fill-licences; a figure's detail, patch"
    def conf(): Subcommand = GroupMedia()

  final private class LeafImport()
      extends Leaf(
        "histmersion media",
        "import",
        "Bring in pictures from an outside provider (legally reusable only): wikidata (the default), depicts, met, " +
          "cleveland, aic, loc, pleiades, opencontext, smithsonian and europeana (off by default: a key of your own), or a " +
          "plugin's collection — `histmersion media providers` " +
          "lists them with their terms. --dry-run asks about one batch and prints the counts per licence (and a " +
          "collection's matches, with their reasons); otherwise a server JOB asks about every place (or event) not " +
          "asked yet, batch by batch, and resumes where it stopped"
      )
      with SharedFlags:
    private val name_ = arg[String]("provider")
    private val writes_ = dryRun(
      "Read one batch, store nothing; print the counts per licence (and the matches, with their reasons)"
    )
    private val limit_ =
      value[Int]("limit", "Targets to ask about (a dry run: default media.default-limit; a job: all not asked yet)")
    private val place_ = value[String]("place", "Only this place")
    private val event_ =
      value[String]("event", "Only this event (its --wikidata item; providers that ask about events)")
    private val refresh_ =
      flag("refresh", "Ask again about ones already asked, and re-read the licences of stored pictures")
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(
      MediaCmd.Command.Import(
        name_(),
        writes_(),
        limit_.toOption,
        place_.toOption,
        event_.toOption,
        refresh = refresh_.getOrElse(false),
        following_()
      )
    )

  final private class LeafProviders()
      extends Leaf(
        "histmersion media",
        "providers",
        "The providers `media import` can run on this server: the built-in ones and any a plugin registered, " +
          "with what each asks about, the plugin that provides it and the licences it may store"
      )
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(MediaCmd.Command.Providers())

  final private class LeafCovers()
      extends Leaf(
        "histmersion media",
        "covers",
        "Pictures for the sources' hover cards: a book's cover from the local Calibre library (read-only), else " +
          "Open Library's by ISBN/OCLC; a paper's illustration chosen among its own figures. --dry-run says what " +
          "would be linked; otherwise the server part runs as a job"
      )
      with SharedFlags:
    private val writes_ = dryRun("Say what would be linked, store nothing")
    private val limit_ = value[Int]("limit", "Sources to look at per step")
    private val ck_ = value[String]("citekey", "Only this source")
    private val refresh_ = flag("refresh", "Redo the sources that already have their cover / illustration")
    private val noCalibre_ = flag("no-calibre", "Skip the Calibre library")
    private val noOl_ = flag("no-open-library", "Do not ask Open Library")
    private val noIll_ = flag("no-illustrations", "Covers only")
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(
      MediaCmd.Command.Covers(
        writes_(),
        limit_.toOption,
        ck_.toOption,
        refresh = refresh_.getOrElse(false),
        noCalibre = noCalibre_.getOrElse(false),
        noOl = noOl_.getOrElse(false),
        noIll = noIll_.getOrElse(false),
        following_()
      )
    )

  final private class LeafListCmd()
      extends Leaf(
        "histmersion media",
        "list",
        "Stored pictures — source figures and providers' — with licence and credit"
      )
      with SharedFlags:
    private val provider_ = value[String]("provider", s"${MediaProvider.all.mkString("|")}")
    private val licence_ = value[String]("licence", "An SPDX id (CC-BY-SA-4.0), public-domain, or none")
    private val place_ = value[String]("place", "Linked to this place")
    private val event_ = value[String]("event", "Linked to this event")
    private val q_ = value[String]("q", "Text in the name, file or credit")
    private val limit_ = value[Int]("limit", "How many to list")
    private val offset_ = value[Int]("offset", "Skip this many (the list's footer names the next offset)")
    private val facets_ = flag("facets", "Only the counts per provider and licence")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      MediaCmd.Command.ListCmd(
        provider_.toOption,
        licence_.toOption,
        place_.toOption,
        event_.toOption,
        q_.toOption,
        limit_.toOption,
        offset_.toOption,
        facets_.getOrElse(false)
      )
    )

  final private class LeafShow()
      extends Leaf("histmersion media", "show", "One picture: origin, licence, credit, and what it shows")
      with SharedFlags:
    private val uid_ = arg[String]("uid")

    def command: Either[Problem, Cli.Command] = Right(MediaCmd.Command.Show(uid_()))

  final private class LeafFillLicences()
      extends Leaf(
        "histmersion media",
        "fill-licences",
        "Give a source's figures the licence the source records (only figures with none); counts unless --apply"
      )
      with SharedFlags:
    private val writes_ = applyFlag("Write the licences")

    def command: Either[Problem, Cli.Command] = Right(MediaCmd.Command.FillLicences(writes_()))

  final private class LeafDetail()
      extends Leaf("histmersion media", "detail", "Show asset metadata including spatial/temporal")
      with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val name_ = arg[String]("name")

    def command: Either[Problem, Cli.Command] = Right(MediaCmd.Command.Detail(ck_(), name_()))

  final private class LeafPatch() extends Leaf("histmersion media", "patch", "Update asset metadata") with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val name_ = arg[String]("name")
    private val kind_ = value[String]("kind", s"Asset kind: ${AssetKind.all.mkString("|")}")
    private val caption_ = value[String]("caption", "Caption text")
    private val license_ = value[String]("license", "License identifier")
    private val lat_ = value[Double]("lat", "Latitude")
    private val lon_ = value[Double]("lon", "Longitude")
    private val ps_ = value[Long]("period-start", "Period start (signed year)")
    private val pe_ = value[Long]("period-end", "Period end (signed year)")

    def command: Either[Problem, Cli.Command] = Right(
      MediaCmd.Command.Patch(
        ck_(),
        name_(),
        kind_.toOption,
        caption_.toOption,
        license_.toOption,
        lat_.toOption,
        lon_.toOption,
        ps_.toOption,
        pe_.toOption
      )
    )

  final private class GroupMedia()
      extends Group(
        "histmersion",
        "media",
        "Pictures: import from providers, covers and illustrations of sources, list, show, fill-licences; a figure's detail, patch"
      ):
    holds(
      LeafImport(),
      LeafProviders(),
      LeafCovers(),
      LeafListCmd(),
      LeafShow(),
      LeafFillLicences(),
      LeafDetail(),
      LeafPatch()
    )

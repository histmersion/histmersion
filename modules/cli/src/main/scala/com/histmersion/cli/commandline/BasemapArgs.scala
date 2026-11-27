package com.histmersion
package cli
package commandline

import com.histmersion.config.*
import com.histmersion.api.basemap.BasemapMarkdown
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.basemap.Basemap
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line of [[BasemapCmd]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion basemap`: declares its options, parses them into typed commands"
)
object BasemapArgs:
  object BasemapEntry extends CommandLine.Entry:
    val name = "basemap"
    def help: String = "The web UI's local base map (street maps and terrain): status, styles, fetch, rm"
    def conf(): Subcommand = GroupBasemap()

  final private class LeafStatus()
      extends Leaf(
        "histmersion basemap",
        "status",
        "What is downloaded, where, the free space there, and which styles lack data"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(BasemapCmd.Command.Status(json_()))

  final private class LeafFetch()
      extends Leaf(
        "histmersion basemap",
        "fetch",
        "Download the local base map (coast, water, rivers, land cover; relief; physical names; sea floor; with --keep-all roads, places and borders) as a server job — resumable; " +
          "refused when the disk would be left with less than basemap.min-free-gb"
      )
      with SharedFlags:
    private val layer_ = value[String]("layer", "vector, terrain, labels, bathymetry or all (default all)")
    private val z_ = value[Int](
      "max-zoom",
      "Deepest zoom of the vector layer (default basemap.vector.max-zoom; with --layer terrain alone, of the relief)"
    )
    private val tz_ =
      value[Int]("terrain-max-zoom", "Deepest zoom of the relief, 512-pixel tiles (default basemap.terrain.max-zoom)")
    private val bbox_ = value[String](
      "bbox",
      "Region as minLon,minLat,maxLon,maxLat, or world (default basemap.bbox); --dry-run says the size zoom by zoom"
    )
    private val focus_ = value[String](
      "focus",
      "Keep the relief of a region deeper than the extract, to basemap.terrain.on-demand.max-zoom: " + "a name of basemap.terrain.focus-regions (aegean, anatolia, levant, mesopotamia, nile-delta, argolid) or a new one with --bbox"
    )
    private val writes_ = dryRun("Read only the directories and report what the download would take")
    private val keepAll_ = flag(
      "keep-all",
      "Store the vector layer unfiltered — roads, places and borders too, for the Readable map (basemap.vector.drop not applied)"
    )
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(
      BasemapCmd.Command.Fetch(
        layer_.getOrElse("all"),
        z_.toOption,
        tz_.toOption,
        bbox_.toOption,
        focus_.toOption,
        writes_(),
        keepAll = keepAll_.getOrElse(false),
        following_()
      )
    )

  final private class LeafRm()
      extends Leaf(
        "histmersion basemap",
        "rm",
        "Remove the downloaded base-map files (and an unfinished download), focus regions, the on-demand relief cache"
      )
      with SharedFlags:
    private val layer_ = value[String](
      "layer",
      "vector, terrain, labels, bathymetry or all — all includes the focus regions and the relief cache " + "(default all; none when only --focus or --cache is given)"
    )
    private val focus_ = value[String]("focus", "A focus region's relief, by name, or all")
    private val cache_ = flag(
      "cache",
      "The caches of tiles read on demand (the deeper relief, the map and the sea floor outside the region)"
    )

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      BasemapCmd.Command.Rm(layer_.toOption, focus_.toOption, cache_.getOrElse(false))
    )

  final private class GroupBasemap()
      extends Group(
        "histmersion",
        "basemap",
        "The web UI's local base map (street maps and terrain): status, styles, fetch, rm"
      ):
    holds(LeafStatus(), LeafFetch(), LeafRm())

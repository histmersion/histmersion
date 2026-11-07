package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.basemap.BasemapMarkdown
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.basemap.Basemap
import com.histmersion.util.console.Console

/** `histmersion basemap status|fetch|rm` — the local base map's extracts on the SERVER's machine
  * (docs/user-guide/how-to/open-data.md): what is there (and which of the two styles it draws whole), downloading them
  * (a job, followed here), and removing them.
  */
object BasemapCmd:

  private def statusCmd (using console: Console) =
    Opts.subcommand("status", "What is downloaded, where, the free space there, and which styles lack data") {
      Output.jsonFlag.map { json => (c: Client) =>
        c.basemapStatus().map(s => Output.emit(json, s)(BasemapMarkdown.status))
      }
    }

  private def fetchCmd (using console: Console) = Opts.subcommand(
    "fetch",
    "Download the local base map (coast, water, rivers, land cover; relief; physical names; sea floor; with --keep-all roads, places and borders) as a server job — resumable; " +
      "refused when the disk would be left with less than basemap.min-free-gb"
  ) {
    (
      Opts.option[String]("layer", "vector, terrain, labels, bathymetry or all (default all)").withDefault("all"),
      Opts
        .option[Int](
          "max-zoom",
          "Deepest zoom of the vector layer (default basemap.vector.max-zoom; with --layer terrain alone, of the relief)"
        )
        .orNone,
      Opts
        .option[Int](
          "terrain-max-zoom",
          "Deepest zoom of the relief, 512-pixel tiles (default basemap.terrain.max-zoom)"
        )
        .orNone,
      Opts
        .option[String](
          "bbox",
          "Region as minLon,minLat,maxLon,maxLat, or world (default basemap.bbox); --dry-run says the size zoom by zoom"
        )
        .orNone,
      Opts
        .option[String](
          "focus",
          "Keep the relief of a region deeper than the extract, to basemap.terrain.on-demand.max-zoom: " +
            "a name of basemap.terrain.focus-regions (aegean, anatolia, levant, mesopotamia, nile-delta, argolid) or a new one with --bbox"
        )
        .orNone,
      Opts.flag("dry-run", "Read only the directories and report what the download would take").orFalse,
      Opts
        .flag(
          "keep-all",
          "Store the vector layer unfiltered — roads, places and borders too, for the Readable map (basemap.vector.drop not applied)"
        )
        .orFalse,
      Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse
    )
      .mapN { (layer, z, tz, bbox, focus, dry, keepAll, noWait) => (c: Client) =>
        c.basemapFetch(
          Basemap.FetchQuery(
            Basemap.FetchQuery.Layer(if focus.isDefined then "terrain" else layer),
            z.map(Basemap.FetchQuery.MaxZoom(_)),
            tz.map(Basemap.FetchQuery.TerrainMaxZoom(_)),
            bbox.map(Basemap.FetchQuery.Bbox(_)),
            Basemap.FetchQuery.DryRun(dry),
            Basemap.FetchQuery.KeepAll(keepAll),
            focus.map(Basemap.FetchQuery.Focus(_))
          )
        ).flatMap { j =>
          Cli.out.println(
            s"base-map job `${j.id}` started: ${j.detail.flatMap(JobDetail.Purpose.of).getOrElse("")} (`histmersion jobs` lists it)"
          )
          if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
        }
      }
  }

  private def rmCmd (using console: Console) = Opts.subcommand(
    "rm",
    "Remove the downloaded base-map files (and an unfinished download), focus regions, the on-demand relief cache"
  ) {
    (
      Opts
        .option[String](
          "layer",
          "vector, terrain, labels, bathymetry or all — all includes the focus regions and the relief cache " +
            "(default all; none when only --focus or --cache is given)"
        )
        .orNone,
      Opts.option[String]("focus", "A focus region's relief, by name, or all").orNone,
      Opts
        .flag(
          "cache",
          "The caches of tiles read on demand (the deeper relief, the map and the sea floor outside the region)"
        )
        .orFalse
    ).mapN { (layer, focus, cache) => (c: Client) =>
      val l = layer.getOrElse(if focus.isDefined || cache then "none" else "all")
      c.basemapRemove(Basemap.RemoveQuery(
        Basemap.RemoveQuery.Layer(l),
        focus.map(Basemap.RemoveQuery.Focus(_)),
        Basemap.RemoveQuery.Cache(cache)
      )).map(r => Cli.out.println(BasemapMarkdown.removed(r)))
    }
  }

  def command(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("basemap", "The web UI's local base map (street maps and terrain): status, styles, fetch, rm") {
      statusCmd orElse fetchCmd orElse rmCmd
    }

package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

/** `histmersion basemap status|fetch|rm` — the terrain base map's extracts on the SERVER's machine
  * (docs/user-guide/how-to/open-data.md): what is there, downloading them (a job, followed here), and
  * removing them. */
object BasemapCmd:

  private val statusCmd = Opts.subcommand("status", "What is downloaded, where, and the free space there") {
    Output.jsonFlag.map { json => (c: Client) => c.basemapStatus().map(s => Output.emit(json, s)(BasemapMarkdown.status)) }
  }

  private val fetchCmd = Opts.subcommand("fetch",
      "Download the terrain base map (coast, water, rivers, land cover; relief) as a server job — resumable; " +
        "refused when the disk would be left with less than basemap.min-free-gb") {
    (Opts.option[String]("layer", "vector, terrain or all (default all)").withDefault("all"),
      Opts.option[Int]("max-zoom", "Deepest zoom of the vector layer (default basemap.vector.max-zoom)").orNone,
      Opts.option[Int]("terrain-max-zoom", "Deepest zoom of the relief, 512-pixel tiles (default basemap.terrain.max-zoom)").orNone,
      Opts.option[String]("bbox", "Region as minLon,minLat,maxLon,maxLat (default basemap.bbox)").orNone,
      Opts.flag("dry-run", "Read only the directories and report what the download would take").orFalse,
      Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse)
      .mapN { (layer, z, tz, bbox, dry, noWait) => (c: Client) =>
        c.basemapFetch(BasemapFetchQuery(layer, z, tz, bbox, dry)).flatMap { j =>
          println(s"base-map job `${j.id}` started: ${j.detail.flatMap(_.about).getOrElse("")} (`histmersion jobs` lists it)")
          if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
        }
      }
  }

  private val rmCmd = Opts.subcommand("rm", "Remove the downloaded base-map files (and an unfinished download)") {
    Opts.option[String]("layer", "vector, terrain or all (default all)").withDefault("all").map { layer => (c: Client) =>
      c.basemapRemove(layer).map(r => println(BasemapMarkdown.removed(r)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("basemap", "The terrain base map of the web UI: status, fetch, rm") {
      statusCmd orElse fetchCmd orElse rmCmd
    }

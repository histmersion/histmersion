package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

/** `histmersion basemap status|styles|fetch|rm` — the terrain base map's extracts on the SERVER's
  * machine (docs/user-guide/how-to/open-data.md): what is there, its named styles (and checking a style
  * file), downloading them (a job, followed here), and removing them. */
object BasemapCmd:

  private val statusCmd = Opts.subcommand("status", "What is downloaded, where, and the free space there") {
    Output.jsonFlag.map { json => (c: Client) => c.basemapStatus().map(s => Output.emit(json, s)(BasemapMarkdown.status)) }
  }

  private val stylesCmd = Opts.subcommand("styles",
      "The terrain map's named styles — the built-in ones and your style file over them — and what was refused") {
    (Opts.flag("example", "Print the built-in style document (commented): a start for basemap-style.conf").orFalse,
      Opts.option[java.nio.file.Path]("check", "Check a style file (HOCON or JSON) without using it").orNone,
      Output.jsonFlag).mapN { (example, check, json) => (c: Client) =>
        check match
          case Some(p) =>
            scala.util.Try(java.nio.file.Files.readString(p)).toEither.left
              .map(e => CliError(s"cannot read $p: ${e.getMessage}", exitCode = ExitCode.Usage))
              .flatMap(c.basemapStylesCheck)
              .flatMap { r =>
                Output.emit(json, r)(BasemapMarkdown.styleCheck)
                Either.cond(r.ok, (), CliError(s"$p is not a valid style file", exitCode = ExitCode.Refused))
              }
          case None if example => c.basemapStylesExample().map(t => print(t))
          case None            => c.basemapStyles().map(s => Output.emit(json, s)(BasemapMarkdown.styles))
      }
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
    Opts.subcommand("basemap", "The terrain base map of the web UI: status, styles, fetch, rm") {
      statusCmd orElse stylesCmd orElse fetchCmd orElse rmCmd
    }

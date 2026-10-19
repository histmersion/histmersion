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
      "Download the terrain base map (coast, water, rivers, land cover; relief; physical names; sea floor) as a server job — resumable; " +
        "refused when the disk would be left with less than basemap.min-free-gb") {
    (Opts.option[String]("layer", "vector, terrain, labels, bathymetry or all (default all)").withDefault("all"),
      Opts.option[Int]("max-zoom", "Deepest zoom of the vector layer (default basemap.vector.max-zoom; with --layer terrain alone, of the relief)").orNone,
      Opts.option[Int]("terrain-max-zoom", "Deepest zoom of the relief, 512-pixel tiles (default basemap.terrain.max-zoom)").orNone,
      Opts.option[String]("bbox", "Region as minLon,minLat,maxLon,maxLat (default basemap.bbox)").orNone,
      Opts.option[String]("focus", "Keep the relief of a region deeper than the extract, to basemap.terrain.on-demand.max-zoom: " +
        "a name of basemap.terrain.focus-regions (aegean, anatolia, levant, mesopotamia, nile-delta, argolid) or a new one with --bbox").orNone,
      Opts.flag("dry-run", "Read only the directories and report what the download would take").orFalse,
      Opts.flag("keep-all", "Store the vector layer unfiltered — roads, buildings and places too (basemap.vector.drop not applied)").orFalse,
      Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse)
      .mapN { (layer, z, tz, bbox, focus, dry, keepAll, noWait) => (c: Client) =>
        c.basemapFetch(BasemapFetchQuery(if focus.isDefined then "terrain" else layer, z, tz, bbox, dry, keepAll, focus)).flatMap { j =>
          println(s"base-map job `${j.id}` started: ${j.detail.flatMap(_.about).getOrElse("")} (`histmersion jobs` lists it)")
          if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
        }
      }
  }

  private val rmCmd = Opts.subcommand("rm",
      "Remove the downloaded base-map files (and an unfinished download), focus regions, the on-demand relief cache") {
    (Opts.option[String]("layer", "vector, terrain, labels, bathymetry or all — all includes the focus regions and the relief cache " +
        "(default all; none when only --focus or --cache is given)").orNone,
      Opts.option[String]("focus", "A focus region's relief, by name, or all").orNone,
      Opts.flag("cache", "The on-demand relief cache").orFalse).mapN { (layer, focus, cache) => (c: Client) =>
      val l = layer.getOrElse(if focus.isDefined || cache then "none" else "all")
      c.basemapRemove(BasemapRemoveQuery(l, focus, cache)).map(r => println(BasemapMarkdown.removed(r)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("basemap", "The terrain base map of the web UI: status, styles, fetch, rm") {
      statusCmd orElse stylesCmd orElse fetchCmd orElse rmCmd
    }

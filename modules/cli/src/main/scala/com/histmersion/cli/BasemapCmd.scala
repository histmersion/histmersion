package com.histmersion
package cli

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

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Status(json: Cli.JsonOutput)
    case Fetch(
        layer: String,
        z: Option[Int],
        tz: Option[Int],
        bbox: Option[String],
        focus: Option[String],
        dry: Boolean,
        keepAll: Boolean,
        noWait: Boolean
    )
    case Rm(layer: Option[String], focus: Option[String], cache: Boolean)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Status(json) =>
          c.basemapStatus().map(s => Output.emit(json, s)(BasemapMarkdown.status))
        case Command.Fetch(layer, z, tz, bbox, focus, dry, keepAll, noWait) =>
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
        case Command.Rm(layer, focus, cache) =>
          val l = layer.getOrElse(if focus.isDefined || cache then "none" else "all")
          c.basemapRemove(
            Basemap.RemoveQuery(
              Basemap.RemoveQuery.Layer(l),
              focus.map(Basemap.RemoveQuery.Focus(_)),
              Basemap.RemoveQuery.Cache(cache)
            )
          ).map(r => Cli.out.println(BasemapMarkdown.removed(r)))

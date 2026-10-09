package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

object EventsCmd:

  /** The filter flags are the shared ones: they select SOURCES, and an event is listed when its
    * source is one of them; `--from`/`--to` are matched against the event's own span, `--title`
    * against its label. The positional query searches the event's label and description. */
  private val search = Opts.subcommand("search", "Find events by label or description (the filter flags select their sources)") {
    (Opts.argument[String]("query").orNone,
      Opts.option[String]("kind", s"Filter by kind: ${EventKind.all.mkString("|")}").orNone,
      Opts.option[String]("historicity", s"Filter: ${Historicity.all.mkString("|")}").orNone,
      Opts.option[String]("place-id", "The event's own place id").orNone,
      FilterFlags.opts)
      .mapN { (q, kind, hist, place, f) => (c: Client) =>
        c.listEvents(Api.EventQuery(f, q.map(_.trim).filter(_.nonEmpty), kind, hist, place))
          .map(r => println(Markdown.eventList(r)))
      }
  }

  private val showCmd = Opts.subcommand("show", "One event: datings, edges, participants") {
    Opts.argument[String]("id").map { id => (c: Client) =>
      c.getEvent(id).map(d => println(Markdown.event(d)))
    }
  }

  /** The arguments of `events patch <id>`: the id and the patch they make (or why they make none). */
  private[cli] val patchOpts: Opts[(String, Either[CliError, EventPatch])] =
    (Opts.argument[String]("id"),
      Opts.option[String]("scale",
        "local (one place: a battle) | regional (across a region: an era) | auto (derive from kind, then place)").orNone,
      Opts.option[String]("place-id", "Gazetteer place id where it happened").orNone,
      Opts.option[Double]("lat", "Its own point: latitude (with --lon; write a negative as --lat=-1.5)").orNone,
      Opts.option[Double]("lon", "Its own point: longitude (with --lat)").orNone,
      Opts.option[String]("geometry", "Its own geometry as GeoJSON (instead of --lat/--lon)").orNone,
      Opts.option[String]("kind", "e.g. battle|siege|destruction|migration|war|cultural_shift").orNone,
      Opts.option[String]("historicity", Historicity.all.mkString("|")).orNone,
      Opts.option[String]("label", "Its label").orNone,
      Opts.option[String]("parent-id", "The event it is part of").orNone,
      Opts.option[String]("description", "Its description").orNone,
      Opts.option[String]("citekey", "Its primary source").orNone)
      .mapN { (id, scale, place, lat, lon, geometry, kind, hist, label, parent, desc, ck) =>
        val patch = for
          geom <- EventGeometry.of(geometry, lat, lon).left.map(m => CliError(m, exitCode = 1))
          _    <- scale.filter(EventScale.parse(_).isEmpty).fold(Right(()))(v =>
                    Left(CliError(s"--scale: '$v' is not local, regional or auto", exitCode = 1)))
          p     = EventPatch(label = label, kind = kind, historicity = hist, geometry = geom, placeId = place,
                    parentId = parent, description = desc, citekey = ck, scale = scale)
          _    <- Either.cond(p != EventPatch(), (), CliError("nothing to change: give at least one field", exitCode = 1))
        yield p
        (id, patch)
      }

  /** `events patch <id>`: the fields given change, the rest stay — `PATCH /api/events/{id}`. */
  private val patchCmd = Opts.subcommand("patch",
      "Change fields of one event: its scale, place, point, kind, label… (the rest stay)") {
    patchOpts.map { (id, patch) => (c: Client) =>
      for
        p <- patch
        d <- c.patchEvent(id, p)
      yield println(Markdown.event(d))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("events", "Historical events: search, show, patch and rename") {
      search orElse showCmd orElse patchCmd orElse LabelRenameCmd(BundleKind.Event)
    }

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

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("events", "Historical events: search, show and rename") {
      search orElse showCmd orElse LabelRenameCmd(BundleKind.Event)
    }

package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import histdb.core.*

object EventsCmd:

  private val search = Opts.subcommand("search", "Find events by label or description") {
    (Opts.argument[String]("query").orNone,
      Opts.option[String]("kind", s"Filter by kind: ${EventKind.all.mkString("|")}").orNone,
      Opts.option[String]("historicity", s"Filter: ${Historicity.all.mkString("|")}").orNone,
      Opts.option[String]("place", "Filter by place id").orNone,
      Opts.option[Long]("from", "Overlap: events reaching at or after this signed year").orNone,
      Opts.option[Long]("to", "Overlap: events reaching at or before this signed year").orNone,
      Opts.option[Int]("limit", "Max results").orNone)
      .mapN { (q, kind, hist, place, from, to, limit) => (c: Client) =>
        c.listEvents(Api.EventQuery(q, kind, hist, place, from, to, limit, None))
          .map(r => println(Markdown.eventList(r)))
      }
  }

  private val showCmd = Opts.subcommand("show", "One event: datings, edges, participants") {
    Opts.argument[String]("id").map { id => (c: Client) =>
      c.getEvent(id).map(d => println(Markdown.event(d)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("events", "Historical events: search and show") {
      search orElse showCmd
    }

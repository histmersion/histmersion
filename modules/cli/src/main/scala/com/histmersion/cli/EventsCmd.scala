package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.{BundleKind, Historicity}

object EventsCmd:

  /** The filter flags are the shared ones: they select SOURCES, and an event is listed when its
    * source is one of them; `--from`/`--to` are matched against the event's own span, `--title`
    * against its label. The positional query searches the event's label and description. */
  private val search = Opts.subcommand("search", "Find events by label or description (the filter flags select their sources)") {
    (Opts.argument[String]("query").orNone,
      Opts.option[String]("kind", s"A kind (${EventTaxonomy.kinds.map(_.kind).mkString("|")}) or a group: natural|human").orNone,
      Opts.option[String]("historicity", s"Filter: ${Historicity.all.mkString("|")}").orNone,
      Opts.option[String]("place-id", "The event's own place id").orNone,
      Opts.option[String]("tier", "attested (a source in the corpus attests it) | catalogue (a catalogue alone)").orNone,
      Opts.flag("with-catalogue", "Also list the catalogue events of the period, whatever sources the filter selects").orFalse,
      FilterFlags.opts, Output.jsonFlag)
      .mapN { (q, kind, hist, place, tier, ctx, f, json) => (c: Client) =>
        c.listEvents(Api.EventQuery(f, q.map(_.trim).filter(_.nonEmpty), kind, hist, place, tier, Option.when(ctx)(true)))
          .map(r => Output.emit(json, r)(Markdown.eventList))
      }
  }

  private val showCmd = Opts.subcommand("show", "One event: datings, edges, participants") {
    (Opts.argument[String]("id"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
      c.getEvent(id).map(d => Output.emit(json, d)(Markdown.event))
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
      Opts.option[String]("citekey", "Its primary source").orNone,
      Opts.option[String]("wikidata", "The Wikidata item it is (Q…; \"\" clears) — where `media import` reads its picture").orNone)
      .mapN { (id, scale, place, lat, lon, geometry, kind, hist, label, parent, desc, ck, wd) =>
        val patch = for
          geom <- EventGeometry.of(geometry, lat, lon).left.map(m => CliError(m, exitCode = ExitCode.Usage))
          _    <- scale.filter(EventScale.parse(_).isEmpty).fold(Right(()))(v =>
                    Left(CliError(s"--scale: '$v' is not local, regional or auto", exitCode = ExitCode.Usage)))
          p     = EventPatch(label = label, kind = kind, historicity = hist, geometry = geom, placeId = place,
                    parentId = parent, description = desc, citekey = ck, scale = scale, wikidata = wd)
          _    <- Either.cond(p != EventPatch(), (), CliError("nothing to change: give at least one field", exitCode = ExitCode.Usage))
        yield p
        (id, patch)
      }

  /** `events patch <id>`: the fields given change, the rest stay — `PATCH /api/events/{id}`. */
  private val patchCmd = Opts.subcommand("patch",
      "Change fields of one event: its scale, place, point, kind, label… (the rest stay); on a catalogue " +
        "event the changes are kept across re-imports (`events corrections` lists them)") {
    patchOpts.map { (id, patch) => (c: Client) =>
      for
        p <- patch
        d <- c.patchEvent(id, p)
      yield println(Markdown.event(d))
    }
  }

  // ── catalogues, links and leads (#1449) ──

  /** Follow a job, then print what it left (an import's report). */
  private[cli] def follow(c: Client, j: JobInfo, noWait: Boolean): Either[CliError, Unit] =
    println(s"job `${j.id}` started: ${j.detail.flatMap(_.events).getOrElse(j.kind)} (`histmersion jobs` lists it)")
    if noWait then Right(())
    else IngestCmd.pollJob(c, j.id, 0).flatMap(_ => c.job(j.id)).map { done =>
      done.detail.flatMap(_.eventImport).foreach(r => println(EventImportReport.markdown(r)))
    }

  private val importCmd = Opts.subcommand("import",
      "Bring in every event a catalogue knows — gvp (Smithsonian Holocene eruptions), noaa (NCEI earthquakes, " +
        "eruptions, tsunamis), wikidata (battles, sieges, wars, treaties, revolts, disasters), or a plugin's catalogue " +
        "(`histmersion events catalogues` lists them) — merged with the " +
        "events there (never overwriting a compiled one). A server job; resumable; --dry-run stores nothing and " +
        "prints what it would import") {
    (Opts.argument[String]("provider"),
      Opts.flag("dry-run", "Read the catalogue and count, store nothing").orFalse,
      Opts.option[Int]("limit", "Records to keep at most").orNone,
      Opts.option[String]("bbox", "Only events inside minLon,minLat,maxLon,maxLat").orNone,
      Opts.option[Long]("from", "Only events ending at or after this signed year (write a negative as --from=-3000)").orNone,
      Opts.option[Long]("to", "Only events starting at or before this signed year (default events.max-year)").orNone,
      Opts.option[Int]("min-vei", "Eruptions: at least this VEI, everywhere (default: all in the region, events.gvp.outside-min-vei outside)").orNone,
      Opts.option[Double]("min-magnitude", "Earthquakes: at least this magnitude, everywhere").orNone,
      Opts.flag("refresh", "Read again the parts an earlier import read").orFalse,
      Opts.flag("no-wait", "Print the job id and return").orFalse)
      .mapN { (p, dry, limit, bbox, from, to, vei, mag, refresh, noWait) => (c: Client) =>
        c.call(EventsApi.importEvents)(EventImportRequest(p, dry, limit, bbox, from, to, vei, mag, refresh))
          .flatMap(follow(c, _, noWait))
      }
  }

  private val rederiveCmd = Opts.subcommand("rederive",
      "Compute the dating of the stored catalogue records again from the records as read — NO network — and " +
        "refresh the events the catalogues created (corrections by hand kept): the step after an importer's " +
        "dating rules change. A server job; --dry-run counts what would change") {
    (Opts.option[String]("provider", "Only this catalogue: gvp | noaa | wikidata | a plugin's (default: all)").orNone,
      Opts.flag("dry-run", "Count what would change, store nothing").orFalse,
      Opts.flag("no-wait", "Print the job id and return").orFalse)
      .mapN { (p, dry, noWait) => (c: Client) =>
        c.call(EventsApi.rederive)(EventRederiveRequest(p, dry)).flatMap { j =>
          println(s"job `${j.id}` started: ${j.detail.flatMap(_.events).getOrElse(j.kind)} (`histmersion jobs` lists it)")
          if noWait then Right(())
          else IngestCmd.pollJob(c, j.id, 0).flatMap(_ => c.job(j.id)).map { done =>
            done.detail.flatMap(_.eventRederive).foreach(r => println(EventRederiveReport.markdown(r)))
          }
        }
      }
  }

  private val correctionsCmd = Opts.subcommand("corrections",
      "The fields of catalogue events corrected by hand (kept over the catalogue on a re-import): conflicts " +
        "first — the catalogue changed a corrected field to something else") {
    (Opts.option[String]("status", s"${CorrectionStatus.all.mkString(" | ")} (default: all but agreed)").orNone,
      Opts.option[String]("event", "Only this event's").orNone,
      Opts.option[Int]("limit", "At most this many").orNone)
      .mapN { (st, ev, limit) => (c: Client) =>
        c.call(EventsApi.corrections)((st, ev, limit, None)).map(l => println(Corrections.markdown(l)))
      }
  }

  private val cataloguesCmd = Opts.subcommand("catalogues", "The event catalogues `events import` can read on this server (the built-in ones and any a plugin registered), their licences and how to cite them") {
    Opts.unit.map { _ => (c: Client) =>
      c.call(EventsApi.catalogues)(()).map(cs => println(cs.map(i =>
        s"- **${i.id}** — ${i.title} (${i.home})\n  - licence: ${i.licence}\n  - cite as: ${i.citation}").mkString("\n")))
    }
  }

  private val linksCmd = Opts.subcommand("links",
      "The sources that may discuss an event (findings near it, passages naming it) — proposed, unconfirmed; " +
        "--apply confirms one (an attestation from a passage naming the event), --reject stops proposing one") {
    (Opts.argument[String]("event"),
      Opts.flag("recompute", "Recompute the proposals first").orFalse,
      Opts.options[String]("apply", "A citekey to confirm (repeatable)").orEmpty,
      Opts.options[String]("reject", "A citekey to stop proposing (repeatable)").orEmpty)
      .mapN { (id, re, confirm, reject) => (c: Client) =>
        if re || confirm.nonEmpty || reject.nonEmpty then
          c.call(EventsApi.applyLinks)((id, EventLinksRequest(re, confirm, reject))).map(r => println(EventLinksMarkdown.result(r)))
        else c.call(EventsApi.links)(id).map(l => println(EventLinksMarkdown.list(l)))
      }
  }

  private val candidatesCmd = Opts.subcommand("candidates",
      "Compute the proposed links of the events not checked yet (--all: every event), as a server job") {
    (Opts.flag("all", "Every event, not only those never checked").orFalse,
      Opts.option[String]("event", "Only this event").orNone,
      Opts.option[Int]("limit", "Events at most").orNone,
      Opts.flag("no-wait", "Print the job id and return").orFalse)
      .mapN { (all, ev, limit, noWait) => (c: Client) =>
        c.call(EventsApi.candidatesJob)(EventCandidatesRequest(all, ev, limit)).flatMap(follow(c, _, noWait))
      }
  }

  private val leadsCmd = Opts.subcommand("leads",
      "Questions the events raise: catalogue events no source in the corpus discusses, with the findings around " +
        "them counted and the commands that act on them (the filter flags select as for search)") {
    (Opts.option[String]("kind", "A kind or natural|human").orNone,
      Opts.option[String]("tier", "catalogue (default) | attested").orNone,
      Opts.option[String]("area", "Only events inside minLon,minLat,maxLon,maxLat").orNone,
      FilterFlags.opts)
      .mapN { (kind, tier, area, f) => (c: Client) =>
        c.call(EventsApi.leads)(Api.EventQuery(f, kind = kind, tier = tier, area = area)).map(l => println(EventLeads.markdown(l)))
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("events", "Historical events: search, show, patch, rename; import and re-derive catalogues, corrections, links to sources, leads") {
      search orElse showCmd orElse patchCmd orElse LabelRenameCmd(BundleKind.Event) orElse importCmd orElse
        rederiveCmd orElse correctionsCmd orElse cataloguesCmd orElse linksCmd orElse candidatesCmd orElse leadsCmd
    }

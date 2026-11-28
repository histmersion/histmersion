package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.EventsMarkdown
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.catalogue.Historicity
import com.histmersion.api.catalogue.{Event, EventCorrection, EventSummary}
import com.histmersion.api.library.Citekey
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.catalogue.{
  EventCandidatesRequest,
  EventGeometry,
  EventImportReport,
  EventImportRequest,
  EventLinksMarkdown,
  EventLinksRequest,
  EventPatch,
  EventRederiveReport,
  EventRederiveRequest,
  EventsApi,
  EventScale,
  EventTaxonomy
}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.catalogue.Correction
import com.histmersion.util.console.Console
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.EventsJobKinds
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.cli.ExitCode
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line of [[EventsCmd]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion events`: declares its options, parses them into typed commands"
)
object EventsArgs:
  object EventsEntry extends CommandLine.Entry:
    val name = "events"
    def help: String =
      "Historical events: search, show, patch, rename; import and re-derive catalogues, corrections, links to sources, leads"
    def conf(): Subcommand = GroupEvents()

  final private class LeafSearch()
      extends Leaf(
        "histmersion events",
        "search",
        "Find events by label or description (the filter flags select their sources)"
      )
      with SharedFlags:
    private val q_ = arg[String]("query", required = false)
    private val kind_ =
      value[String]("kind", s"A kind (${EventTaxonomy.kinds.map(_.kind).mkString("|")}) or a group: natural|human")
    private val hist_ = value[String]("historicity", s"Filter: ${Historicity.all.mkString("|")}")
    private val place_ = value[String]("place-id", "The event's own place id")
    private val tier_ =
      value[String]("tier", "attested (a source in the corpus attests it) | catalogue (a catalogue alone)")
    private val ctx_ =
      flag("with-catalogue", "Also list the catalogue events of the period, whatever sources the filter selects")
    private val f_ = filterFlags()
    private val json_ = json()

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Search(
        q_.toOption,
        kind_.toOption,
        hist_.toOption,
        place_.toOption,
        tier_.toOption,
        ctx_.getOrElse(false),
        f_(),
        json_()
      )
    )

  final private class LeafShow()
      extends Leaf("histmersion events", "show", "One event: datings, edges, participants")
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(EventsCmd.Command.Show(id_(), json_()))

  final private class LeafPatch()
      extends Leaf(
        "histmersion events",
        "patch",
        "Change fields of one event: its scale, place, point, kind, label… (the rest stay); on a catalogue " +
          "event the changes are kept across re-imports (`events corrections` lists them)"
      )
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val scale_ = value[String](
      "scale",
      "local (one place: a battle) | regional (across a region: an era) | auto (derive from kind, then place)"
    )
    private val place_ = value[String]("place-id", "Gazetteer place id where it happened")
    private val lat_ = value[Double]("lat", "Its own point: latitude (with --lon; write a negative as --lat=-1.5)")
    private val lon_ = value[Double]("lon", "Its own point: longitude (with --lat)")
    private val geometry_ = value[String]("geometry", "Its own geometry as GeoJSON (instead of --lat/--lon)")
    private val kind_ = value[String]("kind", "e.g. battle|siege|destruction|migration|war|cultural_shift")
    private val hist_ = value[String]("historicity", Historicity.all.mkString("|"))
    private val label_ = value[String]("label", "Its label")
    private val parent_ = value[String]("parent-id", "The event it is part of")
    private val desc_ = value[String]("description", "Its description")
    private val ck_ = value[String]("citekey", "Its primary source")
    private val wd_ = value[String](
      "wikidata",
      "The Wikidata item it is (Q…; \"\" clears) — where `media import` reads its picture"
    )

    def command: Either[Problems, Cli.Command] =
      EventsCmd
        .patch(
          scale_.toOption,
          place_.toOption,
          lat_.toOption,
          lon_.toOption,
          geometry_.toOption,
          kind_.toOption,
          hist_.toOption,
          label_.toOption,
          parent_.toOption,
          desc_.toOption,
          ck_.toOption,
          wd_.toOption
        )
        .left
        .map(Cli.Error(_))
        .left
        .map(_.problems)
        .map(EventsCmd.Command.Patch(id_(), _))

  final private class LeafImport()
      extends Leaf(
        "histmersion events",
        "import",
        "Bring in every event a catalogue knows — gvp (Smithsonian Holocene eruptions), noaa (NCEI earthquakes, " +
          "eruptions, tsunamis), wikidata (battles, sieges, wars, treaties, revolts, disasters), or a plugin's catalogue " +
          "(`histmersion events catalogues` lists them) — merged with the " +
          "events there (never overwriting a compiled one). A server job; resumable; --dry-run stores nothing and " +
          "prints what it would import"
      )
      with SharedFlags:
    private val p_ = arg[String]("provider")
    private val writes_ = dryRun("Read the catalogue and count, store nothing")
    private val limit_ = value[Int]("limit", "Records to keep at most")
    private val bbox_ = value[String]("bbox", "Only events inside minLon,minLat,maxLon,maxLat")
    private val from_ =
      value[Long]("from", "Only events ending at or after this signed year (write a negative as --from=-3000)")
    private val to_ = value[Long]("to", "Only events starting at or before this signed year (default events.max-year)")
    private val vei_ = value[Int](
      "min-vei",
      "Eruptions: at least this VEI, everywhere (default: all in the region, events.gvp.outside-min-vei outside)"
    )
    private val mag_ = value[Double]("min-magnitude", "Earthquakes: at least this magnitude, everywhere")
    private val refresh_ = flag("refresh", "Read again the parts an earlier import read")
    private val following_ = noWait("Print the job id and return")

    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Import(
        p_(),
        writes_(),
        limit_.toOption,
        bbox_.toOption,
        from_.toOption,
        to_.toOption,
        vei_.toOption,
        mag_.toOption,
        refresh = refresh_.getOrElse(false),
        following_()
      )
    )

  final private class LeafRederive()
      extends Leaf(
        "histmersion events",
        "rederive",
        "Compute the dating of the stored catalogue records again from the records as read — NO network — and " +
          "refresh the events the catalogues created (corrections by hand kept): the step after an importer's " +
          "dating rules change. A server job; --dry-run counts what would change"
      )
      with SharedFlags:
    private val p_ = value[String]("provider", "Only this catalogue: gvp | noaa | wikidata | a plugin's (default: all)")
    private val writes_ = dryRun("Count what would change, store nothing")
    private val following_ = noWait("Print the job id and return")

    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Rederive(p_.toOption, writes_(), following_())
    )

  final private class LeafCorrections()
      extends Leaf(
        "histmersion events",
        "corrections",
        "The fields of catalogue events corrected by hand (kept over the catalogue on a re-import): conflicts " +
          "first — the catalogue changed a corrected field to something else"
      )
      with SharedFlags:
    private val st_ = value[String]("status", s"${Correction.Status.all.mkString(" | ")} (default: all but agreed)")
    private val ev_ = value[String]("event", "Only this event's")
    private val limit_ = value[Int]("limit", "At most this many")

    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Corrections(st_.toOption, ev_.toOption, limit_.toOption)
    )

  final private class LeafCatalogues()
      extends Leaf(
        "histmersion events",
        "catalogues",
        "The event catalogues `events import` can read on this server (the built-in ones and any a plugin registered), their licences and how to cite them"
      )
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(EventsCmd.Command.Catalogues())

  final private class LeafLinks()
      extends Leaf(
        "histmersion events",
        "links",
        "The sources that may discuss an event (findings near it, passages naming it) — proposed, unconfirmed; " +
          "--apply confirms one (an attestation from a passage naming the event), --reject stops proposing one"
      )
      with SharedFlags:
    private val id_ = arg[String]("event")
    private val re_ = flag("recompute", "Recompute the proposals first")
    private val confirm_ = values[String]("apply", "A citekey to confirm (repeatable)")
    private val reject_ = values[String]("reject", "A citekey to stop proposing (repeatable)")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Links(id_(), re_.getOrElse(false), confirm_.getOrElse(Nil), reject_.getOrElse(Nil))
    )

  final private class LeafCandidates()
      extends Leaf(
        "histmersion events",
        "candidates",
        "Compute the proposed links of the events not checked yet (--all: every event), as a server job"
      )
      with SharedFlags:
    private val all_ = flag("all", "Every event, not only those never checked")
    private val ev_ = value[String]("event", "Only this event")
    private val limit_ = value[Int]("limit", "Events at most")
    private val following_ = noWait("Print the job id and return")

    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Candidates(all = all_.getOrElse(false), ev_.toOption, limit_.toOption, following_())
    )

  final private class LeafLeads()
      extends Leaf(
        "histmersion events",
        "leads",
        "Questions the events raise: catalogue events no source in the corpus discusses, with the findings around " +
          "them counted and the commands that act on them (the filter flags select as for search)"
      )
      with SharedFlags:
    private val kind_ = value[String]("kind", "A kind or natural|human")
    private val tier_ = value[String]("tier", "catalogue (default) | attested")
    private val area_ = value[String]("area", "Only events inside minLon,minLat,maxLon,maxLat")
    private val f_ = filterFlags()

    def command: Either[Problem, Cli.Command] = Right(
      EventsCmd.Command.Leads(kind_.toOption, tier_.toOption, area_.toOption, f_())
    )

  final private class GroupEvents()
      extends Group(
        "histmersion",
        "events",
        "Historical events: search, show, patch, rename; import and re-derive catalogues, corrections, links to sources, leads"
      ):
    holds(
      LeafSearch(),
      LeafShow(),
      LeafPatch(),
      LabelRenameArgs.leaf("histmersion events", Bundle.Kind.Event),
      LeafImport(),
      LeafRederive(),
      LeafCorrections(),
      LeafCatalogues(),
      LeafLinks(),
      LeafCandidates(),
      LeafLeads()
    )

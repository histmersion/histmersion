package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.PlacesMarkdown
import com.histmersion.api.{Api, Dataset, Paging}
import com.histmersion.config.*
import java.nio.file.{Files, Path}
import com.histmersion.domain.catalogue.places.PlaceSeed
import com.histmersion.api.catalogue.{EventsApi, Lpf}
import com.histmersion.api.catalogue.{Latitude, Longitude, Place, Year}
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.util.console.Console
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[PlacesCmd]]. */
object PlacesArgs:
  object PlacesEntry extends CommandLine.Entry:
    val name = "places"
    def help: String =
      "The gazetteer: search, show, rename, name, set, merge, rm, add, import, load external sources and materialise places"
    def conf(): Subcommand = GroupPlaces()

  final private class LeafSearch()
      extends Leaf("histmersion places", "search", "Find places by name; the `id` it prints is what `--place` wants")
      with SharedFlags:
    private val q_ = arg[String]("query", required = false)
    private val asOf_ =
      value[Api.AggregateQuery.AsOf]("as-of", "Signed year the gazetteer is read at (BCE negative, e.g. -1400)")
    private val limit_ = value[Int]("limit", "Max results")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Search(q_.toOption, asOf_.toOption, limit_.toOption)
    )

  final private class LeafShow()
      extends Leaf("histmersion places", "show", "One place: geometry, validity, parents and the subtree")
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val asOf_ =
      value[Api.AggregateQuery.AsOf]("as-of", "Signed year the gazetteer is read at (BCE negative, e.g. -1400)")
    private val depth_ = value[Int]("depth", "How deep the subtree goes")
    private val noTree_ = flag("no-tree", "Skip the hierarchy walk")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Show(id_(), asOf_.toOption, depth_.toOption, noTree_.getOrElse(false))
    )

  final private class LeafChildren()
      extends Leaf("histmersion places", "children", "What one place contains, as of a year")
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val asOf_ =
      value[Api.AggregateQuery.AsOf]("as-of", "Signed year the gazetteer is read at (BCE negative, e.g. -1400)")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.Children(id_(), asOf_.toOption))

  final private class LeafSiteTypes()
      extends Leaf(
        "histmersion places",
        "site-types",
        "Refine the type (settlement, fortification, burial…) of the Wikidata sites on the map from their items' " +
          "classes, as a server job; Pleiades and GeoNames sites are typed from their own fields already"
      )
      with SharedFlags:
    private val dry_ = flag("dry-run", "Count the sites it would ask about")
    private val limit_ = value[Int]("limit", "Sites at most")
    private val refresh_ = flag("refresh", "Ask again about sites asked before")
    private val following_ = noWait("Print the job id and return")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command
        .SiteTypes(dry = dry_.getOrElse(false), limit_.toOption, refresh = refresh_.getOrElse(false), following_())
    )

  final private class LeafNameAdd()
      extends Leaf(
        "histmersion places name",
        "add",
        "Give a place a further name (kept by every re-seed): `places name add pleiades_678430 'Lake Kinneret' --lang en`"
      )
      with SharedFlags:
    private val id_ = arg[String]("place-id")
    private val name_ = arg[String]("name")
    private val lang_ = value[String]("lang", "Language tag of the name: en, grc, la, pl … (omit when unknown)")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.NameAdd(id_(), name_(), lang_.toOption))

  final private class LeafNameRm()
      extends Leaf(
        "histmersion places name",
        "rm",
        "Remove a name added by hand (a name the seed gives cannot be removed)"
      )
      with SharedFlags:
    private val id_ = arg[String]("place-id")
    private val name_ = arg[String]("name")
    private val lang_ = value[String]("lang", "Language tag of the name: en, grc, la, pl … (omit when unknown)")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.NameRm(id_(), name_(), lang_.toOption))

  final private class LeafNameListing()
      extends Leaf(
        "histmersion places name",
        "list",
        "The names added by hand; --csv prints them as db/places/supplement/names.csv lines, to promote into the seed"
      )
      with SharedFlags:
    private val place_ = value[String]("place", "Only this place's (an id)")
    private val csv_ = flag("csv", "Print CSV (place_id,name,lang,why) instead of Markdown")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.NameListing(place_.toOption, csv_.getOrElse(false))
    )

  final private class GroupName()
      extends Group("histmersion places", "name", "Names added by hand to gazetteer places: add, rm, list"):
    holds(LeafNameAdd(), LeafNameRm(), LeafNameListing())

  final private class LeafSetCmd()
      extends Leaf(
        "histmersion places",
        "set",
        "Set what a place IS over what its source says — kind, period and/or point; kept by every re-seed, its findings matched again: " +
          "`places set pleiades_886355306 --kind region --reason 'one of the 14 Augustan regiones'`"
      )
      with SharedFlags:
    private val id_ = arg[String]("place-id")
    private val kind_ = value[String]("kind", s"The kind it is: ${Place.Kind.all.mkString("|")}")
    private val from_ =
      value[Long]("from", "Signed year it begins (BCE negative); the end stays as set (open the first time)")
    private val to_ =
      value[Long]("to", "Signed year it ends (BCE negative); the start stays as set (open the first time)")
    private val point_ = value[String](
      "point",
      "Its corrected point, latitude,longitude (a place known by a point only; --reason must name the gazetteer value)"
    )
    private val reason_ = value[String]("reason", "Why: the evidence the decision rests on")
    private val reset_ = flag("reset", "Drop the override: the kind, period and point its source states come back")

    def command: Either[Problem, Cli.Command] =
      point_.toOption
        .map(p => Place.Overrides.parsePoint(Place.Overrides.PointText(p)).map(Some(_)))
        .getOrElse(Right(None))
        .map(point =>
          PlacesCmd.Command.SetCmd(
            id_(),
            kind_.toOption,
            from_.toOption,
            to_.toOption,
            point,
            reason_.toOption,
            reset_.getOrElse(false)
          )
        )

  final private class LeafOverrides()
      extends Leaf(
        "histmersion places",
        "overrides",
        "The kinds, periods and points set by hand (`places set`), with what each source says"
      )
      with SharedFlags:
    private val place_ = value[String]("place", "Only this place's (an id)")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.Overrides(place_.toOption))

  final private class LeafMerge()
      extends Leaf(
        "histmersion places",
        "merge",
        "Merge a place into the one it is the same as — findings, links, children and references move; its id resolves there, and no re-seed brings it back"
      )
      with SharedFlags:
    private val id_ = arg[String]("place-id")
    private val into_ = arg[String]("into-id")
    private val reason_ = value[String]("reason", "Why: the evidence the decision rests on")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.Merge(id_(), into_(), reason_.toOption))

  final private class LeafRm()
      extends Leaf(
        "histmersion places",
        "rm",
        "Remove a place nothing refers to (refused while an event, attestation, child, hand-added name or a finding with no other place does); no re-seed brings it back"
      )
      with SharedFlags:
    private val id_ = arg[String]("place-id")
    private val reason_ = value[String]("reason", "Why: the evidence the decision rests on")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.Rm(id_(), reason_.toOption))

  final private class LeafAdd()
      extends Leaf(
        "histmersion places",
        "add",
        "Add a point place of this collection's own (never touched by a re-seed); --source says where the point comes from"
      )
      with SharedFlags:
    private val label_ = arg[String]("label")
    private val kind_ = value[String]("kind", s"One of ${Place.Kind.all.mkString(", ")}")
    private val lon_ = value[Double]("lon", "Longitude, decimal degrees (east positive)", required = true)
    private val lat_ = value[Double]("lat", "Latitude, decimal degrees (north positive)", required = true)
    private val source_ = value[String](
      "source",
      "Where the coordinates come from: a gazetteer or Wikidata id, a map, a publication",
      required = true
    )
    private val id_ = value[String]("id", s"The place id (default ${Place.Edits.LocalPrefix}<label>)")
    private val names_ = values[String]("name", "A further name (repeatable)")
    private val lang_ = value[String]("lang", "Language tag of the name: en, grc, la, pl … (omit when unknown)")
    private val from_ = value[Long]("from", "Signed year the place begins (BCE negative)")
    private val to_ = value[Long]("to", "Signed year the place ends (BCE negative)")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Add(
        label_(),
        kind_.getOrElse(Place.Kind.Site),
        lon_(),
        lat_(),
        source_(),
        id_.toOption,
        names_.getOrElse(Nil),
        lang_.toOption,
        from_.toOption,
        to_.toOption
      )
    )

  final private class LeafImport()
      extends Leaf("histmersion places", "import", "Upsert a Linked Places / GeoJSON file into the gazetteer")
      with SharedFlags:
    private val file_ = arg[String]("file.json")
    private val kind_ =
      value[String]("kind", s"Fallback kind for features that declare none: ${Place.Kind.all.mkString("|")}")
    private val batch_ = value[Int]("batch", "Features per request")
    private val dataset_ = datasetFlag()

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Import(file_(), kind_.toOption, batch_.toOption, dataset_())
    )

  final private class LeafSeed()
      extends Leaf(
        "histmersion places",
        "seed",
        "Import every gazetteer file under db/places (Natural Earth, Pleiades, traced polities)"
      )
      with SharedFlags:
    private val dir_ = value[String]("dir", "Directory of LPF/GeoJSON files (default: config places.seedDir)")
    private val batch_ = value[Int]("batch", "Features per request")
    private val noRebuild_ = flag("no-rebuild", "Do not re-materialise finding_places afterwards")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Seed(dir_.toOption, batch_.toOption, noRebuild_.getOrElse(false))
    )

  final private class LeafFetch()
      extends Leaf(
        "histmersion places",
        "fetch",
        "Download and import an external gazetteer (cliopatria, awmc, chronontology) or the coastline (land). Re-runnable and idempotent."
      )
      with SharedFlags:
    private val source_ = arg[String]("source")
    private val dryRun_ = flag("dry-run", "Parse and transform only — do not upsert")
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Fetch(source_(), dryRun = dryRun_.getOrElse(false), following_())
    )

  final private class LeafSources()
      extends Leaf(
        "histmersion places",
        "sources",
        "The external gazetteer sources: the pinned GeoNames and Wikidata extracts, the LPF files in the drop folder, and what was imported"
      )
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.Sources())

  final private class LeafLoad()
      extends Leaf(
        "histmersion places",
        "load",
        "Import an external source — geonames, wikidata, tgn (Getty) or lpf (the drop folder) — joined to the gazetteer by shared ids; a dry run unless --apply"
      )
      with SharedFlags:
    private val source_ = arg[String]("geonames|wikidata|tgn|lpf")
    private val apply_ =
      flag("apply", "Import for real (without it: a dry run that reports what would change and keeps nothing)")
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(
      PlacesCmd.Command.Load(source_(), apply = apply_.getOrElse(false), following_())
    )

  final private class LeafRebuild()
      extends Leaf("histmersion places", "rebuild", "Re-materialise which findings fall in which places")
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.Rebuild())

  final private class LeafBuildHierarchy()
      extends Leaf(
        "histmersion places",
        "build-hierarchy",
        "Compute parent-child edges from spatial containment (point inside polygon). Idempotent."
      )
      with SharedFlags:
    private val following_ = noWait("Print the job id and return instead of following it")

    def command: Either[Problem, Cli.Command] = Right(PlacesCmd.Command.BuildHierarchy(following_()))

  final private class GroupPlaces()
      extends Group(
        "histmersion",
        "places",
        "The gazetteer: search, show, rename, name, set, merge, rm, add, import, load external sources and materialise places"
      ):
    holds(
      LeafSearch(),
      LeafShow(),
      LeafChildren(),
      LeafSiteTypes(),
      LabelRenameArgs.leaf("histmersion places", Bundle.Kind.Place),
      GroupName(),
      LeafSetCmd(),
      LeafOverrides(),
      LeafMerge(),
      LeafRm(),
      LeafAdd(),
      LeafImport(),
      LeafSeed(),
      LeafFetch(),
      LeafSources(),
      LeafLoad(),
      LeafRebuild(),
      LeafBuildHierarchy()
    )

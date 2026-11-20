package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.*
import java.nio.file.{Files, Path}
import com.histmersion.api.library.{
  AcquireApi,
  AcquireMarkdown,
  ArchiveId,
  ArchiveImportRequest,
  AssessRequest,
  Citekey,
  FollowupsRequest,
  RefsRequest,
  RetractionsUpdate,
  Title
}
import com.histmersion.api.library.Ingest
import com.histmersion.util.console.Console
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[AcquireCmd]]. */
object AcquireArgs:
  object DiscoverEntry extends CommandLine.Entry:
    val name = "discover"
    def help: String = "Find literature upstream (OpenAlex / Crossref / Wikipedia)"
    def conf(): Subcommand = GroupDiscover()

  object RefsEntry extends CommandLine.Entry:
    val name = "refs"
    def help: String = "The reference list a paper deposited, resolved against the corpus"
    def conf(): Subcommand = LeafRefs()

  object AssessEntry extends CommandLine.Entry:
    val name = "assess"
    def help: String = "Retraction / correction / impact verdict for a paper (retracted|corrected|current|low-impact)"
    def conf(): Subcommand = GroupAssess()

  object FollowupsEntry extends CommandLine.Entry:
    val name = "followups"
    def help: String =
      "Papers citing this one, with intents and a proposed edge kind (confirms|challenges|refutes|supersedes|cites)"
    def conf(): Subcommand = LeafFollowups()

  object IngestEntry extends CommandLine.Entry:
    val name = "ingest"
    def help: String =
      "Bring a paper in: a local PDF/EPUB (uploaded and OCR'd, keeping its figures) or an identifier " +
        "(metadata → legal OA PDF → the same OCR job → reference edges → assessment)"
    def conf(): Subcommand = LeafIngest()

  object ArchiveEntry extends CommandLine.Entry:
    val name = "archive"
    def help: String = "Internet Archive: search, inspect and import public texts"
    def conf(): Subcommand = GroupArchive()

  final private class LeafDiscoverSearch()
      extends Leaf("histmersion discover", "search", "OpenAlex search for literature to ingest")
      with SharedFlags:
    private val q_ = arg[String]("query")
    private val since_ = value[Int]("since", "Only works published from this year on")
    private val sort_ = value[String]("sort", "relevance (default) | cited")
    private val limit_ = value[Int]("limit", "Max results")
    private val notInDb_ = flag("not-in-db", "Only works the corpus does NOT have yet")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      AcquireCmd.Command
        .DiscoverSearch(q_(), since_.toOption, sort_.toOption, limit_.toOption, notInDb_.getOrElse(false), json_())
    )

  final private class LeafDiscoverMeta()
      extends Leaf("histmersion discover", "meta", "Normalised metadata for one DOI")
      with SharedFlags:
    private val doi_ = arg[String]("doi")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AcquireCmd.Command.DiscoverMeta(doi_(), json_()))

  final private class LeafDiscoverWikirefs()
      extends Leaf("histmersion discover", "wikirefs", "Harvest the DOIs a Wikipedia article cites (the trailhead)")
      with SharedFlags:
    private val url_ = arg[String]("wikipedia-url")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AcquireCmd.Command.DiscoverWikirefs(url_(), json_()))

  final private class GroupDiscover()
      extends Group("histmersion", "discover", "Find literature upstream (OpenAlex / Crossref / Wikipedia)"):
    holds(LeafDiscoverSearch(), LeafDiscoverMeta(), LeafDiscoverWikirefs())

  final private class LeafRefs()
      extends Leaf("histmersion", "refs", "The reference list a paper deposited, resolved against the corpus")
      with SharedFlags:
    private val id_ = arg[String]("citekey|doi")
    private val apply_ = flag("apply", "Write the resolved references as `references` edges")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AcquireCmd.Command.Refs(id_(), apply_.getOrElse(false), json_()))

  final private class LeafAssessUpdateRetractions()
      extends Leaf(
        "histmersion assess",
        "update-retractions",
        "Refresh the local Retraction Watch mirror (~60 000 rows)"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AcquireCmd.Command.AssessUpdateRetractions(json_()))

  /** `assess <citekey|doi>` judges one paper; `assess update-retractions` refreshes the mirror. */
  final private class GroupAssess()
      extends Leaf(
        "histmersion",
        "assess",
        "Retraction / correction / impact verdict for a paper (retracted|corrected|current|low-impact)"
      )
      with SharedFlags:
    private val id_ = arg[String]("citekey|doi")
    private val apply_ =
      flag("apply", "Write the verdict into the source's status (only over 'unassessed'; a retraction always)")
    private val force_ = flag("force-status", "With --apply, overwrite a status that is already set")
    private val json_ = json()
    holds(LeafAssessUpdateRetractions())

    def command: Either[Problem, Cli.Command] =
      Right(AcquireCmd.Command.Assess(id_(), apply_.getOrElse(false), force_.getOrElse(false), json_()))

  final private class LeafFollowups()
      extends Leaf(
        "histmersion",
        "followups",
        "Papers citing this one, with intents and a proposed edge kind (confirms|challenges|refutes|supersedes|cites)"
      )
      with SharedFlags:
    private val id_ = arg[String]("citekey|doi")
    private val since_ = value[Int]("since", "Only citing papers published from this year on")
    private val limit_ = value[Int]("limit", "Max citing papers")
    private val apply_ = flag("apply", "Record the proposed edges for citing papers already in the corpus")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      AcquireCmd.Command.Followups(id_(), since_.toOption, limit_.toOption, apply_.getOrElse(false), json_())
    )

  final private class LeafIngest()
      extends Leaf(
        "histmersion",
        "ingest",
        "Bring a paper in: a local PDF/EPUB (uploaded and OCR'd, keeping its figures) or an identifier " +
          "(metadata → legal OA PDF → the same OCR job → reference edges → assessment)"
      )
      with SharedFlags:
    private given ValueConverter[AcquireCmd.IngestTarget] = stringConverter.map(AcquireCmd.IngestTarget.of)
    private val id_ = arg[AcquireCmd.IngestTarget]("file|doi|arxiv:id")
    private val citekey_ = value[String](
      "citekey",
      "Citekey to store it under (required for a file; overrides the generated one for an identifier)"
    )
    private val arxiv_ = value[String]("arxiv", "arXiv id to try when the DOI has no OA copy")
    private val quality_ = flag("quality", "File upload: slow, accurate OCR (docling) — for image-only scans")
    private val force_ = flag("force", "File upload: accept a file already stored under another citekey")
    private val title_ = value[String]("title", "File upload: title, used only when the source is being created")
    private val authors_ =
      values[String]("author", "File upload: author, used only when the source is being created (repeatable)")
    private val year_ = value[Int]("year", "File upload: publication year, used only when the source is being created")
    private val doi_ = value[String]("doi", "File upload: DOI, used only when the source is being created")
    private val prov_ = value[String](
      "provenance",
      "How the source was acquired: manual|ill|author|scan|external-file|calibre|archive|oa (default: manual for file, oa for DOI)"
    )
    private val noWait_ = flag("no-wait", "File upload: print the job id and return instead of following it")
    private val refetch_ = flag(
      "refetch",
      "Identifier: fetch and convert again although the source already has its original (replaces its body)"
    )
    private val legalOnly_ = flag(
      "legal-only",
      "Identifier: ask the legal open-access chain only, never a fetcher the config marks unauthorised (the default)"
    )
    private val allowUnauthorised_ = flag(
      "allow-unauthorised",
      "Identifier: a person's explicit choice to also ask the fetchers the config marks unauthorised " +
        "(only with acquire.allow-unauthorised = true)"
    )
    private val item_ = value[Int](
      "acquire-item",
      "File upload: id of the acquisition-queue item this file fulfils — ticked off when the OCR job succeeds"
    )
    private val json_ = json()

    def command: Either[Problem, Cli.Command] =
      AcquireCmd
        .legalOnly(legalOnly_.getOrElse(false), allowUnauthorised_.getOrElse(false))
        .map(legalOnly =>
          AcquireCmd.Command.Ingest(
            id_(),
            citekey_.toOption,
            arxiv_.toOption,
            quality_.getOrElse(false),
            force_.getOrElse(false),
            title_.toOption,
            authors_.getOrElse(Nil),
            year_.toOption,
            doi_.toOption,
            prov_.toOption,
            noWait_.getOrElse(false),
            refetch_.getOrElse(false),
            legalOnly,
            item_.toOption,
            json_()
          )
        )

  final private class LeafArchiveSearch()
      extends Leaf("histmersion archive", "search", "Search archive.org texts")
      with SharedFlags:
    private val q_ = arg[String]("query")
    private val rows_ = value[Int]("rows", "Max results")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AcquireCmd.Command.ArchiveSearch(q_(), rows_.toOption, json_()))

  final private class LeafArchiveInfo()
      extends Leaf("histmersion archive", "info", "One archive.org item: files and lending restriction")
      with SharedFlags:
    private val id_ = arg[String]("identifier")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AcquireCmd.Command.ArchiveInfo(id_(), json_()))

  final private class LeafArchiveImport()
      extends Leaf("histmersion archive", "import", "Import a PUBLIC archive.org item (lending-only is refused)")
      with SharedFlags:
    private val id_ = arg[String]("identifier")
    private val citekey_ = value[String]("citekey", "Override the generated citekey")
    private val format_ = value[String]("format", "epub | pdf (default: whichever carries text)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      AcquireCmd.Command.ArchiveImport(id_(), citekey_.toOption, format_.toOption, json_())
    )

  final private class GroupArchive()
      extends Group("histmersion", "archive", "Internet Archive: search, inspect and import public texts"):
    holds(LeafArchiveSearch(), LeafArchiveInfo(), LeafArchiveImport())

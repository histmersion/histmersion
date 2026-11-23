package com.histmersion
package cli
package commandline
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.Api
import com.histmersion.config.*
import java.nio.file.Path
import com.histmersion.api.library.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{
  CalibreGap,
  CalibrePushRequest,
  CollectionFieldValue,
  CollectionImportRequest,
  CollectionLinkRequest,
  CollectionPushRequest,
  CollectionsApi,
  CollectionSetRequest,
  CollectionsMarkdown,
  Ingest,
  MetadataPatch
}
import com.histmersion.api.media.MediaCoversRequest
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Paging
import com.histmersion.api.library.Query
import com.histmersion.api.library.Source
import com.histmersion.api.library.RepairAcquire
import com.histmersion.config.CalibreCliConfig
import com.histmersion.api.library.Title
import com.histmersion.api.library.CollectionSync
import com.histmersion.api.library.CollectionUpdateAll
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.CollectionEntry
import com.histmersion.api.library.CollectionAudit
import com.histmersion.api.library.AcquireApi
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[CalibreCmd]]. */
object LibraryArgs:
  object LibraryEntry extends CommandLine.Entry:
    val name = "calibre"
    def help: String = "The local Calibre library: search, import, link, push, update, set, audit, sync, gaps"
    def conf(): Subcommand = GroupCalibre()

  final private class LeafSearch()
      extends Leaf(
        "histmersion calibre",
        "search",
        "Search the local Calibre library (read-only); --unlinked lists the entries no source links yet, newest first"
      )
      with SharedFlags:
    private val q_ = arg[String]("query", required = false)
    private val unlinked_ = flag("unlinked", "Only entries no source is linked to (the query, if given, narrows them)")
    private val limit_ = value[Int]("limit", "Max results")
    private val json_ = json()

    // an EMPTY query is the whole library (`search "" --limit 2000`, the enrich-calibre audit); only a missing one is
    // refused
    def command: Either[Problem, Cli.Command] =
      if q_.isEmpty && !unlinked_.getOrElse(false) then Left(Problem.Invalid.Missing.here(Problem.Field("query")))
      else Right(CalibreCmd.Command.Search(q_.toOption, unlinked_.getOrElse(false), limit_.toOption, json_()))

  final private class LeafImport()
      extends Leaf(
        "histmersion calibre",
        "import",
        "Import a purchased book from Calibre into the corpus and convert it (the Calibre original is never touched)"
      )
      with SharedFlags:
    private val id_ = arg[Int]("calibre-id")
    private val citekey_ = value[String]("citekey", "Override the generated citekey")
    private val format_ = value[String]("format", "pdf | epub")
    private val noConvert_ = flag("no-convert", "Create or enrich the record only; do not upload the file for OCR")
    private val following_ = noWait("Queue the conversion and return instead of following it")
    private val item_ = value[Int](
      "acquire-item",
      "Id of the acquisition-queue item this book fulfils — ticked off when the conversion succeeds (without it, an open item naming the book's DOI is ticked off, and similar ones are listed)"
    )
    private val json_ = json()

    // the item is ticked off by the conversion: the two together cannot both hold
    def command: Either[Problem, Cli.Command] =
      if noConvert_.getOrElse(false) && item_.isDefined then
        Left(
          Problem.Invalid.Inconsistent.here(
            NonEmptyVector.of(Problem.Field("--no-convert"), Problem.Field("--acquire-item")),
            Problem.Invalid.Exclusion.AtMostOne
          )
        )
      else
        Right(
          CalibreCmd.Command.Import(
            id_(),
            citekey_.toOption,
            format_.toOption,
            noConvert = noConvert_.getOrElse(false),
            following_(),
            item_.toOption,
            json_()
          )
        )

  final private class LeafLink()
      extends Leaf(
        "histmersion calibre",
        "link",
        "Link a source to an existing Calibre entry (checks the entry exists; Calibre itself is not changed)"
      )
      with SharedFlags:
    private val ckText_ = arg[String]("citekey")
    private val idValue_ = arg[Int]("calibre-id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(CalibreCmd.Command.Link(ckText_(), idValue_(), json_()))

  final private class LeafPush()
      extends Leaf(
        "histmersion calibre",
        "push",
        "Add a stored source's file to Calibre with title/authors/DOI/tags; --replace swaps the file of its linked entry instead (Calibre GUI must be closed)"
      )
      with SharedFlags:
    private val ckText_ = arg[String]("citekey")
    private val file_ = value[String](
      "file",
      "The file to add (default: the original the server stored; without --replace, <cli.calibre.originals-dir>/<citekey>/paper.* first when present)"
    )
    private val replaceFlag_ =
      flag("replace", "Replace the file of the source's LINKED Calibre entry instead of adding a new entry")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      CalibreCmd.Command.Push(ckText_(), file_.toOption, replaceFlag_.getOrElse(false), json_())
    )

  /** `update <citekey>` rewrites one entry, `update --all [--apply]` every linked one: one or the other. */
  final private class LeafUpdate()
      extends Leaf(
        "histmersion calibre",
        "update",
        "Rewrite a source's Calibre entry from the corpus: title, authors, year, identifiers, tags (and publisher/comments where empty); --all for every linked entry (Calibre GUI must be closed)"
      )
      with SharedFlags:
    private val ck_ = arg[String]("citekey", required = false)
    private val all_ =
      flag("all", "Every Calibre entry a source is linked to (a dry run listing the changes, unless --apply)")
    private val apply_ = flag("apply", "With --all: write the changes (Calibre GUI must be closed)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] =
      (ck_.toOption, all_.getOrElse(false)) match
        case (Some(ck), false) if !apply_.getOrElse(false) => Right(CalibreCmd.Command.UpdateOne(ck, json_()))
        case (None, true)     => Right(CalibreCmd.Command.UpdateAll(Api.Apply(apply_.getOrElse(false)), json_()))
        case (Some(_), false) =>
          Left(
            Problem.Invalid.Inconsistent.here(
              NonEmptyVector.of(Problem.Field("citekey"), Problem.Field("--apply")),
              Problem.Invalid.Exclusion.AtMostOne
            )
          )
        case _ =>
          Left(
            Problem.Invalid.Inconsistent.here(
              NonEmptyVector.of(Problem.Field("citekey"), Problem.Field("--all")),
              Problem.Invalid.Exclusion.OneOf
            )
          )

  final private class LeafSetCmd()
      extends Leaf(
        "histmersion calibre",
        "set",
        s"Set fields on a Calibre entry, e.g. --field languages:eng (settable: ${Calibre.SettableFields.mkString(", ")}; Calibre GUI must be closed)"
      )
      with SharedFlags:
    private val idValue_ = arg[Int]("calibre-id")
    private val raw_ = values[String]("field", "<name>:<value>, repeatable", required = true)

    def command: Either[Problem, Cli.Command] = Right(CalibreCmd.Command.SetCmd(idValue_(), raw_()))

  final private class LeafAudit()
      extends Leaf(
        "histmersion calibre",
        "audit",
        "Compare every source's Calibre entry with the corpus record (title, authors, year) — read-only"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(CalibreCmd.Command.Audit(json_()))

  final private class LeafSync()
      extends Leaf(
        "histmersion calibre",
        "sync",
        "Push every source with a fetched file that is not in Calibre yet (dry run unless --apply)"
      )
      with SharedFlags:
    private val apply_ = flag("apply", "Actually add them (Calibre GUI must be closed)")
    private val limit_ = value[Int]("limit", "Only consider this many gaps")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      CalibreCmd.Command.Sync(apply_.getOrElse(false), limit_.toOption, json_())
    )

  final private class LeafGaps()
      extends Leaf(
        "histmersion calibre",
        "gaps",
        "List sources with a fetched file that are missing from Calibre; --quiet prints one line and exits 1 when there are any (the reminder hook)"
      )
      with SharedFlags:
    private val quiet_ = flag("quiet", "One line, exit 1 when gaps exist (records with no fetched file never count)")
    private val limit_ = value[Int]("limit", "Max gaps to list")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      CalibreCmd.Command.Gaps(quiet_.getOrElse(false), limit_.toOption, json_())
    )

  final private class GroupCalibre()
      extends Group(
        "histmersion",
        "calibre",
        "The local Calibre library: search, import, link, push, update, set, audit, sync, gaps"
      ):
    holds(
      LeafSearch(),
      LeafImport(),
      LeafLink(),
      LeafPush(),
      LeafUpdate(),
      LeafSetCmd(),
      LeafAudit(),
      LeafSync(),
      LeafGaps()
    )

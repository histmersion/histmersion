package com.histmersion
package server
import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.mcp.McpOut
import com.histmersion.util.data.maintenance.WriteGate
import chimp.server.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.{Api, Markdown}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueuePatch}
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.automation.Queue
import com.histmersion.api.checks.{FormatCheck, FormatField, FormatMarkdown, Lint}
import com.histmersion.api.library.AcquireChecksMarkdown
import com.histmersion.api.library.Acquire
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.checks.CheckControllers
import com.histmersion.domain.library.search.QueryTools
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.server.views.WorkListView
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.server.agents.EngineContributions
import com.histmersion.api.automation.Creation
import com.histmersion.domain.Creator
import com.histmersion.api.automation.Creation
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{FeaturePlace, LintRule, movesTo, tolerated}

/** The MCP half of stream 2.8: `lint`, `queue`, `query` — the three tools that replace the research workspace's
  * `lint-wiki`, `bookkeeping` and "just run DuckDB SQL" habits (plan §5.1).
  *
  * Same rule as the rest of the server: a tool is a thin wrapper over the controllers REST binds and returns Markdown,
  * so the MCP output and the CLI output are the same bytes.
  */
@movesTo(FeaturePlace.Split)
object OpsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(h: ServerWiring) =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)
    val lint =
      tool("lint")
        .description(
          s"Health-check the corpus and get a Markdown report grouped by rule. ${h.checks.lintRules.size} rules: " +
            h.checks.lintRules.mkString(", ") +
            ". Run it after a batch of ingestion to find dead reference targets, un-enriched sources " +
            "(no findings / period / regions / summary), sources still at status=unassessed, " +
            "duplicate DOIs, non-canonical tags, year/citekey mismatches, article filler phrases, " +
            "uncited paragraphs, findings that carry neither a date nor a place, and Markdown that will " +
            "not display as written (article-markdown, note-markdown). Pass `rules` " +
            "to run a subset, or `fix: true` to first apply the two safe repairs — unify unambiguous " +
            "short citekey targets and rebuild missing full-text chunks (with `rules: [\"finding-geocode\"]`, " +
            "also move the findings an older geocoder misplaced; with `rules: [\"finding-unplaced\"]`, give a point to the findings whose place name the gazetteer now knows; with `rules: [\"place-kind\"]`, reclassify the point-only regions that are peoples, provinces or settlements and merge withdrawn duplicates; with `rules: [\"place-grid-point\"]`, move atlas-grid-square points to their linked record's point; with `rules: [\"place-imported-point-area\"]`, remove the GeoNames/Wikidata regions and polities known only by a point — bounded per call: call again while `fixed` says some are still to remove) — and report the state afterwards. Pass `article` (a slug), `source` (a citekey) or `text` (Markdown not stored " +
            "yet) instead to check just that one for FORMATTING problems, each with line, column, " +
            "excerpt and what goes wrong; with `fix: true` the wording-safe repairs are applied to it " +
            "(for `text`, the repaired text is returned). Use after ingestion, after writing an " +
            "article, or to diagnose data integrity — not for searching."
        )
        .input[LintInput]
        .handle { in =>
          CreatorControllers.mcp(in) {
            val subject = (in.article.map(_.trim).filter(_.nonEmpty), in.source.map(_.trim).filter(_.nonEmpty), in.text)
            subject match
              case (Some(raw), _, _) =>
                Slug
                  .parse(raw)
                  .fold(
                    p => McpOut.refused(p),
                    slug =>
                      (if in.fix then h.research.articles.fix(slug) else h.research.articles.lint(slug))
                        .fold(McpOut.error, r => ToolResult.text(FormatMarkdown.report(r)))
                  )
              case (_, Some(raw), _) =>
                Citekey
                  .parse(raw)
                  .fold(
                    p => McpOut.refused(p),
                    ck =>
                      (if in.fix then h.library.sources.fix(ck) else h.library.sources.lint(ck))
                        .fold(McpOut.error, r => ToolResult.text(FormatMarkdown.report(r)))
                  )
              case (_, _, Some(text)) =>
                val r = h.formats.check(
                  FormatCheck(FormatCheck.Text(text), in.field.getOrElse(FormatField.Body), FormatCheck.Fix(in.fix))
                )
                ToolResult.text(
                  FormatMarkdown.report(r) + r.text.fold("")(t => s"\n\n### Repaired text\n\n````markdown\n$t\n````")
                )
              case _ =>
                h.checks.lintWithFix(
                  in.rules.map(CheckRule.Name(_)),
                  CheckControllers.ApplyFix(in.fix)
                ) match
                  case Right(rep) => ToolResult.text(Lint.markdown(rep).value)
                  case Left(e)    => McpOut.error(e)
          }
        }

    val queue =
      tool("queue")
        .description(
          "The project's bookkeeping, as three lists: `acquire` (what the USER must buy/borrow/" +
            "download), `tasks` (the agent's backlog) and `log` (the dated worklog). One tool, one " +
            "`action`: `list` shows the open items of `list` with their ids (`all: true` also shows " +
            "done items and the file's prose blocks), `add` appends `text` under an optional `topic`, " +
            "`done` closes item `id`, `recheck` runs the acquisition list's legal re-check now (closes the items " +
            "the corpus holds with their text, tries a few due ones through the legal open-access chain only; " +
            "`id` = that item alone, `limit` = at most that many tried, `all: true` = every open item with a DOI, " +
            "due or not, past the daily caps; `dryRun: true` only lists what it would close and try; a pass of more " +
            "than one item runs as a job — follow it with job_status). This is where a queue/tracker/changelog belongs — never write " +
            "one as a new file. `topic` groups items under a heading; on `log` it defaults to today's " +
            "date, so an added line lands in today's entry. `add` requires `createdBy`, who adds the item: " +
            "`plugin` and `workflow` (both), and the agent as `agentDefinition`, `provider` and `model` (all three, " +
            "or none for a step without a model)."
        )
        .input[QueueInput]
        .handle(in => runQueue(h, in))

    val backups =
      tool("backups")
        .description(
          "List the backup archives on the server (pg_dump -Fc), newest first: filename, size and " +
            "timestamp. Read-only — use it to check whether a backup exists before a bulk operation. " +
            "Creating a backup is the `backup` tool; restoring is `histmersion restore` (human only)."
        )
        .input[BackupsInput]
        .handle { _ =>
          h.upkeep.backupList match
            case Right(rows) => ToolResult.text(Markdown.backups(rows.toVector))
            case Left(e)     => McpOut.error(e)
        }

    List(lint, queue) ++ QueryTools(h.library.query) ++ List(backups)

  /** `action` dispatch, kept out of the tool body so the failure modes read as one list. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def runQueue(h: ServerWiring, in: QueueInput)(using Creator.New): ToolResult[NoStructuredOutput] =
    def render(list: String) =
      h.workListView.list(
        WorkListControllers.KindOfList(list),
        WorkListView.IncludeAll(in.all)
      ) match
        case Right(l) =>
          l.markdown.map(_.value).fold(McpOut.unknown(Problem.TypeName("Queue.Kind"), s"${l.list}"))(McpOut.text)
        case Left(e) => McpOut.error(e)

    in.action.trim.toLowerCase match
      case "list" =>
        in.list
          .map(render)
          .getOrElse(
            McpOut.error(
              Api.Error(Problem.Invalid.Missing.here(Problem.Field("list"))),
              s"The lists: ${Queue.Kind.all.mkString(", ")}."
            )
          )
      case "add" =>
        (in.list, in.text.map(_.trim).filter(_.nonEmpty)) match
          case (Some(list), Some(text)) =>
            val added = h.workLists.addOverMcp(
              WorkListControllers.ListName(list),
              QueueAdd(
                Queue.QueueItem.Text(text),
                in.topic.map(Queue.QueueItem.Topic(_)),
                reason = in.reason.map((text => Queue.Rationale(text))),
                createdBy = in.createdBy
              )
            )
            McpOut.of(added)(i =>
              s"added **#${i.id}** to ${i.list}${i.topic.map(t => s" · $t").getOrElse("")}: ${i.text}"
            )
          case (None, _) => McpOut.missing(Problem.Field("list"))
          case _         => McpOut.missing(Problem.Field("text"))
      case "done" =>
        in.id match
          case Some(id) =>
            McpOut.of(
              h.workLists.patch(
                QueueItemId(id),
                QueuePatch(status = Some(Queue.QueueItem.Status(Queue.Done)))
              )
            )(i => s"done **#${i.id}**: ${i.text}")
          case None =>
            McpOut.error(
              Api.Error(Problem.Invalid.Missing.here(Problem.Field("id"))),
              "Run `action: list` to see the items' ids."
            )
      case "recheck" =>
        McpOut.of(
          h.maintenance
            .guard(WriteGate.OperationName("acquire/recheck"))(
              EngineContributions
                .work(h.agents)
                .acquireRecheck
                .run(
                  Acquire.Checks.RecheckRequest(
                    Acquire.Checks.RecheckRequest.DryRun(in.dryRun),
                    in.limit.map(Acquire.Checks.RecheckRequest.Limit(_)),
                    in.id.map(Acquire.Checks.RecheckRequest.Id(_)),
                    Some(Acquire.Checks.RecheckRequest.By("mcp")),
                    Acquire.Checks.RecheckRequest.All(in.all)
                  )
                )
            )
        )(AcquireChecksMarkdown.result)
      case other =>
        McpOut.unknown(
          Problem.TypeName("QueueAction"),
          other,
          Problem.Raw("list"),
          Problem.Raw("add"),
          Problem.Raw("done"),
          Problem.Raw("recheck")
        )

  // Inputs last, per the file convention: JSON codec + tapir Schema both derived by Kindlings.
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final case class LintInput(
      rules: List[String] = Nil,
      fix: Boolean = false,
      article: Option[String] = None,
      source: Option[String] = None,
      text: Option[String] = None,
      field: Option[FormatField] = None,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec
  object LintInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[LintInput] = KindlingsSchema
      .derived[LintInput]
      .schema
      .modify(_.rules)(_.description("Run only these rules (omit for all); an unknown rule is a 400"))
      .modify(_.fix)(
        _.description(
          "First apply safe repairs (resolve short edge targets, reindex stale chunks), then report; with article/source/text, apply the wording-safe formatting repairs to it"
        )
      )
      .modify(_.article)(_.description("Check one article's body and summary for formatting problems (a slug)"))
      .modify(_.source)(_.description("Check one source's summary and notes for formatting problems (a citekey)"))
      .modify(_.text)(_.description("Check Markdown that is not stored yet (e.g. an article body before put_article)"))
      .modify(_.field)(_.description("What `text` is: body (an article body, the default), summary or notes"))

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final case class QueueInput(
      action: String,
      list: Option[String] = None,
      text: Option[String] = None,
      topic: Option[String] = None,
      reason: Option[String] = None,
      id: Option[Int] = None,
      all: Boolean = false,
      dryRun: Boolean = false,
      limit: Option[Int] = None,
      createdBy: Option[Creation.By] = None
  ) derives JsonCodec
  object QueueInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[QueueInput] = KindlingsSchema
      .derived[QueueInput]
      .schema
      .modify(_.action)(
        _.description("What to do: list | add | done | recheck (the acquisition list's legal re-check)")
      )
      .modify(_.list)(_.description("Which queue: acquire | tasks | log"))
      .modify(_.text)(_.description("The item text (for action=add)"))
      .modify(_.topic)(_.description("Group the item under this heading (defaults to today's date for log)"))
      .modify(_.reason)(
        _.description("Why this item is on the list / what research direction it serves (for action=add)")
      )
      .modify(_.id)(_.description("The item id (for action=done; for action=recheck: that acquisition item only)"))
      .modify(_.all)(
        _.description(
          "Include done items and prose blocks (for action=list); for action=recheck: every open " +
            "acquisition item with a DOI, due or not, past the caps (a job)"
        )
      )
      .modify(_.dryRun)(
        _.description("action=recheck: list what it would close and try; no fetcher is asked, nothing stored")
      )
      .modify(_.limit)(
        _.description("action=recheck: try at most this many due items (default acquire.recheck.per-pass)")
      )

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class BackupsInput() derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

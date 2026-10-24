package com.histmersion.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Queue.{QueueAdd, QueuePatch}

/** The MCP half of stream 2.8: `lint`, `queue`, `query` — the three tools that replace the research
  * workspace's `lint-wiki`, `bookkeeping` and "just run DuckDB SQL" habits (plan §5.1).
  *
  * Same rule as the rest of the server: a tool is a thin wrapper over [[OpsHandlers]] and returns
  * Markdown, so the MCP output and the CLI output are the same bytes.
  */
object OpsTools:
  import McpTools.given // honour Scala defaults when decoding tool arguments

  def apply(ops: OpsHandlers, h: Handlers) =
    val lint =
      tool("lint")
        .description(
          s"Health-check the corpus and get a Markdown report grouped by rule. ${Lint.all.size} rules: " +
            Lint.names.mkString(", ") +
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
            "article, or to diagnose data integrity — not for searching.")
        .input[LintInput]
        .handle { in =>
          val subject = (in.article.map(_.trim).filter(_.nonEmpty), in.source.map(_.trim).filter(_.nonEmpty), in.text)
          subject match
            case (Some(slug), _, _) =>
              (if in.fix then h.fixArticle(slug) else h.lintArticle(slug))
                .fold(McpOut.error, r => ToolResult.text(FormatMarkdown.report(r)))
            case (_, Some(ck), _) =>
              (if in.fix then h.fixSource(ck) else h.lintSource(ck))
                .fold(McpOut.error, r => ToolResult.text(FormatMarkdown.report(r)))
            case (_, _, Some(text)) =>
              val r = h.lintText(FormatCheck(text, in.field.getOrElse(FormatField.Body), in.fix))
              ToolResult.text(FormatMarkdown.report(r) + r.text.fold("")(t => s"\n\n### Repaired text\n\n````markdown\n$t\n````"))
            case _ =>
              ops.lintWithFix(in.rules, in.fix) match
                case Right(rep) => ToolResult.text(Lint.markdown(rep))
                case Left(e)    => McpOut.error(e)
        }

    val queue =
      tool("queue")
        .description(
          "The project's bookkeeping, as three lists: `acquire` (what the USER must buy/borrow/" +
            "download), `tasks` (the agent's backlog) and `log` (the dated worklog). One tool, one " +
            "`action`: `list` shows the open items of `list` with their ids (`all: true` also shows " +
            "done items and the file's prose blocks), `add` appends `text` under an optional `topic`, " +
            "`done` closes item `id`. This is where a queue/tracker/changelog belongs — never write " +
            "one as a new file. `topic` groups items under a heading; on `log` it defaults to today's " +
            "date, so an added line lands in today's entry.")
        .input[QueueInput]
        .handle(in => ToolResult.text(runQueue(ops, in)))

    val query =
      tool("query")
        .description(ops.queryToolDescription)
        .input[QueryInput]
        .handle { in =>
          ops.query(Query.QueryRequest(in.sql)) match
            case Right(r) => ToolResult.text(Query.markdown(r))
            // hand the live schema back with the error so the next attempt can be right
            case Left(e) =>
              ToolResult.text(s"${McpOut.refusal(e)}\n\n${Query.schemaMarkdown(ops.schemaTables)}")
        }

    val schema =
      tool("schema")
        .description(
          "List the tables, columns and types that `query` can read (from the live " +
            "information_schema). Use to check column names before writing SQL — not for " +
            "searching the corpus.")
        .input[SchemaInput]
        .handle(_ => ToolResult.text(Query.schemaMarkdown(ops.schemaTables)))

    val backups =
      tool("backups")
        .description(
          "List the backup archives on the server (pg_dump -Fc), newest first: filename, size and " +
            "timestamp. Read-only — use it to check whether a backup exists before a bulk operation. " +
            "Creating a backup is the `backup` tool; restoring is `histmersion restore` (human only).")
        .input[BackupsInput]
        .handle { _ =>
          ops.backups match
            case Right(rows) => ToolResult.text(Markdown.backups(rows.toVector))
            case Left(e)     => McpOut.error(e)
        }

    List(lint, queue, query, schema, backups)

  /** `action` dispatch, kept out of the tool body so the failure modes read as one list. */
  private def runQueue(ops: OpsHandlers, in: QueueInput): String =
    def render(list: String) =
      ops.queueList(list, in.all) match
        case Right(l) =>
          Queue.Kind.parse(l.list).map(k => Queue.markdown(k, l.items)).getOrElse(s"_unknown list ${l.list}_")
        case Left(e) => McpOut.refusal(e)

    in.action.trim.toLowerCase match
      case "list" =>
        in.list.map(render).getOrElse(s"_give a list: ${Queue.Kind.all.mkString(", ")}_")
      case "add" =>
        (in.list, in.text.map(_.trim).filter(_.nonEmpty)) match
          case (Some(list), Some(text)) =>
            ops.queueAdd(list, QueueAdd(text, in.topic, reason = in.reason)) match
              case Right(i) => s"added **#${i.id}** to ${i.list}${i.topic.map(t => s" · $t").getOrElse("")}: ${i.text}"
              case Left(e)  => McpOut.refusal(e)
          case _ => "_add needs both `list` and `text`_"
      case "done" =>
        in.id match
          case Some(id) =>
            ops.queuePatch(id, QueuePatch(status = Some(Queue.Done))) match
              case Right(i) => s"done **#${i.id}**: ${i.text}"
              case Left(e)  => McpOut.refusal(e)
          case None => "_done needs an `id` (run action=list to see them)_"
      case other =>
        s"_unknown action '$other' — use list, add or done_"

  // Inputs last, per the file convention: circe Codec + tapir Schema both derived by Kindlings.
  final case class LintInput(
      rules: List[String] = Nil,
      fix: Boolean = false,
      article: Option[String] = None,
      source: Option[String] = None,
      text: Option[String] = None,
      field: Option[FormatField] = None
  ) derives KindlingsCodecAsObject
  object LintInput:
    given Schema[LintInput] = KindlingsSchema.derived[LintInput].schema
      .modify(_.rules)(_.description("Run only these rules (omit for all); an unknown rule is a 400"))
      .modify(_.fix)(_.description("First apply safe repairs (resolve short edge targets, reindex stale chunks), then report; with article/source/text, apply the wording-safe formatting repairs to it"))
      .modify(_.article)(_.description("Check one article's body and summary for formatting problems (a slug)"))
      .modify(_.source)(_.description("Check one source's summary and notes for formatting problems (a citekey)"))
      .modify(_.text)(_.description("Check Markdown that is not stored yet (e.g. an article body before put_article)"))
      .modify(_.field)(_.description("What `text` is: body (an article body, the default), summary or notes"))

  final case class QueueInput(
      action: String,
      list: Option[String] = None,
      text: Option[String] = None,
      topic: Option[String] = None,
      reason: Option[String] = None,
      id: Option[Int] = None,
      all: Boolean = false
  ) derives KindlingsCodecAsObject
  object QueueInput:
    given Schema[QueueInput] = KindlingsSchema.derived[QueueInput].schema
      .modify(_.action)(_.description("What to do: list | add | done"))
      .modify(_.list)(_.description("Which queue: acquire | tasks | log"))
      .modify(_.text)(_.description("The item text (for action=add)"))
      .modify(_.topic)(_.description("Group the item under this heading (defaults to today's date for log)"))
      .modify(_.reason)(_.description("Why this item is on the list / what research direction it serves (for action=add)"))
      .modify(_.id)(_.description("The item id (for action=done)"))
      .modify(_.all)(_.description("Include done items and prose blocks (for action=list)"))

  final case class QueryInput(sql: String) derives KindlingsCodecAsObject
  object QueryInput:
    given Schema[QueryInput] = KindlingsSchema.derived[QueryInput].schema
      .modify(_.sql)(_.description("One read-only SQL statement (Postgres refuses writes)"))

  final case class SchemaInput() derives KindlingsCodecAsObject
  object SchemaInput:
    given Schema[SchemaInput] = KindlingsSchema.derived[SchemaInput].schema

  final case class BackupsInput() derives KindlingsCodecAsObject
  object BackupsInput:
    given Schema[BackupsInput] = KindlingsSchema.derived[BackupsInput].schema

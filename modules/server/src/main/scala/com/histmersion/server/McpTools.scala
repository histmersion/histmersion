package com.histmersion.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.{EditionsPolicy, PlaceKind}
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.mcp.McpOut

/** MCP tools (Chimp). Every tool returns Markdown and delegates to the same [[Handlers]] as REST. */
final class McpTools(
    h: Handlers,
    acquire: com.histmersion.server.acquire.AcquireHandlers,
    collections: com.histmersion.domain.library.collections.CollectionsControllers
):
  import McpTools.*

  private val ping =
    tool("ping")
      .description("Liveness check for the Histmersion MCP server")
      .input[PingInput]
      .handle(_ => ToolResult.text(s"${h.version.name} ${h.version.version} — ok"))

  // ── ingest, as a JOB (plan §1.8) ────────────────────────────────────────────────────────────
  private val ingest =
    tool("ingest")
      .description(
        "OCR a PDF/EPUB into a source and keep its figures. Give EITHER `path` (a path the SERVER " +
          "can see) OR `base64` (the document itself, for smaller files) plus a `citekey`. This " +
          "returns immediately with a job id, because converting a long scan takes minutes: poll " +
          "`job_status` until it is done|failed. `quality` runs the slow, accurate OCR path (use it " +
          "for image-only scans); `force` accepts a file already stored under another citekey. When " +
          "the citekey already exists only its body and figures are replaced — findings, tags and " +
          "notes are left alone — so re-OCR never costs enrichment. Optional title/authors/year/doi " +
          "are used ONLY when the source is being created. `acquireItem` names the acquisition-queue " +
          "item this document fulfils: the job ticks it off (`ingested as [[citekey]]`) when it " +
          "succeeds, and leaves it open when it fails. Without it, the job ticks off every open " +
          "acquisition item that names the source's DOI, and lists items with a similar title in its " +
          "progress as possible matches — those are never closed for you."
      )
      .input[IngestInput]
      .handle { in =>
        h.ingest.fromTool(
          in.citekey,
          in.path,
          in.base64,
          in.filename,
          in.quality,
          in.force,
          in.title,
          in.authors,
          in.year,
          in.doi,
          in.acquireItem
        ) match
          case Right(a) =>
            ToolResult.text(s"queued job `${a.jobId}` for **${a.citekey}** — poll `job_status` with that id")
          case Left(e) => McpOut.error(e)
      }

  private val jobStatus =
    tool("job_status")
      .description(
        "Where an OCR job got to: queued|running|done|failed, with its progress line or the reason " +
          "it failed. Give `id` for one job, or omit it to list the recent ones — filtered on the server by " +
          "`status`, `kind` (ingest, reocr, agent, media-import, …), `since` / `until` (2026-10-01, an ISO instant, or " +
          "7d / 12h ago) and `subject` (text in what it is about); the listing heads with the counts per kind. A job that says 'interrupted' was killed by a server restart — re-run the " +
          "same ingest, it is idempotent."
      )
      .input[JobStatusInput]
      .handle { in =>
        in.id.map(_.trim).filter(_.nonEmpty) match
          case Some(id) =>
            h.ingest.job(id) match
              case Right(j) => ToolResult.text(IngestMarkdown.job(j))
              case Left(e)  => McpOut.error(e)
          case None =>
            val f = in.filter
            ToolResult.text(
              h.ingest
                .jobsPage(f, in.limit, in.before, None)
                .fold(
                  McpOut.refusal,
                  (js, page) =>
                    IngestMarkdown.jobs(
                      js.toVector,
                      page,
                      f.qualifier,
                      Paging.olderLine(page, c => s"call `job_status` again with the same filters and `before: \"$c\"`")
                    )
                )
            )
      }

  private val jobControlTool =
    tool("job_control")
      .description(
        "Pause, resume or cancel a job by `id` (`action`: pause | resume | cancel). A long background job — a picture " +
          "or event import, a base-map download, an acquisition re-check, the init job's gazetteer loads — stops after " +
          "the part it is on: paused it keeps what it did and, resumed, goes on with the parts it had not done; " +
          "cancelled it is recorded as cancelled. An agent run takes its own pause, resume and cancel. Other jobs (an " +
          "OCR, a vacuum) run as one piece and are refused. Only when the user asked for it."
      )
      .input[JobControlInput]
      .handle(in =>
        h.jobControl.control(in.id, in.action).fold(McpOut.error, j => ToolResult.text(IngestMarkdown.job(j)))
      )

  private val activityTool =
    tool("activity")
      .description(
        "Research › Activity: agent runs, background jobs, worklog lines and the person's own decisions in ONE list, " +
          "newest first, each naming what it touched (goal, run or job, source, article, list item, approval). `show`: " +
          "all (default) | running | agents | jobs | yours | problems; `since` (2026-10-01, an ISO instant, or 7d / 12h ago); " +
          "`goal` = one research goal's history; `limit` (default 30, at most 200); `before` = the cursor the last page " +
          "gave, for the next older one. Use to see what happened recently or what is going on now."
      )
      .input[ActivityInput]
      .handle { in =>
        h.activity
          .page(in.show, in.since, in.goal, in.limit, in.before)
          .fold(
            McpOut.error,
            p =>
              ToolResult.text(
                ActivityMarkdown
                  .page(p, c => s"_Older: call `activity` again with the same filters and `before: \"$c\"`._")
              )
          )
      }

  /** Backup is here because an agent asked to "save the state before a big reseed" should be able to do it; RESTORE
    * deliberately is NOT — it drops the database, which is not a decision to leave to a tool call (`histmersion
    * restore` asks a human to confirm).
    */
  /** The config reload (docs/research-plugins.md §3.5): an agent that edited the user's config file on request applies
    * it without a restart.
    */
  private val configTool =
    tool("config")
      .description(
        "The server's own settings, kept in its settings file (never in the database). `get` shows settings " +
          "(`key` one, or `prefix` a block such as `acquire`): what the file says, what applies and from where, " +
          "the default and what the setting does — a credential only as set or not and how it is referred to. " +
          "`set` (`key`, `value` as a person would type it) and `unset` (`key`) change the file in place, keeping " +
          "its comments, order, includes and anything written by hand, check the change first (a refused change " +
          "says why in plain words), keep the old file as .bak and reload the server. A credential is written as " +
          "a reference — env:NAME or <vault>:<entry> — and `value:` only with `literalConsent`. `reload` reads the " +
          "file again and switches the running server to it in place; `restart` restarts a server that runs " +
          "under its service, for the settings only a restart applies (the listening host/port, the bundled " +
          "runtime, pool sizes)."
      )
      .input[ConfigInput]
      .handle { in =>
        def page(f: com.histmersion.api.ConfigEdit.Page => String, prefix: Option[String]) =
          h.settings.page(prefix).fold(McpOut.error, p => ToolResult.text(f(p)))
        in.action.trim.toLowerCase(java.util.Locale.ROOT) match
          case "reload" => ToolResult.text(ReloadMarkdown.report(h.configReload()))
          case "get"    =>
            in.key.map(_.trim).filter(_.nonEmpty) match
              case Some(k) => page(p => p.field(k).fold(s"No setting called $k.")(ConfigEditMarkdown.field), Some(k))
              case None    => page(ConfigEditMarkdown.page, in.prefix)
          case "set" | "unset" =>
            in.key.map(_.trim).filter(_.nonEmpty) match
              case None    => McpOut.error(ApiError(s"`${in.action}` needs a `key`"))
              case Some(k) =>
                val value = if in.action.trim.equalsIgnoreCase("unset") then None else Some(in.value.getOrElse(""))
                h.settings
                  .write(
                    com.histmersion.api.ConfigEdit.Write(
                      List(com.histmersion.api.ConfigEdit.Change(k, value)),
                      literalConsent = in.literalConsent.getOrElse(false)
                    )
                  )
                  .fold(McpOut.error, w => ToolResult.text(ConfigEditMarkdown.written(w)))
          case "restart" => h.settings.restart().fold(McpOut.error, r => ToolResult.text(ConfigEditMarkdown.restart(r)))
          case other => McpOut.error(ApiError(s"unknown action '$other' (one of: get, set, unset, reload, restart)"))
      }

  private val backup =
    tool("backup")
      .description(
        "Take a full pg_dump backup of the Histmersion database, server-side, and return the file name, " +
          "size and SHA-256 checksum. Use it before a bulk reseed, a mass patch, or any other " +
          "operation you may need to undo. Restoring is NOT available as a tool: it destroys data, " +
          "and is done by a human with `histmersion restore <file> --force`."
      )
      .input[BackupInput]
      .handle { _ =>
        h.backup() match
          case Right(r) => ToolResult.text(Markdown.backupStatus(r))
          case Left(e)  => McpOut.error(e)
      }

  /** VACUUM (#1445): after a re-seed, a gazetteer load or a place clean-up the place tables hold hundreds of thousands
    * of dead row versions; an agent that just ran one can reclaim them.
    */
  private val vacuumTool =
    tool("vacuum")
      .description(
        "Run PostgreSQL VACUUM — with `analyze: true`, VACUUM (ANALYZE), which also refreshes the planner's " +
          "statistics — over the named `tables`, or over the gazetteer and finding tables a re-seed rewrites " +
          "(config maintenance.vacuum-tables) when none is named. Use it after `places seed`, a gazetteer " +
          "load or a place clean-up (`lint --fix` on the place rules), which leave dead row versions behind. " +
          "It runs as a server job, one table at a time, outside any transaction (no data changes, no table " +
          "lock beyond VACUUM's own); it answers with the job — follow it with `job_status`."
      )
      .input[VacuumRequest]
      .handle(in => h.vacuum(in).fold(McpOut.error, j => ToolResult.text(IngestMarkdown.job(j))))

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  // Shared preamble: the ONE place the writing rules are named, so all four tools point an agent at
  // the same document instead of paraphrasing it differently.
  private val ArticleRules =
    "Articles are the COMPILED KNOWLEDGE of this corpus — the concept/myth/culture/region/timeline/" +
      "report pages a reader reads instead of the papers. Write them by the rules in " +
      "docs/writing-guide.md: exhaust the filter (every source it returned is used or explicitly " +
      "excluded), surface disagreements with years, EVERY PARAGRAPH CITES at least one source as " +
      "[[citekey]] (a paragraph with no citekey is deleted, not rewritten), plain technical language " +
      "with no filler, specialist terms defined in one clause on first use, and the sections " +
      "question / what the sources say / disagreements / what is unresolved. Record the article's FILTER " +
      "(`filter`) and a REASON for each of its sources the body does not cite (`reasons`): the page then " +
      "generates the Not used list, its counts and the Method note — never write those by hand."
  private val LinkRules =
    "Links are Obsidian wikilinks in the body: [[citekey]] cites a source (it renders as a link to " +
      "that source and appears in the article's 'Sources cited' footer), [[other_slug]] points at " +
      "another article (and shows up in its backlinks). Nothing else maintains the link graph."
  private val FormatRules =
    "The Markdown is CommonMark plus pipe tables (a blank line before a table, a `|---|` row with one " +
      "cell per column); every write answers with a 'Formatting' list of what will not display as " +
      "written (line, column, excerpt) — fix the errors; `strict: true` refuses a write that has one."
  private val PatchFirstArticle =
    "To extend or correct an article that already exists, use patch_article — with `append` to add a " +
      "section, or `body` to rewrite it. NEVER re-upload a whole article with put_article: that " +
      "erases anything you did not resend."

  private val listArticles =
    tool("list_articles")
      .description(
        "List/search the compiled articles. `kind` filters to concept|myth|culture|region|timeline|" +
          "report, `tag` to the articles citing a source with that tag, `title` to a title " +
          "substring, `query` is full text over the article's title, summary and body (websearch " +
          "syntax: \"phrase\", -not, or) plus a title substring. Start here before compiling " +
          "anything: an article on the topic may already exist, in which case you extend it with " +
          s"patch_article rather than starting a new one. $ArticleRules"
      )
      .input[ListArticlesInput]
      .handle(in => ToolResult.text(ArticleMarkdown.list(h.listArticles(in.toQuery))))

  private val getArticle =
    tool("get_article")
      .description(
        "Fetch one compiled article by slug. `parts` (default: all) selects which sections to " +
          "return: body (the Markdown prose), summary (the one-paragraph lede), cited (sources it " +
          "cites with title/year/status — teasers truncated to 200 chars, markdown stripped), " +
          "backlinks (other articles and source notes linking TO it — same teaser format). " +
          "Pass a comma-separated list to get several parts (e.g. `parts: \"body,cited\"`). `coverage` is the " +
          "generated Not used list and Method note (from the article's recorded filter; see article_coverage). " +
          "Kinds: concept, myth, culture, region, timeline, report, index (the wiki's MOC pages). " +
          s"$LinkRules Read the article before patching it. $PatchFirstArticle"
      )
      .input[GetArticleInput]
      .handle { in =>
        h.getArticle(in.slug) match
          case Right(v) =>
            val parts = in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)
            ToolResult.text(ArticleMarkdown.article(v, parts))
          case Left(e) => ToolResult.text(s"_No article '${in.slug}' — find a slug with list_articles._")
      }

  private val putArticle =
    tool("put_article")
      .description(
        "Create or REPLACE a whole article under one slug (lower-case, underscores: sea_peoples). " +
          "Idempotent and destructive — everything you omit is erased — so use it to CREATE an " +
          "article or to deliberately rewrite one from scratch. `kind` is concept|myth|culture|" +
          "region|timeline|report, `body` is the Markdown (open it with a '# Title' heading), " +
          "`summary` is the one-paragraph lede (omit it and the first paragraph is used), `period` " +
          "is signed years, BCE negative. " +
          s"$ArticleRules $LinkRules $FormatRules $PatchFirstArticle"
      )
      .input[PutArticleInput]
      .handle { in =>
        h.putArticle(in.slug, in.article, in.strict) match
          case Right(v) =>
            ToolResult.text(
              Aliases.note(AliasKindsAdapter.ArticleRecords, in.slug.toLowerCase, v.article.slug)(
                ArticleMarkdown.article(v)
              )
            )
          case Left(e) => McpOut.error(e)
      }

  private val patchArticle =
    tool("patch_article")
      .description(
        "Extend or correct an existing article: send ONLY what changes, everything else is left " +
          "exactly as it is. `append` adds Markdown — at the end by default, or under a named " +
          "`section` heading (e.g. `section:\"## Disagreements\"`) so the new content lands in the " +
          "right place without a full body rewrite. `body` rewrites the prose, `title`, `summary`, " +
          "`kind`, `regions`, `period{start,end}` and `tags{add,remove,replace}` touch just " +
          "themselves. `filter` replaces the filter the article is compiled from ({} clears it); `reasons` " +
          "[{citekey, reason}] sets why each named source of that filter is left out (an empty reason removes " +
          "one) — the page generates the Not used list and the Method note from the two. `critiqueReasons` " +
          "[{src, dst, reason}] sets why one disagreement of the critique checklist (an edge src→dst of kind " +
          "challenges/refutes/updates/superseded_by touching a cited source) does not bear on the article (empty removes; " +
          "stored beside the article, it leaves updatedAt as it is). Pass ifUpdatedAt with the updatedAt you last read to be told — instead of " +
          s"silently clobbering — when someone else changed the article meanwhile. $ArticleRules " +
          s"$LinkRules $FormatRules"
      )
      .input[PatchArticleInput]
      .handle { in =>
        h.patchArticle(in.slug, in.patch, in.strict) match
          case Right(v) =>
            ToolResult.text(
              Aliases.note(AliasKindsAdapter.ArticleRecords, in.slug.toLowerCase, v.article.slug)(
                ArticleMarkdown.article(v)
              )
            )
          case Left(e) => McpOut.error(e)
      }

  private val checkEdits =
    tool("check_edits")
      .description(McpTools.CheckEditsDoc)
      .input[CheckEditsInput]
      .handle(in => ToolResult.text(McpTools.checkEdits(h, in)))

  private val articleCoverage =
    tool("article_coverage")
      .description(McpTools.ArticleCoverageDoc)
      .input[ArticleCoverageInput]
      .handle(in => ToolResult.text(McpTools.articleCoverageMarkdown(h, in)))

  private val articleReview =
    tool("article_review")
      .description(McpTools.ArticleReviewDoc)
      .input[ArticleReviewInput]
      .handle(in => ToolResult.text(McpTools.articleReviewMarkdown(h, in)))

  private val exportArticle =
    tool("export_article")
      .description(
        "Render an article as the Markdown note it would be on disk at wiki/<kind>/<slug>.md: YAML " +
          "frontmatter (type, slug, title, summary, regions, tags, period_start/period_end, and the filter " +
          "and reasons it is compiled from) plus the body, then the generated Not used and Method sections " +
          "between markers an import drops. This is the git-friendly mirror of the database and it round-trips back in through " +
          "seed."
      )
      .input[ExportArticleInput]
      .handle { in =>
        h.exportArticle(in.slug) match
          case Right(text) => ToolResult.text(text)
          case Left(e)     => McpOut.error(e)
      }

  private val articleVersions =
    tool("article_versions")
      .description(
        "List the edit history of one article (newest first): version id, date, word count and the " +
          "first 200 chars of the summary at that point. Use it to see how an article changed over " +
          "time, or to find the version id of a body you want to recover with `article_version`."
      )
      .input[ArticleVersionsInput]
      .handle { in =>
        h.articleVersions(in.slug) match
          case Right(vs) => ToolResult.text(ArticleMarkdown.versions(in.slug, vs))
          case Left(e)   => McpOut.error(e)
      }

  private val articleVersion =
    tool("article_version")
      .description(
        "Fetch the full body of one historical version of an article. Use it to read what the article " +
          "said before a rewrite, or to recover text that a bad patch overwrote."
      )
      .input[ArticleVersionInput]
      .handle { in =>
        h.articleVersion(in.slug, in.versionId) match
          case Right(v) => ToolResult.text(ArticleMarkdown.version(v))
          case Left(e)  => McpOut.error(e)
      }

  // DELETE /api/articles/{slug} is deliberately NOT an MCP tool: dropping compiled knowledge is a
  // human decision, made with `histmersion article delete`, not something a model should reach for while
  // tidying. Every other article operation is on both surfaces.
  // POST /api/sources/{ck}/delete (#969) is deliberately NOT an MCP tool either, for the same reason:
  // removing a source, its findings and its stored original is a person's decision, made with
  // `histmersion delete <citekey> --apply` after reading the dry run.
  private val goalsToolRead =
    tool("goals")
      .description(
        "Research GOALS: the project's research programs and topics. Without `slug` it LISTS goals " +
          "filtered by status (planned|seeding|compiled|reviewed|done); with `slug` it SHOWS one goal with " +
          "its sub-goals, links (sources, articles, queries, queue items), schedules and approved plan."
      )
      .input[GoalsInput]
      .handle { in =>
        in.slug.map(_.trim).filter(_.nonEmpty) match
          case Some(slug) =>
            h.agents.goalView(slug) match
              case Right(v) => ToolResult.text(AgentsMarkdown.goalView(v))
              case Left(e)  => McpOut.error(e)
          case None =>
            val q = Api.GoalQuery(in.status, in.limit, in.offset)
            ToolResult.text(Markdown.goalList(h.listGoals(q)))
      }

  private val putGoalTool =
    tool("put_goal")
      .description(
        "Create or replace a research goal. A goal tracks a research program: slug (kebab-case id), " +
          "title, status (planned|seeding|compiled|reviewed|done), notes (progress), tags, regions, " +
          "period."
      )
      .input[GoalPutInput]
      .handle { in =>
        val put =
          GoalPut(in.title, in.status, in.notes, in.tags, in.regions, in.periodStart, in.periodEnd, in.parentSlug)
        h.putGoal(in.slug, put) match
          case Right(g) =>
            ToolResult.text(Aliases.note(AliasKindsAdapter.GoalRecords, in.slug, g.slug)(Markdown.goal(g)))
          case Left(e) => McpOut.error(e)
      }

  private val patchGoalTool =
    tool("patch_goal")
      .description("Update specific fields of a goal without replacing the whole row.")
      .input[GoalPatchInput]
      .handle { in =>
        val p = GoalPatch(
          in.title,
          in.status,
          in.notes,
          in.tags,
          in.regions,
          in.periodStart,
          in.periodEnd,
          in.parentSlug,
          in.budgetUsd
        )
        h.patchGoal(in.slug, p) match
          case Right(g) =>
            ToolResult.text(Aliases.note(AliasKindsAdapter.GoalRecords, in.slug, g.slug)(Markdown.goal(g)))
          case Left(e) => McpOut.error(e)
      }

  private val agentStateTool =
    tool("agent_state")
      .description(
        "Agent STATE: key-value JSONB store that survives across sessions. Without `key` it LISTS " +
          "all keys; with `key` it SHOWS one entry; with `key` + `value` it SETS (upserts)."
      )
      .input[AgentStateInput]
      .handle { in =>
        in.key.map(_.trim).filter(_.nonEmpty) match
          case Some(key) =>
            in.value.map(_.trim).filter(_.nonEmpty) match
              case Some(v) =>
                h.agentStateSet(key, AgentStatePut(v)) match
                  case Right(e) => ToolResult.text(Markdown.agentState(e))
                  case Left(e)  => McpOut.error(e)
              case None =>
                h.agentStateGet(key) match
                  case Right(e) => ToolResult.text(Markdown.agentState(e))
                  case Left(e)  => McpOut.error(e)
          case None =>
            ToolResult.text(Markdown.agentStateList(h.agentStateList()))
      }

  private val deleteAgentStateTool =
    tool("delete_agent_state")
      .description("Delete an agent state key.")
      .input[DeleteAgentStateInput]
      .handle { in =>
        h.agentStateDelete(in.key) match
          case Right(d) => ToolResult.text(s"Deleted agent state key '${d.key}'.")
          case Left(e)  => McpOut.error(e)
      }

  private val mergePlanTool =
    tool("merge_plan")
      .description(
        "Plan the import of another Histmersion collection's bundle into this one (read-only: it " +
          "decides nothing and writes nothing). Lists EVERY issue in one run — two citekeys for one " +
          "work, one citekey for two works, differing DOIs/years/statuses, records changed on both " +
          "sides, articles that diverged — each with a stable id (`S-3c1e7a2b`), the evidence, and the " +
          "decisions it allows (left = this collection, right = the bundle). `bundle` is the sha256 of " +
          "a bundle uploaded to the server (the user runs `histmersion merge plan <file>`, which " +
          "uploads it and prints the sha256). `resolve` maps issue ids to decisions " +
          "(`keep-left`, `keep-right`, `both`, `map:<citekey>`, …) to see what would remain; `editions` " +
          "is merge|separate. Present the plan to the user; scholarly decisions (statuses, editions, " +
          "articles) are theirs — never resolve them on their behalf."
      )
      .input[MergePlanInput]
      .handle { in =>
        val editions = in.editions.map(_.trim).filter(_.nonEmpty)
        editions.map(e => EditionsPolicy.values.find(_.toString.equalsIgnoreCase(e)).toRight(e)) match
          case Some(Left(e)) => ToolResult.text(s"_editions '$e' is not merge or separate_")
          case other         =>
            val res = MergeResolutions(
              in.resolve.toList.sortBy(_._1).map((k, v) => MergeResolution(k, v)),
              MergePolicy(other.flatMap(_.toOption))
            )
            h.bundles.plan(MergePlanRequest(in.bundle, res)) match
              case Right(p) => ToolResult.text(MergeMarkdown.plan(p, in.all))
              case Left(e)  => McpOut.error(e)
      }

  private val mergesTool =
    tool("merges")
      .description(
        "What this collection took in from other Histmersion collections (read-only). `action`: " +
          "`list` (default) — the merges, newest first, with the bundle, where it came from, the plan " +
          "and the backup taken; `show` with `id` (a merge id or its first characters) — one merge's " +
          "report (sources merged, new, renamed, articles merged three-way…), its resolutions and the " +
          "decisions it remembers; `ledger` — the merge ledger of one record (`uid`: a uid, here or " +
          "merged away, or a citekey) or of one merge (`id`): which record of the other collection " +
          "each one here came from, and the redirects of merged-away uids. Applying a merge is the " +
          "user's (`histmersion merge apply`, or the /merge screen) — never an agent's."
      )
      .input[MergesInput]
      .handle { in =>
        MergesAction.parse(in.action) match
          case None => ToolResult.text(s"_action '${in.action.getOrElse("")}' is not list, show or ledger_")
          case Some(MergesAction.List) => ToolResult.text(MergeMarkdown.merges(h.bundles.merges(in.limit)))
          case Some(MergesAction.Show) =>
            in.id.fold(ToolResult.text("_show needs `id` (a merge id from `list`)_"))(id =>
              h.bundles.mergeDetail(id).fold(McpOut.error, d => ToolResult.text(MergeMarkdown.detail(d)))
            )
          case Some(MergesAction.Ledger) =>
            h.bundles
              .mergeLedger(in.uid, in.id, in.limit)
              .fold(McpOut.error, l => ToolResult.text(MergeMarkdown.ledger(l)))
      }

  private val renameLabel =
    tool("rename_label")
      .description(
        "Give an article a new slug, an event a new id, a goal a new slug, or a gazetteer place a new " +
          "id (`kind`: article | event | goal | place). One transaction: what points at it follows (an " +
          "article's versions, links and every [[old]] in other articles — each rewrite recorded as a " +
          "version; an event's attestations and asset links; a goal's sub-goals, links and schedules; a " +
          "place's names, parents, footprints, events, attestations and finding assignments). " +
          s"${Aliases.Rule} (get_article / events / goals / places show the record under its new one, a " +
          "places filter on the old id still matches.) Refused when the new label " +
          "is taken or is an old label of another record. For a source's citekey use rename_source."
      )
      .input[RenameLabelInput]
      .handle { in =>
        val req = LabelRename(in.to)
        val r = in.kind.trim.toLowerCase(java.util.Locale.ROOT) match
          case "article" => Right(h.renameArticle(in.from, req))
          case "event"   => Right(h.renameEvent(in.from, req))
          case "goal"    => Right(h.renameGoal(in.from, req))
          case "place"   => Right(h.renamePlace(in.from, req))
          case other     => Left(s"_kind '$other' is not article, event, goal or place_")
        r.fold(ToolResult.text, _.fold(McpOut.error, x => ToolResult.text(MergeMarkdown.renamed(x))))
      }

  // Declared LAST: every tool above must be initialised before the list is built.
  val server = McpServer(tools =
    List(
      ping
    ) ++ com.histmersion.domain.library.LibraryTools(
      h.library.search,
      h.library.labels,
      h.library.sources,
      h.library.findings,
      h.library.citations,
      h.exportSource
    ) ++ List(
      backup,
      configTool,
      vacuumTool,
      listArticles,
      getArticle,
      putArticle,
      patchArticle,
      checkEdits,
      articleCoverage,
      articleReview,
      exportArticle,
      articleVersions,
      articleVersion
    ) ++ com.histmersion.domain.catalogue.places.PlacesTools(h.places) ++
      com.histmersion.domain.catalogue.periods.PeriodsTools(h.periods) ++
      com.histmersion.domain.catalogue.events.EventsTools(h.events) ++
      com.histmersion.domain.catalogue.evidence.AttestationsTools(h.attestations) ++ List(
        goalsToolRead,
        putGoalTool,
        patchGoalTool,
        agentStateTool,
        deleteAgentStateTool,
        ingest,
        jobStatus,
        jobControlTool,
        activityTool
      ) ++ com.histmersion.domain.media.MediaTools(
        h.media.assets,
        h.media.links,
        ck => h.library.sources.get(ck).isRight
      ) ++ List(
        mergePlanTool,
        mergesTool,
        renameLabel
      )
      ++ OpsTools(h.ops, h) // stream 2.8: lint · queue · query
      ++ com.histmersion.domain.personal.PersonalTools(h.personal) // the person's bookmarks and notes, read only
      ++ com.histmersion.domain.catalogue.evidence.EventLinksTools(h.eventLinks) // event_links (proposed links, leads)
      ++ com.histmersion.domain.catalogue.events.CorrectionsTools(h.eventImports) // event_corrections
      ++ com.histmersion.domain.automation.agents.AgentsTools( // embedded agents: start and follow runs
        h.agents,
        dry =>
          Right(
            RepairAcquire.markdown(
              com.histmersion.server.agents.TransitionWorkflows
                .work(h.agents)
                .oaBacklog
                .repair(RepairAcquire.RepairRequest(dry, Some("mcp")))
            )
          )
      )
      // stream 2.6: discover / refs / assess / followups / ingest_doi / archive
      ++ com.histmersion.server.acquire.AcquireMcp(acquire, collections).tools
      ++ com.histmersion.domain.library.collections.CollectionsTools(collections) // the Calibre library, archive.org
  )

  val endpoint = server.endpoint(List("mcp"))

  /** Every tool `/mcp` advertises — what a `{{tool:<name>}}` placeholder in a definition may name. */
  def toolNames: Set[String] = server.tools.map(_.name).toSet

object McpTools:
  export com.histmersion.util.mcp.ToolInputs.given

  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  final case class PingInput() derives KindlingsCodecAsObject, KindlingsSchema
  object PingInput:
    given Schema[PingInput] = summon[KindlingsSchema[PingInput]].schema

  /** The `config` tool's input: what to do with the server's configuration. */
  final case class ConfigInput(
      action: String,
      key: Option[String] = None,
      value: Option[String] = None,
      prefix: Option[String] = None,
      literalConsent: Option[Boolean] = None
  ) derives KindlingsCodecAsObject
  object ConfigInput:
    given Schema[ConfigInput] = KindlingsSchema
      .derived[ConfigInput]
      .schema
      .modify(_.action)(_.description("get | set | unset | reload | restart"))
      .modify(_.key)(_.description("the setting, e.g. backups.keep (get, set, unset)"))
      .modify(_.value)(_.description("set: the value as a person types it — text, a number, true/false, a list [a, b]"))
      .modify(_.prefix)(_.description("get: the settings under this block, e.g. acquire"))
      .modify(_.literalConsent)(
        _.description("set: a credential may be written as value:… (the key itself in the file)")
      )

  final case class BackupInput() derives KindlingsCodecAsObject, KindlingsSchema
  object BackupInput:
    given Schema[BackupInput] = summon[KindlingsSchema[BackupInput]].schema
  // ── article tool inputs (plan §5.2 stream 2.7). The write tools reuse the REST DTOs verbatim, so
  //    the MCP schema and the HTTP body are the same shape — one contract, two transports.
  final case class ListArticlesInput(
      kind: Option[String] = None,
      query: Option[String] = None,
      tag: Option[String] = None,
      title: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    /** `tag` is the shared Filter's (a source tag: articles CITING such a source); `query` is the article's own full
      * text — see [[Api.ArticleQuery]].
      */
    def toQuery: Api.ArticleQuery =
      Api.ArticleQuery(Filter.of(tags = tag.toList, limit = limit, offset = offset, title = title), kind, query)
  object ListArticlesInput:
    given Schema[ListArticlesInput] = KindlingsSchema
      .derived[ListArticlesInput]
      .schema
      .modify(_.kind)(_.description("Article kind: concept|myth|culture|region|timeline|report|index"))
      .modify(_.query)(_.description("Full-text search over title, summary and body"))
      .modify(_.tag)(_.description("Only articles citing a source that carries this tag"))
      .modify(_.title)(_.description("Case-insensitive substring of the article's title"))

  final case class GetArticleInput(slug: String, parts: Option[String] = None) derives KindlingsCodecAsObject
  object GetArticleInput:
    given Schema[GetArticleInput] = KindlingsSchema
      .derived[GetArticleInput]
      .schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))
      .modify(_.parts)(
        _.description("Comma-separated parts to return: body,summary,coverage,cited,backlinks (default: all)")
      )

  /** `article_coverage` as `/mcp` and an agent step both describe it. */
  val ArticleCoverageDoc: String =
    "The coverage of one article, computed by code from the filter it records: how many sources the filter " +
      "returns, which of them the body cites, every other one with its stored reason (or 'not yet assessed'), " +
      "the status breakdown, the disagreement edges (challenges, refutes, updates, superseded_by) touching the " +
      "cited sources, the further searches, and the DRIFT — sources neither cited nor given a reason, reasons " +
      "that went stale; the CRITIQUE CHECKLIST (Disagreements checked: each edge touching a cited source, addressed " +
      "when both sources are cited in one paragraph, reasoned when a critique reason is stored, else open). Then " +
      "the Not used list and Method note exactly as the page generates them. An article " +
      "that records no filter has none (set it with patch_article `filter`)."

  /** `article_coverage`: the one handler, rendered. */
  def articleCoverageMarkdown(h: Handlers, in: ArticleCoverageInput): String =
    h.articleCoverage(in.slug).fold(McpOut.refusal, ArticleCoverage.markdown)

  /** `article_review` as `/mcp` describes it. */
  val ArticleReviewDoc: String =
    "Where one article stands with the review panel (rules reviewer, cold reader, skeptic, mechanical checks): " +
      "its state (unreviewed, queued, in-review with the round, passed, majors-open, stopped) with what it means, " +
      "the review manager's last outcome, the research goals that link the article with their stage, the approvals " +
      "about it that wait for a person, and its review history from the review ledger — every finding by run and " +
      "round, with reviewer, kind, place, problem and what became of it (fixed, rejected with the record's sentence, " +
      "won't fix, still open). CLI: histmersion article review."

  /** `article_review`: the one handler, rendered. */
  def articleReviewMarkdown(h: Handlers, in: ArticleReviewInput): String =
    h.articleReview(in.slug).fold(McpOut.refusal, ArticleReviewView.markdown)

  final case class ArticleReviewInput(slug: String) derives KindlingsCodecAsObject
  object ArticleReviewInput:
    given Schema[ArticleReviewInput] = KindlingsSchema
      .derived[ArticleReviewInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))

  final case class ArticleCoverageInput(slug: String) derives KindlingsCodecAsObject
  object ArticleCoverageInput:
    given Schema[ArticleCoverageInput] = KindlingsSchema
      .derived[ArticleCoverageInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))

  /** `check_edits` as `/mcp` and an agent step both describe it. */
  val CheckEditsDoc: String =
    "Dry-run a change to an article and see what the review checks would say — nothing is written. `edits` " +
      "are applied in order to the CURRENT stored body: each is {op: replace|insertAfter|insertBefore|delete, " +
      "anchor: text copied exactly from the body (it must occur once; whitespace is normalised), text}. Or pass " +
      "`body`, a whole draft, instead (the article need not exist yet). The answer: each edit's fate (applies, " +
      "or why not), then the problems of the result — links to nothing, uncited paragraphs, a Sources cited " +
      "heading, banned filler, formatting errors it introduces — and whether one is fatal. These are exactly the " +
      "checks code runs on a review fixer's answer."

  /** `check_edits`: the one handler, rendered. */
  def checkEdits(h: Handlers, in: CheckEditsInput): String =
    h.checkArticle(in.slug, ArticleEdits.ArticleCheckRequest(in.edits, in.body, in.kind))
      .fold(McpOut.refusal, ArticleEdits.markdown)

  final case class CheckEditsInput(
      slug: String,
      edits: List[ArticleEdits.EditAnswer] = Nil,
      body: Option[String] = None,
      kind: Option[String] = None
  ) derives KindlingsCodecAsObject
  object CheckEditsInput:
    given Schema[CheckEditsInput] = KindlingsSchema
      .derived[CheckEditsInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))
      .modify(_.edits)(_.description("Edits to the current stored body, applied in order"))
      .modify(_.body)(_.description("Instead of edits: a whole draft body"))
      .modify(_.kind)(
        _.description(
          "Check the section set of this kind instead of the article's (index = none, for a fragment to be appended)"
        )
      )

  final case class PutArticleInput(slug: String, article: ArticlePut, strict: Boolean = false)
  object PutArticleInput:
    given Schema[PutArticleInput] = KindlingsSchema
      .derived[PutArticleInput]
      .schema
      .modify(_.strict)(_.description(com.histmersion.domain.library.LibraryTools.StrictDoc))
    given Codec.AsObject[PutArticleInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PutArticleInput]) // #813

  final case class PatchArticleInput(slug: String, patch: ArticlePatch, strict: Boolean = false)
  object PatchArticleInput:
    given Schema[PatchArticleInput] = KindlingsSchema
      .derived[PatchArticleInput]
      .schema
      .modify(_.strict)(_.description(com.histmersion.domain.library.LibraryTools.StrictDoc))
    given Codec.AsObject[PatchArticleInput] =
      StrictJson.codec(KindlingsCodecAsObject.derived[PatchArticleInput]) // #813

  final case class ExportArticleInput(slug: String) derives KindlingsCodecAsObject, KindlingsSchema
  object ExportArticleInput:
    given Schema[ExportArticleInput] = summon[KindlingsSchema[ExportArticleInput]].schema

  final case class ArticleVersionsInput(slug: String) derives KindlingsCodecAsObject
  object ArticleVersionsInput:
    given Schema[ArticleVersionsInput] = KindlingsSchema
      .derived[ArticleVersionsInput]
      .schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))

  final case class ArticleVersionInput(slug: String, versionId: Long) derives KindlingsCodecAsObject
  object ArticleVersionInput:
    given Schema[ArticleVersionInput] = KindlingsSchema
      .derived[ArticleVersionInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))
      .modify(_.versionId)(_.description("The version id from article_versions"))

  // ── ingest / jobs / assets (plan §1.8). Declared LAST, for the same reason `PlacesInput` was. ──
  final case class IngestInput(
      citekey: String,
      path: Option[String] = None,
      base64: Option[String] = None,
      filename: Option[String] = None,
      quality: Boolean = false,
      force: Boolean = false,
      title: Option[String] = None,
      authors: List[String] = Nil,
      year: Option[Int] = None,
      doi: Option[String] = None,
      provenance: Option[String] = None,
      acquireItem: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object IngestInput:
    given Schema[IngestInput] = KindlingsSchema
      .derived[IngestInput]
      .schema
      .modify(_.citekey)(_.description("Citekey to store the source under"))
      .modify(_.path)(_.description("Server-side file path (alternative to base64)"))
      .modify(_.base64)(_.description("Base64-encoded document (alternative to path)"))
      .modify(_.quality)(_.description("Use the slow, accurate OCR path (docling)"))
      .modify(_.force)(_.description("Accept a file already stored under another citekey"))
      .modify(_.provenance)(
        _.description("How the source was acquired: manual|ill|author|scan|external-file|calibre|archive|oa")
      )
      .modify(_.acquireItem)(
        _.description("Id of the acquisition-queue item this document fulfils; ticked off when the OCR job succeeds")
      )

  final case class JobControlInput(id: String, action: String) derives KindlingsCodecAsObject, KindlingsSchema
  object JobControlInput:
    given Schema[JobControlInput] = summon[KindlingsSchema[JobControlInput]].schema

  final case class ActivityInput(
      show: Option[String] = None,
      since: Option[String] = None,
      goal: Option[String] = None,
      limit: Option[Int] = None,
      before: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ActivityInput:
    given Schema[ActivityInput] = summon[KindlingsSchema[ActivityInput]].schema

  final case class JobStatusInput(
      id: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      before: Option[String] = None,
      kind: List[String] = Nil,
      since: Option[String] = None,
      until: Option[String] = None,
      subject: Option[String] = None
  ) derives KindlingsCodecAsObject:
    def filter: ListFilters.JobFilter = ListFilters.JobFilter(status.toList, kind, since, until, subject)
  object JobStatusInput:
    given Schema[JobStatusInput] = KindlingsSchema
      .derived[JobStatusInput]
      .schema
      .modify(_.id)(_.description("Job id to check (omit for a listing)"))
      .modify(_.status)(_.description("Filter listing: queued|running|done|failed (comma-separated for several)"))
      .modify(_.kind)(
        _.description("Filter listing: these job kinds (ingest, reocr, agent, media-import, events-import, init, …)")
      )
      .modify(_.since)(
        _.description("Filter listing: started at or after 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
      )
      .modify(_.until)(_.description("Filter listing: started before the end of 2026-10-01, or an ISO instant"))
      .modify(_.subject)(
        _.description("Filter listing: text in what the job is about (citekey, target, detail) or the start of its id")
      )
      .modify(_.before)(_.description("Listing: the next older page — the id of the last job shown"))

  final case class GoalsInput(
      slug: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object GoalsInput:
    given Schema[GoalsInput] = KindlingsSchema
      .derived[GoalsInput]
      .schema
      .modify(_.slug)(_.description("Goal slug to look up directly (omit for listing)"))
      .modify(_.status)(_.description("Filter: planned|seeding|compiled|reviewed|done"))

  final case class GoalPutInput(
      slug: String,
      title: String,
      status: String = "planned",
      notes: Option[String] = None,
      tags: List[String] = Nil,
      regions: List[String] = Nil,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None
  )
  object GoalPutInput:
    given Schema[GoalPutInput] = KindlingsSchema
      .derived[GoalPutInput]
      .schema
      .modify(_.slug)(_.description("Kebab-case identifier for the goal"))
      .modify(_.status)(_.description("planned|seeding|compiled|reviewed|done"))
      .modify(_.periodStart)(_.description("Signed year the goal's period begins (BCE negative); alone = from then on"))
      .modify(_.periodEnd)(_.description("Signed year the goal's period ends (BCE negative); alone = until then"))
    given Codec.AsObject[GoalPutInput] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPutInput]) // #813

  final case class GoalPatchInput(
      slug: String,
      title: Option[String] = None,
      status: Option[String] = None,
      notes: Option[String] = None,
      tags: Option[List[String]] = None,
      regions: Option[List[String]] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None,
      budgetUsd: Option[Double] = None
  )
  object GoalPatchInput:
    given Schema[GoalPatchInput] = KindlingsSchema
      .derived[GoalPatchInput]
      .schema
      .modify(_.slug)(_.description("Goal slug to patch"))
      .modify(_.periodStart)(
        _.description("New start of the period (signed year, BCE negative); given alone, the end stays as stored")
      )
      .modify(_.periodEnd)(
        _.description("New end of the period (signed year, BCE negative); given alone, the start stays as stored")
      )
      .modify(_.budgetUsd)(_.description("Cost cap (USD) of the agent runs started for this goal; 0 clears it"))
    given Codec.AsObject[GoalPatchInput] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatchInput]) // #813

  final case class AgentStateInput(
      key: Option[String] = None,
      value: Option[String] = None
  ) derives KindlingsCodecAsObject
  object AgentStateInput:
    given Schema[AgentStateInput] = KindlingsSchema
      .derived[AgentStateInput]
      .schema
      .modify(_.key)(_.description("State key to get or set (omit for listing all keys)"))
      .modify(_.value)(_.description("JSON string value to set (omit to just read the key)"))

  final case class DeleteAgentStateInput(key: String) derives KindlingsCodecAsObject
  object DeleteAgentStateInput:
    given Schema[DeleteAgentStateInput] = KindlingsSchema
      .derived[DeleteAgentStateInput]
      .schema
      .modify(_.key)(_.description("State key to delete"))

  /** Input of the `merge_plan` tool (docs/research-merge-import.md §6, M3). */
  final case class MergePlanInput(
      bundle: String,
      resolve: Map[String, String] = Map.empty,
      editions: Option[String] = None,
      all: Boolean = false
  )
  object MergePlanInput:
    given Schema[MergePlanInput] = KindlingsSchema
      .derived[MergePlanInput]
      .schema
      .modify(_.bundle)(
        _.description("sha256 of a bundle uploaded to the server (`histmersion merge plan <file>` prints it)")
      )
      .modify(_.resolve)(_.description("Issue id → decision, e.g. {\"S-3c1e7a2b\": \"map:guterbock1997_hittite\"}"))
      .modify(_.editions)(_.description("merge | separate: answers every year-gap (edition) question at once"))
      .modify(_.all)(_.description("List every default, info line and match, not only the first few"))
    given Codec.AsObject[MergePlanInput] = StrictJson.codec(KindlingsCodecAsObject.derived[MergePlanInput])

  /** What the `merges` tool does. */
  enum MergesAction:
    case List, Show, Ledger
  object MergesAction:
    def parse(raw: Option[String]): Option[MergesAction] =
      raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None    => Some(List)
        case Some(a) => values.find(_.toString.equalsIgnoreCase(a))

  /** Input of the `merges` tool (docs/research-merge-import.md §6.1, M7). */
  final case class MergesInput(
      action: Option[String] = None,
      id: Option[String] = None,
      uid: Option[String] = None,
      limit: Option[Int] = None
  )
  object MergesInput:
    given Schema[MergesInput] = KindlingsSchema
      .derived[MergesInput]
      .schema
      .modify(_.action)(_.description("list (default) | show | ledger"))
      .modify(_.id)(_.description("A merge id, or its first characters (show; ledger of one merge)"))
      .modify(_.uid)(_.description("ledger: the record — a uid (here, or merged away) or a citekey"))
      .modify(_.limit)(_.description("How many merges / ledger rows (default 50 / 200)"))
    given Codec.AsObject[MergesInput] = StrictJson.codec(KindlingsCodecAsObject.derived[MergesInput])

  /** Input of the `rename_label` tool (V042, V053, V055). */
  final case class RenameLabelInput(kind: String, from: String, to: String)
  object RenameLabelInput:
    given Schema[RenameLabelInput] = KindlingsSchema
      .derived[RenameLabelInput]
      .schema
      .modify(_.kind)(_.description("article | event | goal | place"))
      .modify(_.from)(_.description("The current slug (article, goal) or id (event, place)"))
      .modify(_.to)(_.description("The new one: lower-case letters, digits, '_', '.' and '-'"))
    given Codec.AsObject[RenameLabelInput] = StrictJson.codec(KindlingsCodecAsObject.derived[RenameLabelInput])

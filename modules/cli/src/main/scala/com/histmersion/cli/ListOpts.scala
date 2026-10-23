package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.{JobState, ListFilters}
import com.histmersion.core.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}

/** The filter options of the three listings — `agent approvals`, `agent runs`, `jobs` — over core
  * [[ListFilters]]: the same names as the REST query (kebab-case here), every list option repeatable
  * or comma-separated, and the server checks the values. The "next page" line repeats them
  * ([[PageCmd]]). */
object ListOpts:

  private def many(name: String, help: String): Opts[List[String]] = Opts.options[String](name, s"$help (repeatable, or comma-separated)").orEmpty
  private def one(name: String, help: String): Opts[Option[String]] = Opts.option[String](name, help).orNone

  private val since = one("since", "Only those started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
  private val until = one("until", "Only those started before the end of: 2026-10-01, or an ISO instant")

  val approvals: Opts[ListFilters.ApprovalFilter] =
    (one("state", s"Only approvals in this state: ${(ApprovalState.values.map(_.wire) :+ "all").mkString("|")} (default open)"),
      many("kind", s"Only these kinds: ${ApprovalKind.values.map(_.wire).mkString("|")}"),
      one("subject", "Only those about something matching this text: a goal or article slug, a manager, a citekey"),
      one("goal", "Only one goal's: its plan, or proposed by a run for it"),
      many("decided-by", s"Only those decided by: ${ListFilters.DecidedBy.values.map(_.wire).mkString("|")}"))
      .mapN(ListFilters.ApprovalFilter.apply)

  val runs: Opts[ListFilters.RunFilter] =
    (many("status", s"Only runs in these states: ${RunState.values.map(_.wire).mkString("|")} (waiting = for an approval)"),
      many("pipeline", s"Only these pipelines: ${Pipeline.values.map(_.wire).mkString("|")}"),
      many("manager", "Only the runs these process managers dispatched (review, enrich, seed:<goal>, …)"),
      one("goal", "Only the runs for this goal"),
      many("model", "Only runs a step of which used this provider:model"),
      many("provider", "Only runs a step of which used this provider (codex, claude-code, …)"),
      many("origin", s"Only runs started by: ${Origin.values.map(_.wire).mkString("|")}"),
      since, until,
      one("subject", "Only runs whose target matches this text (a citekey, an article or goal slug)"))
      .mapN(ListFilters.RunFilter.apply)

  val jobs: Opts[ListFilters.JobFilter] =
    (many("status", s"Only jobs in these states: ${JobState.values.map(_.wire).mkString("|")} (an agent run's too: ${RunState.values.map(_.wire).filterNot(JobState.values.map(_.wire).contains).mkString("|")})"),
      many("kind", "Only these job kinds: ingest, reocr, agent, media-import, events-import, init, basemap-fetch, …"),
      since, until,
      one("subject", "Only jobs about something matching this text (citekey, target, detail), or whose id starts so"))
      .mapN(ListFilters.JobFilter.apply)

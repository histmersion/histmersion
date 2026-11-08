package com.histmersion
package cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.{Goal, ListFilters}
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}

/** The filter options of the three listings — `agent approvals`, `agent runs`, `jobs` — over core [[ListFilters]]: the
  * same names as the REST query (kebab-case here), every list option repeatable or comma-separated, and the server
  * checks the values. The "next page" line repeats them ([[PageCmd]]).
  */
object ListOpts:


  private def many(name: String, help: String): Opts[List[String]] =
    Opts.options[String](name, s"$help (repeatable, or comma-separated)").orEmpty
  private def one(name: String, help: String): Opts[Option[String]] = Opts.option[String](name, help).orNone

  private val since = one("since", "Only those started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
  private val until = one("until", "Only those started before the end of: 2026-10-01, or an ISO instant")

  val approvals: Opts[ListFilters.ApprovalFilter] =
    (
      one(
        "state",
        s"Only approvals in this state: ${(ApprovalState.values.map(_.wire.value) :+ "all").mkString("|")} (default open)"
      ),
      many("kind", s"Only these kinds: ${ApprovalKind.values.map(_.wire).mkString("|")}"),
      one("subject", "Only those about something matching this text: a goal or article slug, a manager, a citekey"),
      one("goal", "Only one goal's: its plan, or proposed by a run for it"),
      many("decided-by", s"Only those decided by: ${ListFilters.DecidedBy.values.map(_.wire).mkString("|")}")
    )
      .mapN { (state, kind, subject, goal, decidedBy) =>
        ListFilters.ApprovalFilter(
          state.map(ListFilters.ApprovalFilter.State(_)),
          kind.map(ListFilters.ApprovalFilter.Kind(_)),
          subject.map(ListFilters.ApprovalFilter.Subject(_)),
          goal.map(Goal.Slug(_)),
          decidedBy.map(ListFilters.ApprovalFilter.DecidedBy(_))
        )
      }

  val runs: Opts[ListFilters.RunFilter] =
    (
      many(
        "status",
        s"Only runs in these states: ${RunState.values.map(_.wire).mkString("|")} (waiting = for an approval)"
      ),
      many("pipeline", s"Only these pipelines: ${Pipeline.values.map(_.wire).mkString("|")}"),
      many("manager", "Only the runs these process managers dispatched (review, enrich, seed:<goal>, …)"),
      one("goal", "Only the runs for this goal"),
      many("model", "Only runs a step of which used this provider:model"),
      many("provider", "Only runs a step of which used this provider (codex, claude-code, …)"),
      many("origin", s"Only runs started by: ${Origin.values.map(_.wire).mkString("|")}"),
      since,
      until,
      one("subject", "Only runs whose target matches this text (a citekey, an article or goal slug)")
    )
      .mapN { (status, pipeline, manager, goal, model, provider, origin, since, until, subject) =>
        ListFilters.RunFilter(
          status.map(ListFilters.RunFilter.Status(_)),
          pipeline.map(ListFilters.RunFilter.Pipeline(_)),
          manager.map(ListFilters.RunFilter.Manager(_)),
          goal.map(Goal.Slug(_)),
          model.map(ListFilters.RunFilter.Model(_)),
          provider.map(ListFilters.RunFilter.Provider(_)),
          origin.map(ListFilters.RunFilter.Origin(_)),
          since.map(ListFilters.RunFilter.Since(_)),
          until.map(ListFilters.RunFilter.Until(_)),
          subject.map(ListFilters.RunFilter.Subject(_))
        )
      }

  val jobs: Opts[ListFilters.JobFilter] =
    (
      many(
        "status",
        s"Only jobs in these states: ${JobState.values.map(_.wire).mkString("|")} (an agent run's too: ${RunState.values.map(_.wire.value).filterNot(JobState.values.map(_.wire.value).contains).mkString("|")})"
      ),
      many("kind", "Only these job kinds: ingest, reocr, agent, media-import, events-import, init, basemap-fetch, …"),
      since,
      until,
      one("subject", "Only jobs about something matching this text (citekey, target, detail), or whose id starts so")
    )
      .mapN { (status, kind, since, until, subject) =>
        ListFilters.JobFilter(
          status.map(ListFilters.JobFilter.Status(_)),
          kind.map(ListFilters.JobFilter.Kind(_)),
          since.map(ListFilters.JobFilter.Since(_)),
          until.map(ListFilters.JobFilter.Until(_)),
          subject.map(ListFilters.JobFilter.Subject(_))
        )
      }

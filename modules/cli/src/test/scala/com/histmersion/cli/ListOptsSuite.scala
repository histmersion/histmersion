package com.histmersion
package cli

import com.histmersion.api.automation.ListFilters.{ApprovalFilter, JobFilter, RunFilter}
import com.monovore.decline.Command
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Agents
import com.histmersion.api.jobs.JobState
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.Paging

/** The filter options of `agent approvals`, `agent runs` and `jobs`: repeatable or comma-separated, parsed into the
  * core filters the request carries, and repeated by the next-page line.
  */
class ListOptsSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private def parse[A](opts: com.monovore.decline.Opts[A], args: String*): Either[String, A] =
    Command("t", "test")(opts).parse(args.toList, Map.empty).left.map(_.toString)

  test("approvals: state, kinds (repeated and comma-separated), subject, goal, decided-by"):
    assertEquals(
      parse(
        ListOpts.approvals,
        "--kind",
        "goal-plan,edge",
        "--kind",
        "article-publish",
        "--state",
        "all",
        "--subject",
        "tin",
        "--goal",
        "bronze-age",
        "--decided-by",
        "autopilot"
      ),
      Right(
        ApprovalFilter(
          Some(Approval.State.Wire("all")),
          List(Approval.Kind.Wire("goal-plan,edge"), Approval.Kind.Wire("article-publish")),
          Some(ApprovalFilter.Subject("tin")),
          Some(Goal.Slug("bronze-age")),
          List(Approval.DecidedBy.Wire("autopilot"))
        )
      )
    )
    assertEquals(parse(ListOpts.approvals), Right(ApprovalFilter()))
    // the server reads the comma-separated and repeated values as one list
    assertEquals(
      parse(ListOpts.approvals, "--kind", "goal-plan,edge").map(_.normalized.kind),
      Right(List(Approval.Kind.Wire("goal-plan"), Approval.Kind.Wire("edge")))
    )

  test("runs: every filter, and the dates as typed (the server reads them)"):
    assertEquals(
      parse(
        ListOpts.runs,
        "--pipeline",
        "review-article",
        "--status",
        "failed",
        "--status",
        "cancelled",
        "--manager",
        "seed:tin",
        "--provider",
        "codex",
        "--model",
        "codex:gpt-6",
        "--origin",
        "manager",
        "--goal",
        "g",
        "--since",
        "7d",
        "--until",
        "2026-10-01",
        "--subject",
        "a2020"
      ),
      Right(
        RunFilter(
          List(RunFilter.Status("failed"), RunFilter.Status("cancelled")),
          List(RunFilter.Pipeline("review-article")),
          List(RunFilter.Manager("seed:tin")),
          Some(Goal.Slug("g")),
          List(RunFilter.Model("codex:gpt-6")),
          List(RunFilter.Provider("codex")),
          List(Agents.Origin.Wire("manager")),
          Some(RunFilter.Since("7d")),
          Some(RunFilter.Until("2026-10-01")),
          Some(RunFilter.Subject("a2020"))
        )
      )
    )

  test("jobs: kinds, states, dates, subject"):
    assertEquals(
      parse(ListOpts.jobs, "--kind", "ingest", "--kind", "reocr", "--status", "failed", "--since", "2026-09-01"),
      Right(
        JobFilter(
          List(JobState.Wire("failed")),
          List(JobKinds.Wire("ingest"), JobKinds.Wire("reocr")),
          Some(JobFilter.Since("2026-09-01")),
          None,
          None
        )
      )
    )

  test("the commands take the filters, --limit, --before and --json; approve/reject take several ids"):
    val agent = Command("histmersion", "test")(AgentCmd.command)
    assert(
      agent.parse(List("agent", "approvals", "--kind", "goal-plan", "--state", "open", "--json"), Map.empty).isRight
    )
    assert(
      agent
        .parse(
          List("agent", "runs", "--pipeline", "review-article", "--status", "failed", "--limit", "5", "--json"),
          Map.empty
        )
        .isRight
    )
    assert(agent.parse(List("agent", "approve", "12", "13", "14", "--note", "checked"), Map.empty).isRight)
    assert(agent.parse(List("agent", "reject", "12"), Map.empty).isRight)
    assert(agent.parse(List("agent", "approve"), Map.empty).isLeft, "an id is needed")
    val jobs = Command("histmersion", "test")(IngestCmd.all)
    assert(
      jobs.parse(List("jobs", "--kind", "ingest", "--status", "failed", "--subject", "x", "--json"), Map.empty).isRight
    )
    assert(jobs.parse(List("jobs", "4f1c"), Map.empty).isRight)

  test("ingest takes --legal-only for an identifier"):
    assert(parse(AcquireCmd.ingest, "ingest", "10.1038/zz1", "--legal-only").isRight)
    assert(parse(AcquireCmd.ingest, "ingest", "10.1038/zz1", "--legal-only", "--refetch", "--json").isRight)
    assert(parse(AcquireCmd.ingest, "ingest", "10.1038/zz1", "--legal").isLeft)

  test("the next-page line repeats every filter, a list option once per value"):
    val f = ApprovalFilter(
      kind = List(Approval.Kind.Wire("goal-plan,edge")),
      decidedBy = List(Approval.DecidedBy.Wire("person"))
    )
    assertEquals(
      PageCmd.next(
        "agent approvals",
        f.options.map((k, v) => (k.value, v.map(_.value))) ++ List("limit" -> Some("20")),
        Paging.Cursor("41")
      ),
      "histmersion agent approvals --kind goal-plan --kind edge --decided-by person --limit 20 --before 41"
    )
    assertEquals(
      PageCmd.next(
        "agent runs",
        RunFilter(manager = List(RunFilter.Manager("seed:tin")), since = Some(RunFilter.Since("7d"))).options.map(
          (k, v) => (k.value, v.map(_.value))
        ),
        Paging.Cursor("r9")
      ),
      "histmersion agent runs --manager seed:tin --since 7d --before r9"
    )

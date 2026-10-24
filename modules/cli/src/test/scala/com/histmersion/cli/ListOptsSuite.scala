package com.histmersion.cli

import com.histmersion.api.ListFilters.{ApprovalFilter, JobFilter, RunFilter}
import com.monovore.decline.Command

/** The filter options of `agent approvals`, `agent runs` and `jobs`: repeatable or comma-separated,
  * parsed into the core filters the request carries, and repeated by the next-page line. */
class ListOptsSuite extends munit.FunSuite:

  private def parse[A](opts: com.monovore.decline.Opts[A], args: String*): Either[String, A] =
    Command("t", "test")(opts).parse(args.toList, Map.empty).left.map(_.toString)

  test("approvals: state, kinds (repeated and comma-separated), subject, goal, decided-by"):
    assertEquals(parse(ListOpts.approvals, "--kind", "goal-plan,edge", "--kind", "article-publish", "--state", "all",
        "--subject", "tin", "--goal", "bronze-age", "--decided-by", "autopilot"),
      Right(ApprovalFilter(Some("all"), List("goal-plan,edge", "article-publish"), Some("tin"), Some("bronze-age"), List("autopilot"))))
    assertEquals(parse(ListOpts.approvals), Right(ApprovalFilter()))
    // the server reads the comma-separated and repeated values as one list
    assertEquals(parse(ListOpts.approvals, "--kind", "goal-plan,edge").map(_.normalized.kind), Right(List("goal-plan", "edge")))

  test("runs: every filter, and the dates as typed (the server reads them)"):
    assertEquals(parse(ListOpts.runs, "--pipeline", "review-article", "--status", "failed", "--status", "cancelled",
        "--manager", "seed:tin", "--provider", "codex", "--model", "codex:gpt-6", "--origin", "manager", "--goal", "g",
        "--since", "7d", "--until", "2026-10-01", "--subject", "a2020"),
      Right(RunFilter(List("failed", "cancelled"), List("review-article"), List("seed:tin"), Some("g"), List("codex:gpt-6"),
        List("codex"), List("manager"), Some("7d"), Some("2026-10-01"), Some("a2020"))))

  test("jobs: kinds, states, dates, subject"):
    assertEquals(parse(ListOpts.jobs, "--kind", "ingest", "--kind", "reocr", "--status", "failed", "--since", "2026-09-01"),
      Right(JobFilter(List("failed"), List("ingest", "reocr"), Some("2026-09-01"), None, None)))

  test("the commands take the filters, --limit, --before and --json; approve/reject take several ids"):
    val agent = Command("histmersion", "test")(AgentCmd.command)
    assert(agent.parse(List("agent", "approvals", "--kind", "goal-plan", "--state", "open", "--json"), Map.empty).isRight)
    assert(agent.parse(List("agent", "runs", "--pipeline", "review-article", "--status", "failed", "--limit", "5", "--json"), Map.empty).isRight)
    assert(agent.parse(List("agent", "approve", "12", "13", "14", "--note", "checked"), Map.empty).isRight)
    assert(agent.parse(List("agent", "reject", "12"), Map.empty).isRight)
    assert(agent.parse(List("agent", "approve"), Map.empty).isLeft, "an id is needed")
    val jobs = Command("histmersion", "test")(IngestCmd.all)
    assert(jobs.parse(List("jobs", "--kind", "ingest", "--status", "failed", "--subject", "x", "--json"), Map.empty).isRight)
    assert(jobs.parse(List("jobs", "4f1c"), Map.empty).isRight)

  test("the next-page line repeats every filter, a list option once per value"):
    val f = ApprovalFilter(kind = List("goal-plan,edge"), decidedBy = List("person"))
    assertEquals(PageCmd.next("agent approvals", f.options ++ List("limit" -> Some("20")), "41"),
      "histmersion agent approvals --kind goal-plan --kind edge --decided-by person --limit 20 --before 41")
    assertEquals(PageCmd.next("agent runs", RunFilter(manager = List("seed:tin"), since = Some("7d")).options, "r9"),
      "histmersion agent runs --manager seed:tin --since 7d --before r9")

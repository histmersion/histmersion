package com.histmersion.api

import com.histmersion.api.ApprovalDigest.*

/** The approval digest, pure (#3762 proposal 3): the grouping by kind, what a bulk decision matches, the worklog line
  * and the Markdown.
  */
class ApprovalDigestSuite extends munit.FunSuite:

  private val open = List(
    Open(3, "goal-plan", Some("tin"), "2026-10-21T10:00:00Z"),
    Open(1, "goal-plan", Some("tin"), "2026-10-20T10:00:00Z"),
    Open(2, "goal-plan", Some("copper"), "2026-10-20T11:00:00Z"),
    Open(4, "article-publish", Some("art"), "2026-10-22T09:00:00Z"),
    Open(5, "status-change", None, "2026-10-23T09:00:00Z")
  )

  test("grouped by kind, the most first: count, oldest, who decides, subjects, ids oldest first"):
    val gs = group(open, _ == "article-publish")
    assertEquals(gs.map(g => (g.kind, g.count)), List(("goal-plan", 3), ("article-publish", 1), ("status-change", 1)))
    val plans = gs.head
    assertEquals((plans.oldest, plans.decidedBy, plans.ids), (Some("2026-10-20"), "person", List(1L, 2L, 3L)))
    assertEquals(plans.subjects, List(SubjectCount("tin", 2), SubjectCount("copper", 1)))
    assertEquals(gs(1).decidedBy, "checked")

  test("a bulk decision matches by kind, subject and ids together, oldest first, within the limit"):
    assertEquals(matching(open, BulkDecide("approve", kind = Some("goal-plan"))).map(_.id), List(1L, 2L, 3L))
    assertEquals(
      matching(open, BulkDecide("approve", kind = Some("goal-plan"), subject = Some("tin"))).map(_.id),
      List(1L, 3L)
    )
    assertEquals(matching(open, BulkDecide("approve", ids = List(5, 4))).map(_.id), List(4L, 5L))
    assertEquals(
      matching(open, BulkDecide("approve", kind = Some("goal-plan"), limit = Some(2))).map(_.id),
      List(1L, 2L)
    )

  test("the worklog line and the Markdown"):
    val d = Digest("2026-10-24T07:05:00Z", 5, group(open, _ => false), heldForPerson = 2)
    val l = line(d)
    assert(l.startsWith("approval digest: 5 approvals wait for a person — goal-plan 3 (oldest 2026-10-20)"), l)
    assert(l.contains("2 manager items held for a person") && l.contains("decide-all approve --kind"), l)
    assertEquals(line(Digest("t")), "approval digest: nothing waits for a person")
    val md = ApprovalDigestMarkdown.digest(d)
    assert(
      md.startsWith("# Waiting for a person (2026-10-24 07:05 UTC)") && md.contains(
        "## goal-plan: 3 (oldest 2026-10-20)"
      ),
      md
    )
    assert(
      ApprovalDigestMarkdown
        .bulk(BulkResult("reject", false, 3, List(1, 2), List(BulkFailed(3, "decided already")), 2))
        .startsWith("# 3 open approvals matched: rejected 2")
    )
    assert(
      ApprovalDigestMarkdown.bulk(BulkResult("approve", true, 1, List(1), Nil, 5)).contains("would approve 1 (dry run")
    )

  test("goal plans that wait: the group says nobody needs to decide them, the line and the Markdown say why"):
    val why =
      "goal plans wait until the unfinished goals drop to 10: 60 goals with an approved plan are not reviewed yet"
    val gs = group(open, _ => true, Set("goal-plan"))
    assertEquals(gs.map(g => g.kind -> g.decidedBy).toMap.get("goal-plan"), Some(Waits))
    assertEquals(gs.map(g => g.kind -> g.decidedBy).toMap.get("article-publish"), Some("checked"))
    val d = Digest("2026-10-24T07:05:00Z", 5, gs, goalPlansWait = Some(why))
    val l = line(d)
    assert(
      l.contains("goal-plan 3 (oldest 2026-10-20), waiting: nobody needs to decide them now") && l.contains(s"; $why"),
      l
    )
    val md = ApprovalDigestMarkdown.digest(d)
    assert(md.contains(s"- **$why**; a person may still approve one") && md.contains("- decided by: nobody now"), md)

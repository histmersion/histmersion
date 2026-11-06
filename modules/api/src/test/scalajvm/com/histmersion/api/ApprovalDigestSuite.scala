package com.histmersion.api

import com.histmersion.api.automation.ApprovalDigest.*
import com.histmersion.api.automation.ApprovalDigestMarkdown

/** The approval digest, pure (#3762 proposal 3): the grouping by kind, what a bulk decision matches, the worklog line
  * and the Markdown.
  */
class ApprovalDigestSuite extends munit.FunSuite:

  private val open = List(
    Open(com.histmersion.api.automation.ApprovalDigest.Open.Id(3), com.histmersion.api.automation.ApprovalDigest.Open.Kind("goal-plan"), Some(com.histmersion.api.automation.ApprovalDigest.Open.Subject("tin")), com.histmersion.api.automation.ApprovalDigest.Open.CreatedAt("2026-10-21T10:00:00Z")),
    Open(com.histmersion.api.automation.ApprovalDigest.Open.Id(1), com.histmersion.api.automation.ApprovalDigest.Open.Kind("goal-plan"), Some(com.histmersion.api.automation.ApprovalDigest.Open.Subject("tin")), com.histmersion.api.automation.ApprovalDigest.Open.CreatedAt("2026-10-20T10:00:00Z")),
    Open(com.histmersion.api.automation.ApprovalDigest.Open.Id(2), com.histmersion.api.automation.ApprovalDigest.Open.Kind("goal-plan"), Some(com.histmersion.api.automation.ApprovalDigest.Open.Subject("copper")), com.histmersion.api.automation.ApprovalDigest.Open.CreatedAt("2026-10-20T11:00:00Z")),
    Open(com.histmersion.api.automation.ApprovalDigest.Open.Id(4), com.histmersion.api.automation.ApprovalDigest.Open.Kind("article-publish"), Some(com.histmersion.api.automation.ApprovalDigest.Open.Subject("art")), com.histmersion.api.automation.ApprovalDigest.Open.CreatedAt("2026-10-22T09:00:00Z")),
    Open(com.histmersion.api.automation.ApprovalDigest.Open.Id(5), com.histmersion.api.automation.ApprovalDigest.Open.Kind("status-change"), None, com.histmersion.api.automation.ApprovalDigest.Open.CreatedAt("2026-10-23T09:00:00Z"))
  )

  test("grouped by kind, the most first: count, oldest, who decides, subjects, ids oldest first"):
    val gs = group(open, _ == "article-publish")
    assertEquals(gs.map(g => (g.kind, g.count)), List(("goal-plan", 3), ("article-publish", 1), ("status-change", 1)))
    val plans = gs.head
    assertEquals((plans.oldest, plans.decidedBy, plans.ids), (Some("2026-10-20"), "person", List(1L, 2L, 3L)))
    assertEquals(plans.subjects, List(SubjectCount(com.histmersion.api.automation.ApprovalDigest.SubjectCount.Subject("tin"), com.histmersion.api.automation.ApprovalDigest.SubjectCount.Count(2)), SubjectCount(com.histmersion.api.automation.ApprovalDigest.SubjectCount.Subject("copper"), com.histmersion.api.automation.ApprovalDigest.SubjectCount.Count(1))))
    assertEquals(gs(1).decidedBy, "checked")

  test("a bulk decision matches by kind, subject and ids together, oldest first, within the limit"):
    assertEquals(matching(open, BulkDecide(com.histmersion.api.automation.ApprovalDigest.BulkDecide.Decision("approve"), kind = Some(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")))).map(_.id), List(1L, 2L, 3L))
    assertEquals(
      matching(open, BulkDecide(com.histmersion.api.automation.ApprovalDigest.BulkDecide.Decision("approve"), kind = Some(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")), subject = Some(com.histmersion.api.automation.ApprovalDigest.BulkDecide.Subject("tin")))).map(_.id),
      List(1L, 3L)
    )
    assertEquals(matching(open, BulkDecide(com.histmersion.api.automation.ApprovalDigest.BulkDecide.Decision("approve"), ids = List(5, 4))).map(_.id), List(4L, 5L))
    assertEquals(
      matching(open, BulkDecide(com.histmersion.api.automation.ApprovalDigest.BulkDecide.Decision("approve"), kind = Some(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")), limit = Some(2))).map(_.id),
      List(1L, 2L)
    )

  test("the worklog line and the Markdown"):
    val d = Digest(com.histmersion.api.automation.ApprovalDigest.Digest.At("2026-10-24T07:05:00Z"), com.histmersion.api.automation.ApprovalDigest.Digest.Total(5), group(open, _ => false), heldForPerson = com.histmersion.api.automation.ApprovalDigest.Digest.HeldForPerson(2))
    val l = line(d)
    assert(l.startsWith("approval digest: 5 approvals wait for a person — goal-plan 3 (oldest 2026-10-20)"), l)
    assert(l.contains("2 manager items held for a person") && l.contains("decide-all approve --kind"), l)
    assertEquals(line(Digest(com.histmersion.api.automation.ApprovalDigest.Digest.At("t"))), "approval digest: nothing waits for a person")
    val md = ApprovalDigestMarkdown.digest(d)
    assert(
      md.startsWith("# Waiting for a person (2026-10-24 07:05 UTC)") && md.contains(
        "## goal-plan: 3 (oldest 2026-10-20)"
      ),
      md
    )
    assert(
      ApprovalDigestMarkdown
        .bulk(BulkResult(com.histmersion.api.automation.ApprovalDigest.BulkResult.Decision("reject"), com.histmersion.api.automation.ApprovalDigest.BulkResult.DryRun(false), com.histmersion.api.automation.ApprovalDigest.BulkResult.Matched(3), List(com.histmersion.api.automation.ApprovalDigest.BulkResult.Decided(1), com.histmersion.api.automation.ApprovalDigest.BulkResult.Decided(2)), List(BulkFailed(com.histmersion.api.automation.ApprovalDigest.BulkFailed.Id(3), com.histmersion.api.automation.ApprovalDigest.BulkFailed.Error("decided already"))), com.histmersion.api.automation.ApprovalDigest.BulkResult.Left(2)))
        .startsWith("# 3 open approvals matched: rejected 2")
    )
    assert(
      ApprovalDigestMarkdown.bulk(BulkResult(com.histmersion.api.automation.ApprovalDigest.BulkResult.Decision("approve"), com.histmersion.api.automation.ApprovalDigest.BulkResult.DryRun(true), com.histmersion.api.automation.ApprovalDigest.BulkResult.Matched(1), List(com.histmersion.api.automation.ApprovalDigest.BulkResult.Decided(1)), Nil, com.histmersion.api.automation.ApprovalDigest.BulkResult.Left(5))).contains("would approve 1 (dry run")
    )

  test("goal plans that wait: the group says nobody needs to decide them, the line and the Markdown say why"):
    val why =
      "goal plans wait until the unfinished goals drop to 10: 60 goals with an approved plan are not reviewed yet"
    val gs = group(open, _ => true, Set(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")))
    assertEquals(gs.map(g => g.kind -> g.decidedBy).toMap.get("goal-plan"), Some(Waits))
    assertEquals(gs.map(g => g.kind -> g.decidedBy).toMap.get("article-publish"), Some("checked"))
    val d = Digest(com.histmersion.api.automation.ApprovalDigest.Digest.At("2026-10-24T07:05:00Z"), com.histmersion.api.automation.ApprovalDigest.Digest.Total(5), gs, goalPlansWait = Some(why))
    val l = line(d)
    assert(
      l.contains("goal-plan 3 (oldest 2026-10-20), waiting: nobody needs to decide them now") && l.contains(s"; $why"),
      l
    )
    val md = ApprovalDigestMarkdown.digest(d)
    assert(md.contains(s"- **$why**; a person may still approve one") && md.contains("- decided by: nobody now"), md)

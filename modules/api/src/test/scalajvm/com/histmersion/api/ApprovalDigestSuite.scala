package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.ApprovalDigest.*
import com.histmersion.api.automation.ApprovalDigestMarkdown
import com.histmersion.api.automation.Approval

/** The approval digest, pure (#3762 proposal 3): the grouping by kind, what a bulk decision matches, the worklog line
  * and the Markdown.
  */
class ApprovalDigestSuite extends munit.FunSuite:

  private val open = List(
    Open(Open.Id(3), Open.Kind("goal-plan"), Some(Open.Subject("tin")), Open.CreatedAt("2026-10-21T10:00:00Z")),
    Open(Open.Id(1), Open.Kind("goal-plan"), Some(Open.Subject("tin")), Open.CreatedAt("2026-10-20T10:00:00Z")),
    Open(Open.Id(2), Open.Kind("goal-plan"), Some(Open.Subject("copper")), Open.CreatedAt("2026-10-20T11:00:00Z")),
    Open(Open.Id(4), Open.Kind("article-publish"), Some(Open.Subject("art")), Open.CreatedAt("2026-10-22T09:00:00Z")),
    Open(Open.Id(5), Open.Kind("status-change"), None, Open.CreatedAt("2026-10-23T09:00:00Z"))
  )

  test("grouped by kind, the most first: count, oldest, who decides, subjects, ids oldest first"):
    val gs = group(open, _.value == "article-publish")
    assertEquals(gs.map(g => (g.kind, g.count)), List(("goal-plan", 3), ("article-publish", 1), ("status-change", 1)))
    val plans = gs.head
    assertEquals((plans.oldest, plans.decidedBy, plans.ids), (Some("2026-10-20"), "person", List(1L, 2L, 3L)))
    assertEquals(
      plans.subjects,
      List(
        SubjectCount(SubjectCount.Subject("tin"), SubjectCount.Count(2)),
        SubjectCount(SubjectCount.Subject("copper"), SubjectCount.Count(1))
      )
    )
    assertEquals(gs(1).decidedBy, "checked")

  test("a bulk decision matches by kind, subject and ids together, oldest first, within the limit"):
    assertEquals(
      matching(open, BulkDecide(BulkDecide.Decision("approve"), kind = Some(Approval.Kind.Wire("goal-plan"))))
        .map(_.id),
      List(1L, 2L, 3L)
    )
    assertEquals(
      matching(
        open,
        BulkDecide(
          BulkDecide.Decision("approve"),
          kind = Some(Approval.Kind.Wire("goal-plan")),
          subject = Some(BulkDecide.Subject("tin"))
        )
      ).map(_.id),
      List(1L, 3L)
    )
    assertEquals(
      matching(open, BulkDecide(BulkDecide.Decision("approve"), ids = List(BulkDecide.Ids(5), BulkDecide.Ids(4))))
        .map(_.id),
      List(4L, 5L)
    )
    assertEquals(
      matching(
        open,
        BulkDecide(
          BulkDecide.Decision("approve"),
          kind = Some(Approval.Kind.Wire("goal-plan")),
          limit = Some(BulkDecide.Limit(2))
        )
      ).map(_.id),
      List(1L, 2L)
    )

  test("the worklog line and the Markdown"):
    val d = Digest(
      Digest.At("2026-10-24T07:05:00Z"),
      Digest.Total(5),
      group(open, _ => false),
      heldForPerson = Digest.HeldForPerson(2)
    )
    val l = line(d)
    assert(l.value.startsWith("approval digest: 5 approvals wait for a person — goal-plan 3 (oldest 2026-10-20)"), l)
    assert(l.value.contains("2 manager items held for a person") && l.value.contains("decide-all approve --kind"), l)
    assertEquals(line(Digest(Digest.At("t"))), "approval digest: nothing waits for a person")
    val md = ApprovalDigestMarkdown.digest(d)
    assert(
      md.startsWith("# Waiting for a person (2026-10-24 07:05 UTC)") && md.contains(
        "## goal-plan: 3 (oldest 2026-10-20)"
      ),
      md
    )
    assert(
      ApprovalDigestMarkdown
        .bulk(
          BulkResult(
            BulkResult.Decision("reject"),
            BulkResult.DryRun(false),
            BulkResult.Matched(3),
            List(BulkResult.Decided(1), BulkResult.Decided(2)),
            List(BulkFailed(BulkFailed.Id(3), Problem("decided already"))),
            BulkResult.Left(2)
          )
        )
        .startsWith("# 3 open approvals matched: rejected 2")
    )
    assert(
      ApprovalDigestMarkdown
        .bulk(
          BulkResult(
            BulkResult.Decision("approve"),
            BulkResult.DryRun(true),
            BulkResult.Matched(1),
            List(BulkResult.Decided(1)),
            Nil,
            BulkResult.Left(5)
          )
        )
        .contains("would approve 1 (dry run")
    )

  test("goal plans that wait: the group says nobody needs to decide them, the line and the Markdown say why"):
    val why =
      "goal plans wait until the unfinished goals drop to 10: 60 goals with an approved plan are not reviewed yet"
    val gs = group(open, _ => true, Set(Approval.Kind.Wire("goal-plan")))
    assertEquals(gs.map(g => g.kind -> g.decidedBy).toMap.get(Approval.Kind.Wire("goal-plan")), Some(Waits))
    assertEquals(gs.map(g => g.kind -> g.decidedBy).toMap.get(Approval.Kind.Wire("article-publish")), Some("checked"))
    val d =
      Digest(Digest.At("2026-10-24T07:05:00Z"), Digest.Total(5), gs, goalPlansWait = Some(Digest.GoalPlansWait(why)))
    val l = line(d)
    assert(
      l.value.contains("goal-plan 3 (oldest 2026-10-20), waiting: nobody needs to decide them now") && l.value.contains(
        s"; $why"
      ),
      l
    )
    val md = ApprovalDigestMarkdown.digest(d)
    assert(md.contains(s"- **$why**; a person may still approve one") && md.contains("- decided by: nobody now"), md)

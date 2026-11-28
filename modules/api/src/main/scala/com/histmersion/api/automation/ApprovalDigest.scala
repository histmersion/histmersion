package com.histmersion
package api.automation

import com.histmersion.api.automation.approvaldigest.Open
import com.histmersion.api.automation.approvaldigest.KindGroup
import com.histmersion.api.automation.approvaldigest.SubjectCount
import com.histmersion.api.automation.approvaldigest.BulkDecide

import com.histmersion.util.problem.Problem
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object ApprovalDigest:
  type Line = Line.T
  object Line extends Newtype[String, Pure]

  val Waits = "waits"

  val IdsShown = 50

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def group(
      open: List[Open],
      checked: Approval.Kind.Wire => Boolean,
      waiting: Set[Approval.Kind.Wire] = Set.empty
  ): List[KindGroup] =
    open
      .groupBy(_.kind)
      .toList
      .map { (k, as) =>
        val sorted = as.sortBy(_.id)
        val subjects = as
          .flatMap(_.subject)
          .groupMapReduce(identity)(_ => 1)(_ + _)
          .toList
          .sortBy((s, n) => (-n, s))
          .take(10)
          .map((s, n) => SubjectCount(SubjectCount.Subject(s.value), SubjectCount.Count(n)))
        KindGroup(
          k,
          KindGroup.Count(as.size),
          sorted.headOption.map(o => KindGroup.Oldest(o.createdAt.value.take(10))),
          if waiting(k) then Approval.DecidedBy.Wire(Waits)
          else if checked(k) then Approval.DecidedBy.Wire("checked")
          else Approval.DecidedBy.Wire("person"),
          subjects,
          sorted.take(IdsShown).map(o => o.id)
        )
      }
      .sortBy(g => (-(g.count).value, g.kind.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matching(open: List[Open], b: BulkDecide): List[Open] =
    open
      .filter(o =>
        b.kind.forall(k => o.kind.value == k.value.trim) && b.subject.forall(s =>
          o.subject.exists(_.value == s.value.trim)
        ) &&
          (b.ids.isEmpty || b.ids.exists(_ == o.id))
      )
      .sortBy(_.id)
      .take(math.max(1, b.limit.map(_.value).getOrElse(Int.MaxValue)))

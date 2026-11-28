package com.histmersion
package api.automation

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

  final case class SubjectCount(subject: SubjectCount.Subject, count: SubjectCount.Count)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object SubjectCount:
    type Subject = Subject.T
    object Subject extends Newtype[String, Pure]
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

  final case class KindGroup(
      kind: KindGroup.Kind,
      count: KindGroup.Count,
      oldest: Option[KindGroup.Oldest] = None,
      decidedBy: KindGroup.DecidedBy = Approval.DecidedBy.Wire("person"),
      subjects: List[SubjectCount] = Nil,
      ids: List[Open.Id] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object KindGroup:
    export Approval.Kind.Wire as Kind
    type Count = Count.T
    object Count extends Newtype[Int, Pure]
    type Oldest = Oldest.T
    object Oldest extends Newtype[String, Pure]
    export Approval.DecidedBy.Wire as DecidedBy

  final case class Digest(
      at: Digest.At,
      total: Digest.Total = Digest.Total(0),
      kinds: List[KindGroup] = Nil,
      heldForPerson: Digest.HeldForPerson = Digest.HeldForPerson(0),
      lastWritten: Option[Digest.LastWritten] = None,
      goalPlansWait: Option[Digest.GoalPlansWait] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Digest:
    type At = At.T
    object At extends Newtype[String, Pure]
    type Total = Total.T
    object Total extends Newtype[Int, Pure]
    type HeldForPerson = HeldForPerson.T
    object HeldForPerson extends Newtype[Int, Pure]
    type LastWritten = LastWritten.T
    object LastWritten extends Newtype[String, Pure]
    type GoalPlansWait = GoalPlansWait.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object GoalPlansWait extends Newtype[String, Pure]

  final case class BulkDecide(
      decision: BulkDecide.Decision,
      kind: Option[BulkDecide.Kind] = None,
      subject: Option[BulkDecide.Subject] = None,
      ids: List[Open.Id] = Nil,
      note: Option[BulkDecide.Note] = None,
      dryRun: BulkDecide.DryRun = BulkDecide.DryRun.Apply,
      limit: Option[BulkDecide.Limit] = None,
      by: Option[BulkDecide.By] = None,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

  object BulkDecide:
    type Decision = Decision.T
    object Decision extends Newtype[String, Pure]
    export Approval.Kind.Wire as Kind
    type Subject = Subject.T
    object Subject extends Newtype[String, Pure]
    export Approval.Note
    enum DryRun derives CanEqual:
      case DryRun, Apply
    object DryRun extends TwoStates[DryRun]:
      extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]

  final case class BulkFailed(id: BulkFailed.Id, problems: NonEmptyVector[Problem])
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object BulkFailed:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]

  final case class BulkResult(
      decision: BulkResult.Decision,
      dryRun: BulkResult.DryRun = BulkResult.DryRun.Apply,
      matched: BulkResult.Matched = BulkResult.Matched(0),
      decided: List[BulkResult.Decided] = Nil,
      failed: List[BulkFailed] = Nil,
      left: BulkResult.Left = BulkResult.Left(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object BulkResult:
    type Decision = Decision.T
    object Decision extends Newtype[String, Pure]
    enum DryRun derives CanEqual:
      case DryRun, Apply
    object DryRun extends TwoStates[DryRun]:
      extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    type Matched = Matched.T
    object Matched extends Newtype[Int, Pure]
    type Decided = Decided.T
    object Decided extends Newtype[Long, Pure]
    type Left = Left.T
    object Left extends Newtype[Int, Pure]

  val Waits = "waits"

  val IdsShown = 50

  final case class Open(id: Open.Id, kind: Open.Kind, subject: Option[Open.Subject], createdAt: Open.CreatedAt)

  object Open:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    export Approval.Kind.Wire as Kind
    type Subject = Subject.T
    object Subject extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line(d: Digest): Line =
    if d.total == Digest.Total(0) && d.heldForPerson == Digest.HeldForPerson(0) then
      Line("approval digest: nothing waits for a person")
    else
      Line(
        s"approval digest: ${Plural.count(Plural.CountN(d.total.value.toLong), Plural.CountSingular("approval"))} wait for a person" +
          (if d.kinds.isEmpty then ""
           else
             " — " + d.kinds
               .map(g =>
                 s"${g.kind} ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") +
                   (if g.decidedBy.value == "checked" then ", the autopilot's checks decide them within the day's cap"
                    else if g.decidedBy.value == Waits then ", waiting: nobody needs to decide them now"
                    else "")
               )
               .mkString("; ")) +
          (if d.heldForPerson.value > 0 then
             s"; ${Plural.count(Plural.CountN(d.heldForPerson.value.toLong), Plural.CountSingular("manager item"))} held for a person"
           else "") +
          d.goalPlansWait.fold("")(w => s"; $w") +
          "; decide a whole kind at once: histmersion agent decide-all approve --kind <kind> (or the Approvals window)"
      )

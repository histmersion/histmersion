package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

object ApprovalDigest:

  type GroupCheckedArg1 = GroupCheckedArg1.T
  object GroupCheckedArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupCheckedResult = GroupCheckedResult.T
  object GroupCheckedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupWaiting = GroupWaiting.T
  object GroupWaiting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class SubjectCount(subject: SubjectCount.Subject, count: SubjectCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object SubjectCount:
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class KindGroup(
      kind: KindGroup.Kind,
      count: KindGroup.Count,
      oldest: Option[KindGroup.Oldest] = None,
      decidedBy: KindGroup.DecidedBy = _root_.com.histmersion.api.automation.ApprovalDigest.KindGroup.DecidedBy("person"),
      subjects: List[SubjectCount] = Nil,
      ids: List[KindGroup.Ids] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object KindGroup:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Oldest = Oldest.T
    object Oldest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecidedBy = DecidedBy.T
    object DecidedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ids = Ids.T
    object Ids extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Digest(
      at: Digest.At,
      total: Digest.Total = _root_.com.histmersion.api.automation.ApprovalDigest.Digest.Total(0),
      kinds: List[KindGroup] = Nil,
      heldForPerson: Digest.HeldForPerson = _root_.com.histmersion.api.automation.ApprovalDigest.Digest.HeldForPerson(0),
      lastWritten: Option[Digest.LastWritten] = None,
      goalPlansWait: Option[Digest.GoalPlansWait] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Digest:
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeldForPerson = HeldForPerson.T
    object HeldForPerson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastWritten = LastWritten.T
    object LastWritten extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalPlansWait = GoalPlansWait.T
    object GoalPlansWait extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class BulkDecide(
      decision: BulkDecide.Decision,
      kind: Option[BulkDecide.Kind] = None,
      subject: Option[BulkDecide.Subject] = None,
      ids: List[BulkDecide.Ids] = Nil,
      note: Option[BulkDecide.Note] = None,
      dryRun: BulkDecide.DryRun = _root_.com.histmersion.api.automation.ApprovalDigest.BulkDecide.DryRun(false),
      limit: Option[BulkDecide.Limit] = None,
      by: Option[BulkDecide.By] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object BulkDecide:
    type Decision = Decision.T
    object Decision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ids = Ids.T
    object Ids extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class BulkFailed(id: BulkFailed.Id, error: BulkFailed.Error) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object BulkFailed:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class BulkResult(
      decision: BulkResult.Decision,
      dryRun: BulkResult.DryRun = _root_.com.histmersion.api.automation.ApprovalDigest.BulkResult.DryRun(false),
      matched: BulkResult.Matched = _root_.com.histmersion.api.automation.ApprovalDigest.BulkResult.Matched(0),
      decided: List[BulkResult.Decided] = Nil,
      failed: List[BulkFailed] = Nil,
      left: BulkResult.Left = _root_.com.histmersion.api.automation.ApprovalDigest.BulkResult.Left(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object BulkResult:
    type Decision = Decision.T
    object Decision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Matched = Matched.T
    object Matched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Decided = Decided.T
    object Decided extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Left = Left.T
    object Left extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val Waits = "waits"

  val IdsShown = 50

  final case class Open(id: Open.Id, kind: Open.Kind, subject: Option[Open.Subject], createdAt: Open.CreatedAt)

  object Open:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Long](_.value)
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def group(open: List[Open], checked: com.histmersion.api.automation.ApprovalDigest.GroupCheckedArg1 => com.histmersion.api.automation.ApprovalDigest.GroupCheckedResult, waiting: Set[com.histmersion.api.automation.ApprovalDigest.GroupWaiting] = Set.empty): List[KindGroup] =
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
          .map(SubjectCount.apply)
        KindGroup(
          k,
          _root_.com.histmersion.api.automation.ApprovalDigest.KindGroup.Count(as.size),
          sorted.headOption.map(_.createdAt.take(10)),
          if waiting(k) then _root_.com.histmersion.api.automation.ApprovalDigest.KindGroup.DecidedBy(Waits) else if checked(k) then _root_.com.histmersion.api.automation.ApprovalDigest.KindGroup.DecidedBy("checked") else _root_.com.histmersion.api.automation.ApprovalDigest.KindGroup.DecidedBy("person"),
          subjects,
          sorted.take(IdsShown).map(_.id)
        )
      }
      .sortBy(g => (-g.count, g.kind))

  def matching(open: List[Open], b: BulkDecide): List[Open] =
    open
      .filter(o =>
        b.kind.forall(k => o.kind == k.trim) && b.subject.forall(s => o.subject.contains(s.trim)) &&
          (b.ids.isEmpty || b.ids.contains(o.id))
      )
      .sortBy(_.id)
      .take(math.max(1, b.limit.getOrElse(Int.MaxValue)))

  def line(d: Digest): com.histmersion.api.automation.ApprovalDigest.LineResult =
    if d.total == _root_.com.histmersion.api.automation.ApprovalDigest.Digest.Total(0) && d.heldForPerson == _root_.com.histmersion.api.automation.ApprovalDigest.Digest.HeldForPerson(0) then _root_.com.histmersion.api.automation.ApprovalDigest.LineResult("approval digest: nothing waits for a person")
    else
      s"approval digest: ${Plural.count(d.total, _root_.com.histmersion.api.Plural.CountSingular("approval"))} wait for a person" +
        (if d.kinds.isEmpty then ""
         else
           " — " + d.kinds
             .map(g =>
               s"${g.kind} ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") +
                 (if g.decidedBy == "checked" then ", the autopilot's checks decide them within the day's cap"
                  else if g.decidedBy == Waits then ", waiting: nobody needs to decide them now"
                  else "")
             )
             .mkString("; ")) +
        (if d.heldForPerson > 0 then s"; ${Plural.count(d.heldForPerson, "manager item")} held for a person" else "") +
        d.goalPlansWait.fold("")(w => s"; $w") +
        "; decide a whole kind at once: histmersion agent decide-all approve --kind <kind> (or the Approvals window)"

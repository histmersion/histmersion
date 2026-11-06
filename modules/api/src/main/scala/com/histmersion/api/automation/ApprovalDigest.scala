package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

object ApprovalDigest:
  type Line = Line.T
  object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


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
      decidedBy: KindGroup.DecidedBy = _root_.com.histmersion.api.automation.Approval.DecidedBy.Wire("person"),
      subjects: List[SubjectCount] = Nil,
      ids: List[KindGroup.Ids] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object KindGroup:
    type Kind = com.histmersion.api.automation.Approval.Kind.Wire
    val Kind: _root_.com.histmersion.api.automation.Approval.Kind.Wire.type = _root_.com.histmersion.api.automation.Approval.Kind.Wire
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Oldest = Oldest.T
    object Oldest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecidedBy = com.histmersion.api.automation.Approval.DecidedBy.Wire
    val DecidedBy: _root_.com.histmersion.api.automation.Approval.DecidedBy.Wire.type = _root_.com.histmersion.api.automation.Approval.DecidedBy.Wire
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
    type Kind = com.histmersion.api.automation.Approval.Kind.Wire
    val Kind: _root_.com.histmersion.api.automation.Approval.Kind.Wire.type = _root_.com.histmersion.api.automation.Approval.Kind.Wire
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ids = Ids.T
    object Ids extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = com.histmersion.api.automation.Approval.Note
    val Note: _root_.com.histmersion.api.automation.Approval.Note.type = _root_.com.histmersion.api.automation.Approval.Note
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
    type Kind = com.histmersion.api.automation.Approval.Kind.Wire
    val Kind: _root_.com.histmersion.api.automation.Approval.Kind.Wire.type = _root_.com.histmersion.api.automation.Approval.Kind.Wire
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def group(open: List[Open], checked: com.histmersion.api.automation.Approval.Kind.Wire => Boolean, waiting: Set[com.histmersion.api.automation.Approval.Kind.Wire] = Set.empty): List[KindGroup] =
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
          _root_.com.histmersion.api.automation.ApprovalDigest.KindGroup.Count(as.size),
          sorted.headOption.map(o => KindGroup.Oldest(o.createdAt.value.take(10))),
          if waiting(k) then _root_.com.histmersion.api.automation.Approval.DecidedBy.Wire(Waits) else if checked(k) then _root_.com.histmersion.api.automation.Approval.DecidedBy.Wire("checked") else _root_.com.histmersion.api.automation.Approval.DecidedBy.Wire("person"),
          subjects,
          sorted.take(IdsShown).map(o => KindGroup.Ids(o.id.value))
        )
      }
      .sortBy(g => (-(g.count).value, g.kind.value))

  def matching(open: List[Open], b: BulkDecide): List[Open] =
    open
      .filter(o =>
        b.kind.forall(k => o.kind.value == k.value.trim) && b.subject.forall(s => o.subject.exists(_.value == s.value.trim)) &&
          (b.ids.isEmpty || b.ids.exists(_.value == o.id.value))
      )
      .sortBy(_.id)
      .take(math.max(1, b.limit.map(_.value).getOrElse(Int.MaxValue)))

  def line(d: Digest): com.histmersion.api.automation.ApprovalDigest.Line =
    if d.total == _root_.com.histmersion.api.automation.ApprovalDigest.Digest.Total(0) && d.heldForPerson == _root_.com.histmersion.api.automation.ApprovalDigest.Digest.HeldForPerson(0) then _root_.com.histmersion.api.automation.ApprovalDigest.Line("approval digest: nothing waits for a person")
    else
      _root_.com.histmersion.api.automation.ApprovalDigest.Line(s"approval digest: ${Plural.count(_root_.com.histmersion.api.Plural.CountN(d.total.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("approval"))} wait for a person" +
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
        (if d.heldForPerson.value > 0 then s"; ${Plural.count(_root_.com.histmersion.api.Plural.CountN(d.heldForPerson.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("manager item"))} held for a person" else "") +
        d.goalPlansWait.fold("")(w => s"; $w") +
        "; decide a whole kind at once: histmersion agent decide-all approve --kind <kind> (or the Approvals window)")

package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The approval digest (#3762 proposal 3): everything waiting for a person, in ONE view — the open approvals grouped by
  * kind (how many, the oldest, who decides them now, the subjects with the most) and the manager items held for a
  * person — instead of a hundred single items; written once a UTC day to the worklog, and readable at any time (`GET
  * /api/agents/approvals/digest`). A group is decided in one request (`POST /api/agents/approvals/decide`: a kind, a
  * subject, or ids), each through the same decision a person's click takes.
  */
object ApprovalDigest:

  /** A subject (a goal, an article) and its open approvals of a kind. */
  final case class SubjectCount(subject: String, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
  object SubjectCount:
    given Schema[SubjectCount] = summon[KindlingsSchema[SubjectCount]].schema

  /** One kind's open approvals: how many, the oldest's date, who decides them now (`person`, `checked`: the autopilot's
    * checks, within the day's cap, or `waits`: nobody is asked to, see [[Digest.goalPlansWait]]), the subjects with the
    * most, and the ids (oldest first, at most [[IdsShown]]).
    */
  final case class KindGroup(
      kind: String,
      count: Int,
      oldest: Option[String] = None,
      decidedBy: String = "person",
      subjects: List[SubjectCount] = Nil,
      ids: List[Long] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object KindGroup:
    given Schema[KindGroup] = summon[KindlingsSchema[KindGroup]].schema

  /** `GET /api/agents/approvals/digest`: when it was read, every open approval in all, the kinds (the most first), the
    * manager items held for a person or whose run waits for one, when the daily line was last written to the worklog,
    * and why goal plans wait (too many unfinished goals), if they do.
    */
  final case class Digest(
      at: String,
      total: Int = 0,
      kinds: List[KindGroup] = Nil,
      heldForPerson: Int = 0,
      lastWritten: Option[String] = None,
      goalPlansWait: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Digest:
    given Schema[Digest] = summon[KindlingsSchema[Digest]].schema

  /** `POST /api/agents/approvals/decide`: `approve` or `reject` every OPEN approval that matches — of `kind`, about
    * `subject` (exactly), or the `ids` given (all three narrow together; at least one is needed) — at most `limit`
    * (oldest first). `dryRun` lists what it would decide.
    */
  final case class BulkDecide(
      decision: String,
      kind: Option[String] = None,
      subject: Option[String] = None,
      ids: List[Long] = Nil,
      note: Option[String] = None,
      dryRun: Boolean = false,
      limit: Option[Int] = None,
      by: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object BulkDecide:
    given Schema[BulkDecide] = summon[KindlingsSchema[BulkDecide]].schema

  final case class BulkFailed(id: Long, error: String) derives KindlingsCodecAsObject, KindlingsSchema
  object BulkFailed:
    given Schema[BulkFailed] = summon[KindlingsSchema[BulkFailed]].schema

  /** What a bulk decision did (a dry run: would do): the approvals it matched, the ones decided (or that would be), the
    * ones refused and why, and how many open approvals are left.
    */
  final case class BulkResult(
      decision: String,
      dryRun: Boolean = false,
      matched: Int = 0,
      decided: List[Long] = Nil,
      failed: List[BulkFailed] = Nil,
      left: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object BulkResult:
    given Schema[BulkResult] = summon[KindlingsSchema[BulkResult]].schema

  /** A group's `decidedBy` while nobody is asked to decide it. */
  val Waits = "waits"

  /** The ids a group lists. */
  val IdsShown = 50

  /** One open approval as the digest reads it. */
  final case class Open(id: Long, kind: String, subject: Option[String], createdAt: String)

  /** The open approvals grouped by kind, the most first (then the kind); `checked` = the autopilot's checks decide that
    * kind now; `waiting` = the kinds nobody is asked to decide now. Pure.
    */
  def group(open: List[Open], checked: String => Boolean, waiting: Set[String] = Set.empty): List[KindGroup] =
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
          as.size,
          sorted.headOption.map(_.createdAt.take(10)),
          if waiting(k) then Waits else if checked(k) then "checked" else "person",
          subjects,
          sorted.take(IdsShown).map(_.id)
        )
      }
      .sortBy(g => (-g.count, g.kind))

  /** Which open approvals a bulk decision matches, oldest first, at most `limit`. Pure. */
  def matching(open: List[Open], b: BulkDecide): List[Open] =
    open
      .filter(o =>
        b.kind.forall(k => o.kind == k.trim) && b.subject.forall(s => o.subject.contains(s.trim)) &&
          (b.ids.isEmpty || b.ids.contains(o.id))
      )
      .sortBy(_.id)
      .take(math.max(1, b.limit.getOrElse(Int.MaxValue)))

  /** The digest in one worklog line. */
  def line(d: Digest): String =
    if d.total == 0 && d.heldForPerson == 0 then "approval digest: nothing waits for a person"
    else
      s"approval digest: ${Plural.count(d.total, "approval")} wait for a person" +
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

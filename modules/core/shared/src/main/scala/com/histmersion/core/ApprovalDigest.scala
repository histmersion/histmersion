package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The approval digest (#3762 proposal 3): everything waiting for a person, in ONE view — the open
  * approvals grouped by kind (how many, the oldest, who decides them now, the subjects with the most) and
  * the manager items held for a person — instead of a hundred single items; written once a UTC day to the
  * worklog, and readable at any time (`GET /api/agents/approvals/digest`). A group is decided in one
  * request (`POST /api/agents/approvals/decide`: a kind, a subject, or ids), each through the same
  * decision a person's click takes. */
object ApprovalDigest:

  /** A subject (a goal, an article) and its open approvals of a kind. */
  final case class SubjectCount(subject: String, count: Int) derives KindlingsCodecAsObject
  object SubjectCount:
    given Schema[SubjectCount] = KindlingsSchema.derived[SubjectCount].schema

  /** One kind's open approvals: how many, the oldest's date, who decides them now (`person`, `checked`:
    * the autopilot's checks, within the day's cap, or `waits`: nobody is asked to, see [[Digest.goalPlansWait]]),
    * the subjects with the most, and the ids (oldest first, at most [[IdsShown]]). */
  final case class KindGroup(kind: String, count: Int, oldest: Option[String] = None, decidedBy: String = "person",
      subjects: List[SubjectCount] = Nil, ids: List[Long] = Nil) derives KindlingsCodecAsObject
  object KindGroup:
    given Schema[KindGroup] = KindlingsSchema.derived[KindGroup].schema

  /** `GET /api/agents/approvals/digest`: when it was read, every open approval in all, the kinds (the most
    * first), the manager items held for a person or whose run waits for one, when the daily line was last
    * written to the worklog, and why goal plans wait (too many unfinished goals), if they do. */
  final case class Digest(at: String, total: Int = 0, kinds: List[KindGroup] = Nil, heldForPerson: Int = 0,
      lastWritten: Option[String] = None, goalPlansWait: Option[String] = None) derives KindlingsCodecAsObject
  object Digest:
    given Schema[Digest] = KindlingsSchema.derived[Digest].schema

  /** `POST /api/agents/approvals/decide`: `approve` or `reject` every OPEN approval that matches — of `kind`,
    * about `subject` (exactly), or the `ids` given (all three narrow together; at least one is needed) — at
    * most `limit` (oldest first). `dryRun` lists what it would decide. */
  final case class BulkDecide(decision: String, kind: Option[String] = None, subject: Option[String] = None,
      ids: List[Long] = Nil, note: Option[String] = None, dryRun: Boolean = false, limit: Option[Int] = None,
      by: Option[String] = None) derives KindlingsCodecAsObject
  object BulkDecide:
    given Schema[BulkDecide] = KindlingsSchema.derived[BulkDecide].schema

  final case class BulkFailed(id: Long, error: String) derives KindlingsCodecAsObject
  object BulkFailed:
    given Schema[BulkFailed] = KindlingsSchema.derived[BulkFailed].schema

  /** What a bulk decision did (a dry run: would do): the approvals it matched, the ones decided (or that
    * would be), the ones refused and why, and how many open approvals are left. */
  final case class BulkResult(decision: String, dryRun: Boolean = false, matched: Int = 0, decided: List[Long] = Nil,
      failed: List[BulkFailed] = Nil, left: Int = 0) derives KindlingsCodecAsObject
  object BulkResult:
    given Schema[BulkResult] = KindlingsSchema.derived[BulkResult].schema

  /** A group's `decidedBy` while nobody is asked to decide it. */
  val Waits = "waits"

  /** The ids a group lists. */
  val IdsShown = 50

  /** One open approval as the digest reads it. */
  final case class Open(id: Long, kind: String, subject: Option[String], createdAt: String)

  /** The open approvals grouped by kind, the most first (then the kind); `checked` = the autopilot's checks
    * decide that kind now; `waiting` = the kinds nobody is asked to decide now. Pure. */
  def group(open: List[Open], checked: String => Boolean, waiting: Set[String] = Set.empty): List[KindGroup] =
    open.groupBy(_.kind).toList.map { (k, as) =>
      val sorted = as.sortBy(_.id)
      val subjects = as.flatMap(_.subject).groupMapReduce(identity)(_ => 1)(_ + _).toList.sortBy((s, n) => (-n, s))
        .take(10).map(SubjectCount.apply)
      KindGroup(k, as.size, sorted.headOption.map(_.createdAt.take(10)),
        if waiting(k) then Waits else if checked(k) then "checked" else "person",
        subjects, sorted.take(IdsShown).map(_.id))
    }.sortBy(g => (-g.count, g.kind))

  /** Which open approvals a bulk decision matches, oldest first, at most `limit`. Pure. */
  def matching(open: List[Open], b: BulkDecide): List[Open] =
    open.filter(o => b.kind.forall(k => o.kind == k.trim) && b.subject.forall(s => o.subject.contains(s.trim)) &&
      (b.ids.isEmpty || b.ids.contains(o.id))).sortBy(_.id).take(math.max(1, b.limit.getOrElse(Int.MaxValue)))

  /** The digest in one worklog line. */
  def line(d: Digest): String =
    if d.total == 0 && d.heldForPerson == 0 then "approval digest: nothing waits for a person"
    else s"approval digest: ${Plural.count(d.total, "approval")} wait for a person" +
      (if d.kinds.isEmpty then "" else " — " + d.kinds.map(g => s"${g.kind} ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") +
        (if g.decidedBy == "checked" then ", the autopilot's checks decide them within the day's cap"
         else if g.decidedBy == Waits then ", waiting: nobody needs to decide them now" else "")).mkString("; ")) +
      (if d.heldForPerson > 0 then s"; ${Plural.count(d.heldForPerson, "manager item")} held for a person" else "") +
      d.goalPlansWait.fold("")(w => s"; $w") +
      "; decide a whole kind at once: histmersion agent decide-all approve --kind <kind> (or the Approvals window)"

/** The digest and a bulk decision as Markdown — every surface prints this. Pure. */
object ApprovalDigestMarkdown:
  import ApprovalDigest.*

  private def decider(decidedBy: String): String = decidedBy match
    case "checked" => "the autopilot's checks (within the day's cap); a person may decide them first"
    case Waits     => "nobody now: they wait (see above); a person may still decide one"
    case _         => "a person"

  def digest(d: Digest): String =
    val head = s"# Waiting for a person (${d.at.take(16).replace('T', ' ')} UTC)\n\n" +
      s"- ${Plural.count(d.total, "open approval")}" +
      (if d.heldForPerson > 0 then s"\n- ${Plural.count(d.heldForPerson, "manager item")} held for a person, or with a run waiting for one" else "") +
      d.lastWritten.fold("")(t => s"\n- the daily digest was last written to the worklog at $t") +
      d.goalPlansWait.fold("")(w => s"\n- **$w**; a person may still approve one")
    val kinds = d.kinds.map { g =>
      s"## ${g.kind}: ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") + "\n\n" +
        s"- decided by: ${decider(g.decidedBy)}\n" +
        (if g.subjects.isEmpty then "" else s"- about: ${g.subjects.map(x => s"${x.subject} (${x.count})").mkString(", ")}\n") +
        s"- ids: ${g.ids.map(i => s"#$i").mkString(", ")}${if g.count > g.ids.size then ", …" else ""}\n" +
        s"- all at once: `histmersion agent decide-all approve --kind ${g.kind}` (or `reject`; `--dry-run` lists them)"
    }
    (head :: kinds).mkString("\n\n")

  def bulk(r: BulkResult): String =
    val verb = if r.dryRun then s"would ${r.decision}" else if r.decision == "approve" then "approved" else "rejected"
    s"# ${Plural.count(r.matched, "open approval")} matched: $verb ${r.decided.size}" + (if r.dryRun then " (dry run: nothing decided)" else "") +
      (if r.decided.isEmpty then "" else s"\n\n- ${r.decided.map(i => s"#$i").mkString(", ")}") +
      (if r.failed.isEmpty then "" else "\n\n## Not decided\n\n" + r.failed.map(f => s"- #${f.id}: ${f.error}").mkString("\n")) +
      s"\n\n${Plural.count(r.left, "open approval")} left."

package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.{GoalStatus, WireEnumValues}

/** Where an article stands with the `review-article` panel (rules reviewer, cold reader, skeptic and
  * the mechanical checks), as one word: derived by the server from the review manager's item for the
  * article and the article's review ledger (`domain.ArticleReviewStatus`). */
enum ReviewState(val wire: String, val meaning: String):
  case Unreviewed extends ReviewState("unreviewed", "no review of this article is recorded and none is queued")
  case Queued     extends ReviewState("queued", "waiting for the review panel to take it up")
  case InReview   extends ReviewState("in-review", "the review panel is working on it now")
  case Passed     extends ReviewState("passed", "reviewed: no MAJOR finding is open")
  case MajorsOpen extends ReviewState("majors-open", "reviewed: MAJOR findings are still open")
  case Stopped    extends ReviewState("stopped", "the review manager gave up on it; a person decides what happens next")

object ReviewState:
  def parse(s: String): Option[ReviewState] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[ReviewState]:
    def all: Array[ReviewState] = ReviewState.values
    def wire(e: ReviewState): String = e.wire
    def fromWire(s: String): Option[ReviewState] = parse(s)
  given Encoder[ReviewState] = WireEnum.encoder
  given Decoder[ReviewState] = WireEnum.decoder
  given Schema[ReviewState]  = WireEnum.schema

/** An article's review state as a badge: the state, its short `label` (`in review · round 2`,
  * `3 majors open`) and the `detail` sentence its tooltip shows. The counts are of the review
  * ledger's findings; `round` is the pass of the run that is going on now. */
final case class ReviewBadge(
    state: ReviewState,
    label: String,
    detail: String,
    openMajors: Int = 0,
    openMinors: Int = 0,
    findings: Int = 0,
    runs: Int = 0,
    round: Option[Int] = None
) derives KindlingsCodecAsObject
object ReviewBadge:
  given Schema[ReviewBadge] = KindlingsSchema.derived[ReviewBadge].schema

/** One finding of the review ledger, where it was raised and what became of it: `outcome` is the
  * ledger's status (`open`, `fixed`, `rejected`, `wont-fix`, `set-aside`); `note` the closure's
  * evidence (the record's sentence of a rejection, the reason a minor one was declined). */
final case class ReviewFinding(
    id: String,
    reviewer: String,
    severity: String,
    kind: String,
    where: String,
    problem: String,
    fix: String,
    quote: String,
    outcome: String,
    note: String = "",
    closedBy: Option[String] = None,
    closedRound: Option[Int] = None,
    closedRunNumber: Option[Int] = None,
    reopened: Int = 0,
    /** Raised by the final pass of its run: the next run's work. */
    deferred: Boolean = false
) derives KindlingsCodecAsObject
object ReviewFinding:
  given Schema[ReviewFinding] = KindlingsSchema.derived[ReviewFinding].schema

/** The findings first raised in one pass (`round`) of one review run; `runNumber` counts the runs of
  * this article from 1, `startedAt` is when the run began (UTC, when the run is still recorded). */
final case class ReviewRound(run: String, runNumber: Int, round: Int, startedAt: Option[String], findings: List[ReviewFinding])
    derives KindlingsCodecAsObject
object ReviewRound:
  given Schema[ReviewRound] = KindlingsSchema.derived[ReviewRound].schema

/** A research goal that links the article, with the goal's stage. */
final case class ReviewGoal(slug: String, title: String, status: String) derives KindlingsCodecAsObject
object ReviewGoal:
  given Schema[ReviewGoal] = KindlingsSchema.derived[ReviewGoal].schema

/** An approval about the article that waits for a person (an `article-filter`, an `article-publish`). */
final case class ReviewApproval(id: Long, kind: String) derives KindlingsCodecAsObject
object ReviewApproval:
  given Schema[ReviewApproval] = KindlingsSchema.derived[ReviewApproval].schema

/** `GET /api/articles/{slug}/review` — the article's review state, the review manager's last word on
  * it (`outcome`, at `updatedAt`), every finding the panel raised in time order, the research goals
  * that link it and the approvals about it that wait for a person. */
final case class ArticleReviewView(
    slug: String,
    badge: ReviewBadge,
    outcome: Option[String] = None,
    updatedAt: Option[String] = None,
    rounds: List[ReviewRound] = Nil,
    goals: List[ReviewGoal] = Nil,
    approvals: List[ReviewApproval] = Nil
) derives KindlingsCodecAsObject
object ArticleReviewView:
  given Schema[ArticleReviewView] = KindlingsSchema.derived[ArticleReviewView].schema

  /** A reviewer's wire name as a person reads it. */
  def reviewerName(wire: String): String = wire match
    case "rules"       => "rules reviewer"
    case "cold-reader" => "cold reader"
    case "skeptic"     => "skeptic"
    case "mechanical"  => "mechanical check"
    case other         => other

  /** What became of a finding, in words: `fixed`, `rejected: <note>`, `open`, … */
  def outcomeLine(f: ReviewFinding): String =
    val base = f.outcome match
      case "wont-fix"  => "won't fix"
      case "set-aside" => "set aside"
      case "open" if f.deferred => "open (left for the next run)"
      case other       => other
    val when = f.closedRunNumber.zip(f.closedRound).fold("")((run, round) => s" in run $run, round $round")
    val by   = f.closedBy.filter(_.nonEmpty).fold("")(b => s" by ${reviewerName(b)}")
    val note = Option(f.note).map(_.trim).filter(_.nonEmpty).fold("")(n => s": $n")
    val back = if f.reopened > 0 then s" (reopened ${Plural.count(f.reopened, "time")})" else ""
    s"$base$by$when$back$note"

  def markdown(r: ArticleReviewView): String =
    val head = s"## Review of [[${r.slug}]]\n\n**${r.badge.label}** — ${r.badge.detail}" +
      r.outcome.filter(_.nonEmpty).fold("")(o => s"\n\nReview manager: $o" + r.updatedAt.fold("")(t => s" ($t)"))
    val goals =
      if r.goals.isEmpty then ""
      else "\n\n### Research goals\n\n" + r.goals.map(g => s"- `${g.slug}` (${g.status}) ${g.title}").mkString("\n")
    val approvals =
      if r.approvals.isEmpty then ""
      else "\n\n### Waiting for a person\n\n" + r.approvals.map(a => s"- approval #${a.id} (${a.kind})").mkString("\n")
    val history =
      if r.rounds.isEmpty then "\n\n### Review history\n\nNo finding recorded."
      else "\n\n### Review history\n\n" + r.rounds.map { rd =>
        s"#### Run ${rd.runNumber}, round ${rd.round}${rd.startedAt.fold("")(t => s" ($t)")}\n\n" +
          rd.findings.map(f =>
            s"- **${f.id}** ${f.severity.toUpperCase} · ${reviewerName(f.reviewer)}" +
              (if f.kind.nonEmpty then s" · ${f.kind}" else "") + (if f.where.nonEmpty then s" · ${f.where}" else "") +
              s": ${f.problem} — *${outcomeLine(f)}*").mkString("\n")
      }.mkString("\n\n")
    head + goals + approvals + history

/** What each research-goal stage means, in the order a goal moves through them: the goals window's
  * legend and the tooltip of every goal-stage badge, in the web page and on the server's pages. */
object GoalStages:
  val all: List[(String, String)] = List(
    GoalStatus.Planned  -> "created; nothing works on it yet",
    GoalStatus.Seeding  -> "finding and reading sources, writing its articles",
    GoalStatus.Compiled -> "its articles are written; not all have passed review yet",
    GoalStatus.Reviewed -> "every article (and sub-goal) passed the review panel",
    GoalStatus.Done     -> "finished: only you set this, the autopilot never does")

  def meaning(status: String): Option[String] = all.collectFirst { case (s, m) if s == status => m }

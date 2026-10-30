package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/articles/{slug}/review` — the article's review state, the review manager's last word on it (`outcome`, at
  * `updatedAt`), every finding the panel raised in time order, the research goals that link it and the approvals about
  * it that wait for a person.
  */
final case class ArticleReviewView(
    slug: String,
    badge: ReviewBadge,
    outcome: Option[String] = None,
    updatedAt: Option[String] = None,
    rounds: List[ReviewRound] = Nil,
    goals: List[ReviewGoal] = Nil,
    approvals: List[ReviewApproval] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleReviewView:
  given Schema[ArticleReviewView] = summon[KindlingsSchema[ArticleReviewView]].schema

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
      case "wont-fix"           => "won't fix"
      case "set-aside"          => "set aside"
      case "open" if f.deferred => "open (left for the next run)"
      case other                => other
    val when = f.closedRunNumber.zip(f.closedRound).fold("")((run, round) => s" in run $run, round $round")
    val by = f.closedBy.filter(_.nonEmpty).fold("")(b => s" by ${reviewerName(b)}")
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
      else
        "\n\n### Review history\n\n" + r.rounds
          .map { rd =>
            s"#### Run ${rd.runNumber}, round ${rd.round}${rd.startedAt.fold("")(t => s" ($t)")}\n\n" +
              rd.findings
                .map(f =>
                  s"- **${f.id}** ${f.severity.toUpperCase} · ${reviewerName(f.reviewer)}" +
                    (if f.kind.nonEmpty then s" · ${f.kind}" else "") + (if f.where.nonEmpty then s" · ${f.where}"
                                                                         else "") +
                    s": ${f.problem} — *${outcomeLine(f)}*"
                )
                .mkString("\n")
          }
          .mkString("\n\n")
    head + goals + approvals + history

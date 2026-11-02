package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.domain.library.{MatchMethod, MatchOutcome}
import com.histmersion.api.given
import com.histmersion.util.data.exchange.Issue

final case class SourceMatch(
    rightUid: UUID,
    rightCitekey: String,
    leftUid: Option[UUID],
    leftCitekey: Option[String],
    method: MatchMethod,
    outcome: MatchOutcome,
    score: Option[Double] = None,
    why: List[String] = Nil,
    issue: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceMatch:
  given Schema[SourceMatch] = summon[KindlingsSchema[SourceMatch]].schema

  /** A source's match in a merge plan: the plan's Markdown lists every one but the new sources. */
  val Matches: com.histmersion.api.exchange.PlanMatchKind[SourceMatch] =
    com.histmersion.api.exchange.PlanMatchKind(_.outcome != MatchOutcome.New, line)

  /** A source's match as the plan's Markdown lists it. */
  def line(s: SourceMatch): String =
    val target = s.leftCitekey.fold("")(c => s" → `$c`")
    val score = s.score.fold("")(x => s" · score ${math.round(x * 10) / 10.0}")
    s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i")

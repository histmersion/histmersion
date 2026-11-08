package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CandidateList(
    goal: Option[CandidateList.Goal],
    state: CandidateList.State,
    candidates: List[Source.Candidate],
    counts: List[CandidateCount],
    offset: CandidateList.Offset = CandidateList.Offset(0),
    next: Option[CandidateList.Next] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CandidateList:
  export com.histmersion.api.research.ReviewGoal.Slug as Goal
  type State = State.T
  object State extends Newtype[String, Pure]
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  type Next = Next.T
  object Next extends Newtype[Int, Pure]

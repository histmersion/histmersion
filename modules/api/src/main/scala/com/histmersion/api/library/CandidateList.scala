package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class CandidateList(
    goal: Option[CandidateList.Goal],
    state: CandidateList.State,
    candidates: List[Source.Candidate],
    counts: List[CandidateCount],
    offset: CandidateList.Offset = _root_.com.histmersion.api.library.CandidateList.Offset(0),
    next: Option[CandidateList.Next] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CandidateList:
  export com.histmersion.api.research.ReviewGoal.{Slug as Goal}
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Offset = Offset.T
  object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Next = Next.T
  object Next extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

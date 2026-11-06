package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MatchCounts(
    sources: MatchCounts.Sources = 0,
    byUid: MatchCounts.ByUid = 0,
    byLedger: MatchCounts.ByLedger = 0,
    byStrongKey: MatchCounts.ByStrongKey = 0,
    byScore: MatchCounts.ByScore = 0,
    undecided: MatchCounts.Undecided = 0,
    unmatched: MatchCounts.Unmatched = 0,
    deletedHere: MatchCounts.DeletedHere = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MatchCounts:
  type Sources = Sources.T
  object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByUid = ByUid.T
  object ByUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByLedger = ByLedger.T
  object ByLedger extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByStrongKey = ByStrongKey.T
  object ByStrongKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByScore = ByScore.T
  object ByScore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undecided = Undecided.T
  object Undecided extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unmatched = Unmatched.T
  object Unmatched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeletedHere = DeletedHere.T
  object DeletedHere extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

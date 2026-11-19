package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MatchCounts(
    sources: MatchCounts.Sources = MatchCounts.Sources(0),
    byUid: MatchCounts.ByUid = MatchCounts.ByUid(0),
    byLedger: MatchCounts.ByLedger = MatchCounts.ByLedger(0),
    byStrongKey: MatchCounts.ByStrongKey = MatchCounts.ByStrongKey(0),
    byScore: MatchCounts.ByScore = MatchCounts.ByScore(0),
    undecided: MatchCounts.Undecided = MatchCounts.Undecided(0),
    unmatched: MatchCounts.Unmatched = MatchCounts.Unmatched(0),
    deletedHere: MatchCounts.DeletedHere = MatchCounts.DeletedHere(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MatchCounts:
  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  type ByUid = ByUid.T
  object ByUid extends Newtype[Int, Pure]
  type ByLedger = ByLedger.T
  object ByLedger extends Newtype[Int, Pure]
  type ByStrongKey = ByStrongKey.T
  object ByStrongKey extends Newtype[Int, Pure]
  type ByScore = ByScore.T
  object ByScore extends Newtype[Int, Pure]
  type Undecided = Undecided.T
  object Undecided extends Newtype[Int, Pure]
  type Unmatched = Unmatched.T
  object Unmatched extends Newtype[Int, Pure]
  type DeletedHere = DeletedHere.T
  object DeletedHere extends Newtype[Int, Pure]

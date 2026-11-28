package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiqueReason
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Fix(
    body: Fix.Body,
    edits: List[Article.Edits.EditResult],
    applied: List[Fix.Applied],
    rejected: List[RejectedFix],
    unverified: List[Fix.Unverified],
    unresolvedMoved: List[Fix.UnresolvedMoved],
    notes: Fix.Notes,
    wordsBefore: Fix.WordsBefore,
    wordsAfter: Fix.WordsAfter,
    reasons: List[Coverage.Reason] = Nil,
    wontFix: List[DeclinedFix] = Nil,
    setAside: List[PairSetAside] = Nil,
    repaired: List[Fix.Repaired] = Nil,
    repairs: List[Fix.Repairs] = Nil,
    notFixed: List[DeclinedFix] = Nil,
    pairsAside: List[CritiqueReason] = Nil
) derives JsonCodec

object Fix:
  export Article.Body
  export ReviewFinding.{Id as Applied, Id as Unverified}
  type UnresolvedMoved = UnresolvedMoved.T
  object UnresolvedMoved extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
  export Article.Edits.ArticleCheck.{WordsAfter, WordsBefore}
  export ReviewFinding.Id as Repaired
  type Repairs = Repairs.T
  object Repairs extends Newtype[String, Pure]

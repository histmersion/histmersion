package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class ItemState(
    history: List[RunMark] = Nil,
    before: Option[ItemState.Before] = None,
    reopened: ItemState.Reopened = ItemState.Reopened(0),
    pairsBefore: Option[ItemState.PairsBefore] = None,
    spec: Option[ArticleSpec] = None,
    filterHits: Option[ItemState.FilterHits] = None,
    oa: Option[OaWork] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ItemState:
  type Before = Before.T
  object Before extends Newtype[Int, Pure]
  type Reopened = Reopened.T
  object Reopened extends Newtype[Int, Pure]
  type PairsBefore = PairsBefore.T
  object PairsBefore extends Newtype[Int, Pure]
  type FilterHits = FilterHits.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FilterHits extends Newtype[Int, Pure]

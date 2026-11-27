package com.histmersion
package domain.library.acquisition

import com.histmersion.plugin.Year
import com.histmersion.plugin.Doi
import com.histmersion.plugin.WorkKey
import com.histmersion.upstreams.ArxivId
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
final case class FollowupRow(
    title: Option[FollowupRow.Title] = None,
    year: Option[Year] = None,
    doi: Option[Doi] = None,
    arxiv: Option[FollowupRow.Arxiv] = None,
    citedBy: Option[FollowupRow.CitedBy] = None,
    isInfluential: FollowupRow.IsInfluential = FollowupRow.IsInfluential(false),
    intents: List[FollowupRow.Intents] = Nil,
    contexts: List[FollowupRow.Contexts] = Nil
)

object FollowupRow:
  export WorkKey.Title
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Arxiv = ArxivId
  val Arxiv: ArxivId.type = ArxivId
  type CitedBy = CitedBy.T
  object CitedBy extends Newtype[Int, Pure]
  type IsInfluential = IsInfluential.T
  object IsInfluential extends Newtype[Boolean, Pure]
  type Intents = Intents.T
  object Intents extends Newtype[String, Pure]
  type Contexts = Contexts.T
  object Contexts extends Newtype[String, Pure]

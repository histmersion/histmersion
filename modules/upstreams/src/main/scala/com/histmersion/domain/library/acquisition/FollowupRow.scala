package com.histmersion
package domain.library.acquisition

import com.histmersion.plugin.Year
import com.histmersion.plugin.Doi
import com.histmersion.plugin.WorkKey
import com.histmersion.upstreams.ArxivId
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
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
  type Title = WorkKey.Title
  val Title: WorkKey.Title.type = WorkKey.Title
  type Arxiv = ArxivId
  val Arxiv: ArxivId.type = ArxivId
  type CitedBy = CitedBy.T
  object CitedBy extends RefinedType[Int, Pure]
  type IsInfluential = IsInfluential.T
  object IsInfluential extends RefinedType[Boolean, Pure]
  type Intents = Intents.T
  object Intents extends RefinedType[String, Pure]
  type Contexts = Contexts.T
  object Contexts extends RefinedType[String, Pure]

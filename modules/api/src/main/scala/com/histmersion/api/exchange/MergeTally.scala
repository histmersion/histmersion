package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeTally(
    sourcesMerged: MergeTally.SourcesMerged = MergeTally.SourcesMerged(0),
    sourcesByScore: MergeTally.SourcesByScore = MergeTally.SourcesByScore(0),
    sourcesRenamed: MergeTally.SourcesRenamed = MergeTally.SourcesRenamed(0),
    aliases: MergeTally.Aliases = MergeTally.Aliases(0),
    findingsMerged: MergeTally.FindingsMerged = MergeTally.FindingsMerged(0),
    assetsMerged: MergeTally.AssetsMerged = MergeTally.AssetsMerged(0),
    attestationsReanchored: MergeTally.AttestationsReanchored = MergeTally.AttestationsReanchored(0),
    attestationsDropped: MergeTally.AttestationsDropped = MergeTally.AttestationsDropped(0),
    selfLoopsDropped: MergeTally.SelfLoopsDropped = MergeTally.SelfLoopsDropped(0),
    articlesThreeWay: MergeTally.ArticlesThreeWay = MergeTally.ArticlesThreeWay(0),
    articlesFastForward: MergeTally.ArticlesFastForward = MergeTally.ArticlesFastForward(0),
    articlesBoth: MergeTally.ArticlesBoth = MergeTally.ArticlesBoth(0),
    articlesFromFile: MergeTally.ArticlesFromFile = MergeTally.ArticlesFromFile(0),
    eventsMerged: MergeTally.EventsMerged = MergeTally.EventsMerged(0),
    goalsMerged: MergeTally.GoalsMerged = MergeTally.GoalsMerged(0),
    queueMerged: MergeTally.QueueMerged = MergeTally.QueueMerged(0),
    redirects: MergeTally.Redirects = MergeTally.Redirects(0),
    placesMerged: MergeTally.PlacesMerged = MergeTally.PlacesMerged(0),
    periodsMerged: MergeTally.PeriodsMerged = MergeTally.PeriodsMerged(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeTally:
  type SourcesMerged = SourcesMerged.T
  object SourcesMerged extends RefinedType[Int, Pure]
  type SourcesByScore = SourcesByScore.T
  object SourcesByScore extends RefinedType[Int, Pure]
  type SourcesRenamed = SourcesRenamed.T
  object SourcesRenamed extends RefinedType[Int, Pure]
  type Aliases = Aliases.T
  object Aliases extends RefinedType[Int, Pure]
  type FindingsMerged = FindingsMerged.T
  object FindingsMerged extends RefinedType[Int, Pure]
  type AssetsMerged = AssetsMerged.T
  object AssetsMerged extends RefinedType[Int, Pure]
  type AttestationsReanchored = AttestationsReanchored.T
  object AttestationsReanchored extends RefinedType[Int, Pure]
  type AttestationsDropped = AttestationsDropped.T
  object AttestationsDropped extends RefinedType[Int, Pure]
  type SelfLoopsDropped = SelfLoopsDropped.T
  object SelfLoopsDropped extends RefinedType[Int, Pure]
  type ArticlesThreeWay = ArticlesThreeWay.T
  object ArticlesThreeWay extends RefinedType[Int, Pure]
  type ArticlesFastForward = ArticlesFastForward.T
  object ArticlesFastForward extends RefinedType[Int, Pure]
  type ArticlesBoth = ArticlesBoth.T
  object ArticlesBoth extends RefinedType[Int, Pure]
  type ArticlesFromFile = ArticlesFromFile.T
  object ArticlesFromFile extends RefinedType[Int, Pure]
  type EventsMerged = EventsMerged.T
  object EventsMerged extends RefinedType[Int, Pure]
  type GoalsMerged = GoalsMerged.T
  object GoalsMerged extends RefinedType[Int, Pure]
  type QueueMerged = QueueMerged.T
  object QueueMerged extends RefinedType[Int, Pure]
  type Redirects = Redirects.T
  object Redirects extends RefinedType[Int, Pure]
  type PlacesMerged = PlacesMerged.T
  object PlacesMerged extends RefinedType[Int, Pure]
  type PeriodsMerged = PeriodsMerged.T
  object PeriodsMerged extends RefinedType[Int, Pure]

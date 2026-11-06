package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeTally(
    sourcesMerged: MergeTally.SourcesMerged = _root_.com.histmersion.api.exchange.MergeTally.SourcesMerged(0),
    sourcesByScore: MergeTally.SourcesByScore = _root_.com.histmersion.api.exchange.MergeTally.SourcesByScore(0),
    sourcesRenamed: MergeTally.SourcesRenamed = _root_.com.histmersion.api.exchange.MergeTally.SourcesRenamed(0),
    aliases: MergeTally.Aliases = _root_.com.histmersion.api.exchange.MergeTally.Aliases(0),
    findingsMerged: MergeTally.FindingsMerged = _root_.com.histmersion.api.exchange.MergeTally.FindingsMerged(0),
    assetsMerged: MergeTally.AssetsMerged = _root_.com.histmersion.api.exchange.MergeTally.AssetsMerged(0),
    attestationsReanchored: MergeTally.AttestationsReanchored = _root_.com.histmersion.api.exchange.MergeTally.AttestationsReanchored(0),
    attestationsDropped: MergeTally.AttestationsDropped = _root_.com.histmersion.api.exchange.MergeTally.AttestationsDropped(0),
    selfLoopsDropped: MergeTally.SelfLoopsDropped = _root_.com.histmersion.api.exchange.MergeTally.SelfLoopsDropped(0),
    articlesThreeWay: MergeTally.ArticlesThreeWay = _root_.com.histmersion.api.exchange.MergeTally.ArticlesThreeWay(0),
    articlesFastForward: MergeTally.ArticlesFastForward = _root_.com.histmersion.api.exchange.MergeTally.ArticlesFastForward(0),
    articlesBoth: MergeTally.ArticlesBoth = _root_.com.histmersion.api.exchange.MergeTally.ArticlesBoth(0),
    articlesFromFile: MergeTally.ArticlesFromFile = _root_.com.histmersion.api.exchange.MergeTally.ArticlesFromFile(0),
    eventsMerged: MergeTally.EventsMerged = _root_.com.histmersion.api.exchange.MergeTally.EventsMerged(0),
    goalsMerged: MergeTally.GoalsMerged = _root_.com.histmersion.api.exchange.MergeTally.GoalsMerged(0),
    queueMerged: MergeTally.QueueMerged = _root_.com.histmersion.api.exchange.MergeTally.QueueMerged(0),
    redirects: MergeTally.Redirects = _root_.com.histmersion.api.exchange.MergeTally.Redirects(0),
    placesMerged: MergeTally.PlacesMerged = _root_.com.histmersion.api.exchange.MergeTally.PlacesMerged(0),
    periodsMerged: MergeTally.PeriodsMerged = _root_.com.histmersion.api.exchange.MergeTally.PeriodsMerged(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeTally:
  type SourcesMerged = SourcesMerged.T
  object SourcesMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesByScore = SourcesByScore.T
  object SourcesByScore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesRenamed = SourcesRenamed.T
  object SourcesRenamed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Aliases = Aliases.T
  object Aliases extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingsMerged = FindingsMerged.T
  object FindingsMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetsMerged = AssetsMerged.T
  object AssetsMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttestationsReanchored = AttestationsReanchored.T
  object AttestationsReanchored extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttestationsDropped = AttestationsDropped.T
  object AttestationsDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelfLoopsDropped = SelfLoopsDropped.T
  object SelfLoopsDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesThreeWay = ArticlesThreeWay.T
  object ArticlesThreeWay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesFastForward = ArticlesFastForward.T
  object ArticlesFastForward extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesBoth = ArticlesBoth.T
  object ArticlesBoth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesFromFile = ArticlesFromFile.T
  object ArticlesFromFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsMerged = EventsMerged.T
  object EventsMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsMerged = GoalsMerged.T
  object GoalsMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueMerged = QueueMerged.T
  object QueueMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Redirects = Redirects.T
  object Redirects extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesMerged = PlacesMerged.T
  object PlacesMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsMerged = PeriodsMerged.T
  object PeriodsMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

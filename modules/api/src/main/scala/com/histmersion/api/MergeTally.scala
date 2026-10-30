package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MergeTally(
    sourcesMerged: Int = 0,
    sourcesByScore: Int = 0,
    sourcesRenamed: Int = 0,
    aliases: Int = 0,
    findingsMerged: Int = 0,
    assetsMerged: Int = 0,
    attestationsReanchored: Int = 0,
    attestationsDropped: Int = 0,
    selfLoopsDropped: Int = 0,
    articlesThreeWay: Int = 0,
    articlesFastForward: Int = 0,
    articlesBoth: Int = 0,
    articlesFromFile: Int = 0,
    eventsMerged: Int = 0,
    goalsMerged: Int = 0,
    queueMerged: Int = 0,
    redirects: Int = 0,
    placesMerged: Int = 0,
    periodsMerged: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeTally:
  given Schema[MergeTally] = summon[KindlingsSchema[MergeTally]].schema

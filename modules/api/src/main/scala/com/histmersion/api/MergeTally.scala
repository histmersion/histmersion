package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What an apply did beyond inserting and updating rows (§6.4): the folds of matched records. */
final case class MergeTally(
    /** Bundle sources folded into a source here. */
    sourcesMerged: Int = 0,
    /** …of them matched by score rather than a strong key or an identity. */
    sourcesByScore: Int = 0,
    /** Sources given a new citekey (`rename-left`/`rename-right`). */
    sourcesRenamed: Int = 0,
    /** Old citekeys added (the bundle's citekey of a merged source among them). */
    aliases: Int = 0,
    findingsMerged: Int = 0,
    assetsMerged: Int = 0,
    attestationsReanchored: Int = 0,
    attestationsDropped: Int = 0,
    /** Self-loops a merge would have made (`A superseded_by A′`), left out. */
    selfLoopsDropped: Int = 0,
    articlesThreeWay: Int = 0,
    articlesFastForward: Int = 0,
    /** Articles kept twice, the bundle's under another slug (`both`). */
    articlesBoth: Int = 0,
    articlesFromFile: Int = 0,
    eventsMerged: Int = 0,
    goalsMerged: Int = 0,
    queueMerged: Int = 0,
    /** Redirects written (`uid_aliases`): one per folded record. */
    redirects: Int = 0,
    /** Places and named periods of the bundle that are one with one here (by id, or by label and where/when).
      */
    placesMerged: Int = 0,
    periodsMerged: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeTally:
  given Schema[MergeTally] = summon[KindlingsSchema[MergeTally]].schema

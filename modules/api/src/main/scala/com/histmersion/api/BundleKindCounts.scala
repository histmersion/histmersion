package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.values.BundleKind

/** What an import did (or, with `dryRun`, would do) to the entities of one kind. */
final case class BundleKindCounts(
    kind: BundleKind,
    inserted: Int = 0,
    updated: Int = 0,
    unchanged: Int = 0,
    keptLocal: Int = 0,
    conflicts: Int = 0,
    deleted: Int = 0,
    skippedDeleted: Int = 0,
    /** Held back for `merge apply`: part of a source that is the same work as one here (M3). */
    held: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def changed: Int = inserted + updated + deleted
object BundleKindCounts:
  given Schema[BundleKindCounts] = summon[KindlingsSchema[BundleKindCounts]].schema

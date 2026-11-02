package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A sync: the gaps it considered, and with `apply` each push made. */
final case class CollectionSync(
    gaps: CollectionGaps,
    applied: Boolean,
    pushes: List[CollectionSyncPush]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionSync:
  given Schema[CollectionSync] = summon[KindlingsSchema[CollectionSync]].schema

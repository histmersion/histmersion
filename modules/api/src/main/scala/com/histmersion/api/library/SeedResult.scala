package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class SeedResult(
    imported: SeedResult.Imported,
    failed: List[SeedResult.Failed],
    edgesResolved: SeedResult.EdgesResolved,
    skipped: List[SeedResult.Skipped] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object SeedResult:
  type Imported = Imported.T
  object Imported extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[String, Pure]
  type EdgesResolved = EdgesResolved.T
  object EdgesResolved extends Newtype[Int, Pure]
  type Skipped = Skipped.T
  object Skipped extends Newtype[String, Pure]

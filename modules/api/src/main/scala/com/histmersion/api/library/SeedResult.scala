package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SeedResult:
  type Imported = Imported.T
  object Imported extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type EdgesResolved = EdgesResolved.T
  object EdgesResolved extends Newtype[Int, Pure]
  type Skipped = Skipped.T
  object Skipped extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

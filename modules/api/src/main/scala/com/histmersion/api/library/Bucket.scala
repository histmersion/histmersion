package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class Bucket(
    label: Bucket.Label,
    key: Bucket.Key,
    kind: Bucket.Kind,
    start: Option[Year] = None,
    end: Option[Year] = None,
    placeId: Option[Bucket.PlaceId] = None,
    count: Bucket.Count = Bucket.Count(0),
    overlap: Bucket.Overlap = Bucket.Overlap(0),
    contested: Bucket.Contested = Bucket.Contested(false),
    alsoUnder: List[Bucket.AlsoUnder] = Nil,
    children: List[Bucket] = Nil,
    samples: List[Bucket.Sample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Bucket:
  /** [[Bucket]] is recursive, so its schema is a `KindlingsSchema[Bucket]` (the `derives` clause), not a given
    * `Schema[Bucket]`: a typed `given` is visible in its own right-hand side, so the derivation would find it at the
    * `children` field and the lazy val would wait on itself forever on first use (tapir's own `Schema.derived` hangs
    * the same way). Ask for it with `Bucket.schema`; do not add a given `Schema[Bucket]` here.
    */
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export com.histmersion.api.catalogue.Place.Id as PlaceId
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Overlap = Overlap.T
  object Overlap extends Newtype[Int, Pure]
  type Contested = Contested.T
  object Contested extends Newtype[Boolean, Pure]
  type AlsoUnder = AlsoUnder.T
  object AlsoUnder extends Newtype[String, Pure]

  def schema: Schema[Bucket] = summon[KindlingsSchema[Bucket]].schema

  final case class Sample(citekey: Citekey, title: Title)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

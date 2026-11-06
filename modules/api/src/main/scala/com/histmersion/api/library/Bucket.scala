package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Bucket(
    label: Bucket.Label,
    key: Bucket.Key,
    kind: Bucket.Kind,
    start: Option[Bucket.Start] = None,
    end: Option[Bucket.End] = None,
    placeId: Option[Bucket.PlaceId] = None,
    count: Bucket.Count = _root_.com.histmersion.api.library.Bucket.Count(0),
    overlap: Bucket.Overlap = _root_.com.histmersion.api.library.Bucket.Overlap(0),
    contested: Bucket.Contested = _root_.com.histmersion.api.library.Bucket.Contested(false),
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
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Start = Start.T
  object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type End = End.T
  object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceId = PlaceId.T
  object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Overlap = Overlap.T
  object Overlap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contested = Contested.T
  object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AlsoUnder = AlsoUnder.T
  object AlsoUnder extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def schema: Schema[Bucket] = summon[KindlingsSchema[Bucket]].schema

  final case class Sample(citekey: com.histmersion.api.library.Citekey, title: com.histmersion.api.library.Title) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

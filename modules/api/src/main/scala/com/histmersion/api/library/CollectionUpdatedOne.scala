package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** An item rewritten from a record: the fields written (none when it already agreed) and the record's title. */
final case class CollectionUpdatedOne(
    citekey: com.histmersion.api.library.Citekey,
    id: CollectionUpdatedOne.Id,
    changed: List[CollectionUpdatedOne.Changed],
    title: com.histmersion.api.library.Title
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionUpdatedOne:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changed = Changed.T
  object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

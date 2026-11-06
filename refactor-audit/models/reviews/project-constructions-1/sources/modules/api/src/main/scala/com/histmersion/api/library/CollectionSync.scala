package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A sync: the gaps it considered, and with `apply` each push made. */
final case class CollectionSync(
    gaps: CollectionGaps,
    applied: CollectionSync.Applied,
    pushes: List[com.histmersion.api.library.CollectionSync.Push]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionSync:


  /** One push of a sync: the item it became, or why it failed. */
  type Applied = Applied.T
  object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Push(
      citekey: com.histmersion.api.library.Citekey,
      id: Option[Push.Id],
      error: Option[Push.Error]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Push:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  /** Push every counted gap into a collection (a dry run unless `apply`), considering at most `limit` gaps. */
  final case class Request(
      apply: Request.Apply = _root_.com.histmersion.api.library.CollectionSync.Request.Apply(false),
      limit: Option[Request.Limit] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type Apply = Apply.T
    object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

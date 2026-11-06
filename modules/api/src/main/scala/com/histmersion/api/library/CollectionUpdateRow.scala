package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** One item of an update of every linked item: its id, the sources linked to it, its state (`would-change`, `updated`,
  * `failed`, `up-to-date`, `shared`, `missing`, `refused`), the changes and why it failed or was refused.
  */
final case class CollectionUpdateRow(
    id: CollectionUpdateRow.Id,
    citekeys: List[CollectionUpdateRow.Citekeys],
    state: CollectionUpdateRow.State,
    changes: List[CollectionFieldChange],
    reason: Option[CollectionUpdateRow.Reason]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionUpdateRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

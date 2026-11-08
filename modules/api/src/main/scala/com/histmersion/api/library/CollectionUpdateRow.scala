package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Id extends RefinedType[String, Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends RefinedType[String, Pure]
  type State = State.T
  object State extends RefinedType[String, Pure]
  type Reason = Reason.T
  object Reason extends RefinedType[String, Pure]

package com.histmersion
package api.backup
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

/** `force` is required while the database holds sources: `pg_restore --clean` drops what is there. */
final case class RestoreRequest(file: RestoreRequest.File, force: RestoreRequest.Force = RestoreRequest.Force.Safe)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RestoreRequest:
  export Backup.Result.File
  enum Force derives CanEqual:
    case Force, Safe
  object Force extends TwoStates[Force]:
    extension (force: Force) def force: Boolean = force == Force

package com.histmersion
package api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** `force` is required while the database holds sources: `pg_restore --clean` drops what is there. */
final case class RestoreRequest(file: RestoreRequest.File, force: RestoreRequest.Force = RestoreRequest.Force(false)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object RestoreRequest:
  export Backup.Result.File
  type Force = Force.T
  object Force extends Newtype[Boolean, Pure]

package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** `force` is required while the database holds sources: `pg_restore --clean` drops what is there. */
final case class RestoreRequest(file: RestoreRequest.File, force: RestoreRequest.Force = _root_.com.histmersion.api.backup.RestoreRequest.Force(false)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object RestoreRequest:
  type File = com.histmersion.api.backup.Backup.Result.File
  val File: _root_.com.histmersion.api.backup.Backup.Result.File.type = _root_.com.histmersion.api.backup.Backup.Result.File
  type Force = Force.T
  object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

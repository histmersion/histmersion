package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class Health(
    status: Health.Status,
    db: Health.Db,
    schemaVersion: Option[Health.SchemaVersion],
    converter: Option[Health.Converter] = None,
    warnings: List[Health.Warnings] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Health:
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Db = Db.T
  object Db extends RefinedType[String, Pure]
  type SchemaVersion = SchemaVersion.T
  object SchemaVersion extends RefinedType[String, Pure]
  type Converter = Converter.T
  object Converter extends RefinedType[String, Pure]
  type Warnings = Warnings.T
  object Warnings extends RefinedType[String, Pure]

package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
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
  object Status extends Newtype[String, Pure]
  type Db = Db.T
  object Db extends Newtype[String, Pure]
  type SchemaVersion = SchemaVersion.T
  object SchemaVersion extends Newtype[String, Pure]
  type Converter = Converter.T
  object Converter extends Newtype[String, Pure]
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]

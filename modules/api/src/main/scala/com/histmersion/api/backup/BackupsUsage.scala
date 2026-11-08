package com.histmersion
package api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.ByteCount
import io.github.iltotore.iron.constraint.any.Pure

final case class BackupsUsage(
    count: BackupsUsage.Count,
    bytes: ByteCount,
    newest: Option[BackupsUsage.Newest],
    keep: BackupsUsage.Keep
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object BackupsUsage:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Newest = Newest.T
  object Newest extends Newtype[String, Pure]
  type Keep = Keep.T
  object Keep extends Newtype[Int, Pure]

package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A kind's part of an export: its entry's NDJSON payload, the newest change its lines carry, and its blobs. */
final case class CarriedOut(payload: CarriedOut.Payload, asOf: Option[CarriedOut.AsOf], blobs: List[BlobOut] = Nil)

object CarriedOut:
  type Payload = Payload.T
  object Payload extends RefinedType[Array[Byte], Pure]

  type AsOf = AsOf.T
  object AsOf extends RefinedType[String, Pure]

package com.histmersion.util.data.exchange

/** A kind's part of an export: its entry's NDJSON payload, the newest change its lines carry, and its blobs. */
final case class CarriedOut(payload: Array[Byte], asOf: Option[CarriedOut.AsOf], blobs: List[BlobOut] = Nil)

object CarriedOut:
  type AsOf = AsOf.T
  object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

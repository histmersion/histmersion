package com.histmersion.util.data.exchange

/** A kind's part of an export: its entry's NDJSON payload, the newest change its lines carry, and its blobs. */
final case class CarriedOut(payload: Array[Byte], asOf: Option[String], blobs: List[BlobOut] = Nil)

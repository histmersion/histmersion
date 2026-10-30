package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** ATTESTATIONS (plan §1.3): granular source references linking entities to specific passages. W3C Web Annotation
  * selectors (TextQuoteSelector, TextPositionSelector), page labels, IIIF canvas+xywh, CTS URNs, and source_chunks
  * ordinals.
  */

// ── vocabulary ─────────────────────────────────────────────────────────────────────────────────

// ── DTOs ───────────────────────────────────────────────────────────────────────────────────────

final case class Attestation(
    id: Long,
    citekey: String,
    targetType: String,
    targetId: String,
    quote: Option[String] = None,
    prefix: Option[String] = None,
    suffix: Option[String] = None,
    charStart: Option[Int] = None,
    charEnd: Option[Int] = None,
    chunkOrd: Option[Int] = None,
    page: Option[String] = None,
    canvas: Option[String] = None,
    xywh: Option[String] = None,
    ctsUrn: Option[String] = None,
    note: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object Attestation:
  given Schema[Attestation] = summon[KindlingsSchema[Attestation]].schema

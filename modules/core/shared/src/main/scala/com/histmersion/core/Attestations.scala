package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** ATTESTATIONS (plan §1.3): granular source references linking entities to specific passages.
  * W3C Web Annotation selectors (TextQuoteSelector, TextPositionSelector), page labels, IIIF
  * canvas+xywh, CTS URNs, and source_chunks ordinals. */

// ── vocabulary ─────────────────────────────────────────────────────────────────────────────────

object AttestationTarget:
  val Finding     = "finding"
  val Event       = "event"
  val EventDating = "event_dating"
  val Place       = "place"
  val Edge        = "edge"
  val all: List[String] = List(Finding, Event, EventDating, Place, Edge)

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
) derives KindlingsCodecAsObject

object Attestation:
  given Schema[Attestation] = KindlingsSchema.derived[Attestation].schema

final case class AttestationPut(
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
)

object AttestationPut:
  given Schema[AttestationPut] = KindlingsSchema.derived[AttestationPut].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[AttestationPut] = StrictJson.codec(KindlingsCodecAsObject.derived[AttestationPut])

final case class AttestationPatch(
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
)

object AttestationPatch:
  given Schema[AttestationPatch] = KindlingsSchema.derived[AttestationPatch].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[AttestationPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[AttestationPatch])

final case class AttestationList(
    total: Int,
    attestations: List[Attestation],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, attestations.size)

object AttestationList:
  given Schema[AttestationList] = KindlingsSchema.derived[AttestationList].schema
  def of(total: Int, rows: List[Attestation], offset: Int): AttestationList =
    AttestationList(total, rows, offset, OffsetPage(total, offset, rows.size).next)

final case class AttestationDeleted(
    id: Long,
    deleted: Boolean
) derives KindlingsCodecAsObject

object AttestationDeleted:
  given Schema[AttestationDeleted] = KindlingsSchema.derived[AttestationDeleted].schema

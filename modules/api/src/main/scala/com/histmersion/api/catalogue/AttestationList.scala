package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given

final case class AttestationList(
    total: Int,
    attestations: List[Attestation],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, attestations.size)

object AttestationList:
  given Schema[AttestationList] = summon[KindlingsSchema[AttestationList]].schema
  def of(total: Int, rows: List[Attestation], offset: Int): AttestationList =
    AttestationList(total, rows, offset, OffsetPage(total, offset, rows.size).next)

package com.histmersion.upstreams.europepmc
final case class EuropePmcHit(pmcid: Option[EuropePmcHit.Pmcid], isOpenAccess: EuropePmcHit.IsOpenAccess, pdfUrls: List[EuropePmcHit.PdfUrls])

object EuropePmcHit:
  type Pmcid = com.histmersion.domain.library.Pmcid
  val Pmcid: _root_.com.histmersion.domain.library.Pmcid.type = _root_.com.histmersion.domain.library.Pmcid
  type IsOpenAccess = IsOpenAccess.T
  object IsOpenAccess extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PdfUrls = PdfUrls.T
  object PdfUrls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

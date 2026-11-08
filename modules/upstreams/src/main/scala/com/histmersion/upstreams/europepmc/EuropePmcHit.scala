package com.histmersion
package upstreams.europepmc

import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class EuropePmcHit(pmcid: Option[EuropePmcHit.Pmcid], isOpenAccess: EuropePmcHit.IsOpenAccess, pdfUrls: List[EuropePmcHit.PdfUrls])

object EuropePmcHit:
  type Pmcid = WorkKey.Pmcid
  val Pmcid: WorkKey.Pmcid.type = WorkKey.Pmcid
  type IsOpenAccess = IsOpenAccess.T
  object IsOpenAccess extends RefinedType[Boolean, Pure]
  type PdfUrls = PdfUrls.T
  object PdfUrls extends RefinedType[String, Pure]

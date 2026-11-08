package com.histmersion
package upstreams.pmc

import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.constraint.any.Pure
final case class PmcRecord(
    pmcid: PmcRecord.Pmcid,
    openAccess: PmcRecord.OpenAccess,
    manuscript: PmcRecord.Manuscript,
    license: Option[PmcRecord.License],
    pdf: Option[PmcObject],
    xml: Option[PmcObject]
)

object PmcRecord:
  type Pmcid = WorkKey.Pmcid
  val Pmcid: WorkKey.Pmcid.type = WorkKey.Pmcid
  type OpenAccess = OpenAccess.T
  object OpenAccess extends Newtype[Boolean, Pure]
  type Manuscript = Manuscript.T
  object Manuscript extends Newtype[Boolean, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]

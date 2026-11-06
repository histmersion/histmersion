package com.histmersion.upstreams.pmc
final case class PmcRecord(
    pmcid: PmcRecord.Pmcid,
    openAccess: PmcRecord.OpenAccess,
    manuscript: PmcRecord.Manuscript,
    license: Option[PmcRecord.License],
    pdf: Option[PmcObject],
    xml: Option[PmcObject]
)

object PmcRecord:
  type Pmcid = com.histmersion.plugin.WorkKey.Pmcid
  val Pmcid: _root_.com.histmersion.plugin.WorkKey.Pmcid.type = _root_.com.histmersion.plugin.WorkKey.Pmcid
  type OpenAccess = OpenAccess.T
  object OpenAccess extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Manuscript = Manuscript.T
  object Manuscript extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type License = License.T
  object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

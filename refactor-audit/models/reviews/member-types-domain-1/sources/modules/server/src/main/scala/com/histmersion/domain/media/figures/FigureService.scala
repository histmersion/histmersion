package com.histmersion.domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.database.Transactor

/** A source's figures take the source's licence: the figures with no licence whose source records one, counted, and
  * with `apply` given it.
  */
trait FigureService:
  def fillLicences(apply: com.histmersion.domain.media.figures.FigureService.FillLicencesApply): FigureLicenceFill

object FigureService:
  type FillLicencesApply = FillLicencesApply.T
  object FillLicencesApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): FigureService = Implementation(db)

  final private class Implementation(db: Transactor) extends FigureService:
    def fillLicences(apply: com.histmersion.domain.media.figures.FigureService.Implementation.FillLicencesApply): FigureLicenceFill =
      if apply then db.transact(FigureRepo.fillLicences(apply = true))
      else db.read(FigureRepo.fillLicences(apply = false))

  object Implementation:
    type FillLicencesApply = FillLicencesApply.T
    object FillLicencesApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Media's rows alone, in memory: no source is held, so no figure has a source licence to take. */
  final class InMemory extends FigureService:
    def fillLicences(apply: com.histmersion.domain.media.figures.FigureService.InMemory.FillLicencesApply): FigureLicenceFill = FigureLicenceFill(0, 0, apply, Nil)

  object InMemory:
    type FillLicencesApply = FillLicencesApply.T
    object FillLicencesApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

package com.histmersion.domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.database.Transactor

/** A source's figures take the source's licence: the figures with no licence whose source records one, counted, and
  * with `apply` given it.
  */
trait FigureService:
  def fillLicences(apply: com.histmersion.domain.media.figures.FigureService.Apply): FigureLicenceFill

object FigureService:
  type Apply = Apply.T
  object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): FigureService = Implementation(db)

  final private class Implementation(db: Transactor) extends FigureService:
    def fillLicences(apply: com.histmersion.domain.media.figures.FigureService.Apply): FigureLicenceFill =
      if apply.value then db.transact(FigureRepo.fillLicences(apply = com.histmersion.api.media.FigureLicenceFill.Applied(true)))
      else db.read(FigureRepo.fillLicences(apply = com.histmersion.api.media.FigureLicenceFill.Applied(false)))


  /** Media's rows alone, in memory: no source is held, so no figure has a source licence to take. */
  final class InMemory extends FigureService:
    def fillLicences(apply: com.histmersion.domain.media.figures.FigureService.Apply): FigureLicenceFill = FigureLicenceFill(com.histmersion.api.media.FigureLicenceFill.Candidates(0), com.histmersion.api.media.FigureLicenceFill.Updated(0), com.histmersion.api.media.FigureLicenceFill.Applied(apply.value), Nil)


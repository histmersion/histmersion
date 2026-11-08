package com.histmersion
package domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.database.Transactor
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A source's figures take the source's licence: the figures with no licence whose source records one, counted, and
  * with `apply` given it.
  */
trait FigureService:
  def fillLicences(apply: FigureService.Apply): FigureLicenceFill

object FigureService:
  type Apply = Apply.T
  object Apply extends RefinedType[Boolean, Pure]

  def apply(db: Transactor): FigureService = Implementation(db)

  final private class Implementation(db: Transactor) extends FigureService:
    def fillLicences(apply: Apply): FigureLicenceFill =
      if apply.value then db.transact(FigureRepo.fillLicences(apply = FigureLicenceFill.Applied(true)))
      else db.read(FigureRepo.fillLicences(apply = FigureLicenceFill.Applied(false)))


  /** Media's rows alone, in memory: no source is held, so no figure has a source licence to take. */
  final class InMemory extends FigureService:
    def fillLicences(apply: Apply): FigureLicenceFill = FigureLicenceFill(FigureLicenceFill.Candidates(0), FigureLicenceFill.Updated(0), FigureLicenceFill.Applied(apply.value), Nil)


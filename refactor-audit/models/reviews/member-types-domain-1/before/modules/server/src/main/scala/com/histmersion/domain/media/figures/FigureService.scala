package com.histmersion.domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.database.Transactor

/** A source's figures take the source's licence: the figures with no licence whose source records one, counted, and
  * with `apply` given it.
  */
trait FigureService:
  def fillLicences(apply: Boolean): FigureLicenceFill

object FigureService:
  def apply(db: Transactor): FigureService = Implementation(db)

  final private class Implementation(db: Transactor) extends FigureService:
    def fillLicences(apply: Boolean): FigureLicenceFill =
      if apply then db.transact(FigureRepo.fillLicences(apply = true))
      else db.read(FigureRepo.fillLicences(apply = false))

  /** Media's rows alone, in memory: no source is held, so no figure has a source licence to take. */
  final class InMemory extends FigureService:
    def fillLicences(apply: Boolean): FigureLicenceFill = FigureLicenceFill(0, 0, apply, Nil)

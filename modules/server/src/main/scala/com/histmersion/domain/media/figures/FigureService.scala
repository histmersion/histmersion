package com.histmersion
package domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.database.Transactor
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}

/** A source's figures take the source's licence: the figures with no licence whose source records one, counted, and
  * with `apply` given it.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait FigureService:
  def fillLicences(apply: FigureService.Apply)(using WritePermit): FigureLicenceFill

  /** The dry run of [[fillLicences]]: the count alone, a read (no permit). */
  def countLicences(): FigureLicenceFill

object FigureService:
  type Apply = Apply.T
  object Apply extends Newtype[Boolean, Pure]

  def apply(db: Transactor): FigureService = Implementation(db)

  final private class Implementation(db: Transactor) extends FigureService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fillLicences(apply: Apply)(using WritePermit): FigureLicenceFill =
      if apply.value then db.write(FigureRepo.fillLicences(apply = FigureLicenceFill.Applied(true)))
      else countLicences()

    def countLicences(): FigureLicenceFill = db.read(FigureRepo.fillLicences(apply = FigureLicenceFill.Applied(false)))

  /** Media's rows alone, in memory: no source is held, so no figure has a source licence to take. */
  final class InMemory extends FigureService:
    def fillLicences(apply: Apply)(using WritePermit): FigureLicenceFill = filled(apply)
    def countLicences(): FigureLicenceFill = filled(Apply(false))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def filled(apply: Apply): FigureLicenceFill = FigureLicenceFill(
      FigureLicenceFill.Candidates(0),
      FigureLicenceFill.Updated(0),
      FigureLicenceFill.Applied(apply.value),
      Nil
    )

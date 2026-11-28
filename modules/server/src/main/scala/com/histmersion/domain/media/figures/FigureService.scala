package com.histmersion
package domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.database.Transactor
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** A source's figures take the source's licence: the figures with no licence whose source records one, counted, and
  * with `apply` given it.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait FigureService:
  def fillLicences(apply: FigureService.Apply)(using WritePermit): FigureLicenceFill

  /** The dry run of [[fillLicences]]: the count alone, a read (no permit). */
  def countLicences(): FigureLicenceFill

object FigureService:
  enum Apply derives CanEqual:
    case Apply, DryRun
  object Apply extends TwoCases[Apply]:
    extension (apply: Apply) def applies: Boolean = apply == Apply

  def apply(db: Transactor): FigureService = Implementation(db)

  final private class Implementation(db: Transactor) extends FigureService:
    def fillLicences(apply: Apply)(using WritePermit): FigureLicenceFill =
      if apply.applies then db.write(FigureRepo.fillLicences(apply = FigureLicenceFill.Applied.Applied))
      else countLicences()

    def countLicences(): FigureLicenceFill =
      db.read(FigureRepo.fillLicences(apply = FigureLicenceFill.Applied.Previewed))

  /** Media's rows alone, in memory: no source is held, so no figure has a source licence to take. */
  final class InMemory extends FigureService:
    def fillLicences(apply: Apply)(using WritePermit): FigureLicenceFill = filled(apply)
    def countLicences(): FigureLicenceFill = filled(Apply.DryRun)
    private def filled(apply: Apply): FigureLicenceFill = FigureLicenceFill(
      FigureLicenceFill.Candidates(0),
      FigureLicenceFill.Updated(0),
      (if apply.applies then FigureLicenceFill.Applied.Applied else FigureLicenceFill.Applied.Previewed),
      Nil
    )

package com.histmersion.domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.Api

/** `media fill-licences`: the figures with no licence take their source's — counted, and written with `apply`. */
final class FiguresControllers(figures: FigureService, maintenance: WriteGate):
  def fillLicences(apply: com.histmersion.domain.media.figures.FiguresControllers.FillLicencesApply): Either[Api.Error, FigureLicenceFill] =
    if apply then maintenance.guard("media_fill_licences")(Right(figures.fillLicences(apply = true)))
    else Right(figures.fillLicences(apply = false))

object FiguresControllers:
  type FillLicencesApply = FillLicencesApply.T
  object FillLicencesApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

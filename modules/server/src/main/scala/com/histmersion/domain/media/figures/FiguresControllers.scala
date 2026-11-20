package com.histmersion
package domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.Api

/** `media fill-licences`: the figures with no licence take their source's — counted, and written with `apply`. */
final class FiguresControllers(figures: FigureService, maintenance: WriteGate):
  def fillLicences(apply: FigureService.Apply): Either[Api.Error, FigureLicenceFill] =
    if apply.value then
      maintenance.guard(WriteGate.OperationName("media_fill_licences"))(
        Right(figures.fillLicences(apply = FigureService.Apply(true)))
      )
    else Right(figures.countLicences()) // the count alone is a read: it answers while a restore runs

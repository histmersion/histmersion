package com.histmersion.domain.media.figures

import com.histmersion.api.{ApiError, FigureLicenceFill}
import com.histmersion.util.data.maintenance.WriteGate

/** `media fill-licences`: the figures with no licence take their source's — counted, and written with `apply`. */
final class FiguresControllers(figures: FigureService, maintenance: WriteGate):
  def fillLicences(apply: Boolean): Either[ApiError, FigureLicenceFill] =
    if apply then maintenance.guard("media_fill_licences")(Right(figures.fillLicences(apply = true)))
    else Right(figures.fillLicences(apply = false))

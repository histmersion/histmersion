package com.histmersion.domain.media.figures

import com.histmersion.api.media.FigureLicenceFill

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The figures' repository as the db suites reach it. */
object FigureFixtures:
  def fillLicences(ds: DataSource, apply: FigureLicenceFill.Applied): FigureLicenceFill =
    transact(ds)(FigureRepo.fillLicences(apply))

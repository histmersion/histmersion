package com.histmersion.domain.media.figures

import com.histmersion.api.FigureLicenceFill

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The figures' repository as the db suites reach it. */
object FigureFixtures:
  def fillLicences(ds: DataSource, apply: Boolean): FigureLicenceFill = transact(ds)(FigureRepo.fillLicences(apply))

package com.histmersion.domain.media.covers

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The covers' repository as the db suites reach it from outside the feature. */
object CoverFixtures:
  def illustrationCandidates(ds: DataSource, only: Option[String], refresh: Boolean, limit: Int): List[String] =
    connect(ds)(CoverRepo.illustrationCandidates(only, refresh, limit))
  def coverCandidates(ds: DataSource, only: Option[String], refresh: Boolean, limit: Int): List[CoverKeys] =
    connect(ds)(CoverRepo.coverCandidates(only, refresh, limit))

package com.histmersion.domain.media.covers

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The covers' repository as the db suites reach it from outside the feature. */
object CoverFixtures:
  export CoverRepo.{Limit, Only, Refresh}

  def illustrationCandidates(
      ds: DataSource,
      only: Option[CoverRepo.Only],
      refresh: CoverRepo.Refresh,
      limit: CoverRepo.Limit
  ): List[String] =
    connect(ds)(CoverRepo.illustrationCandidates(only, refresh, limit))
  def coverCandidates(
      ds: DataSource,
      only: Option[CoverRepo.Only],
      refresh: CoverRepo.Refresh,
      limit: CoverRepo.Limit
  ): List[CoverKeys] =
    connect(ds)(CoverRepo.coverCandidates(only, refresh, limit))

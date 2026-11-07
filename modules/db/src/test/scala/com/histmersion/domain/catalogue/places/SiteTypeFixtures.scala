package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource

/** The sites' refined types as a db suite writes and reads them. */
object SiteTypeFixtures:
  export SiteTypeRepo.Refresh
  export PlaceRepo.SiteType

  def sitesToType(
      ds: DataSource,
      limit: PlaceRepo.Limit,
      refresh: SiteTypeRepo.Refresh,
      before: Option[com.histmersion.domain.DeepTime.SignedYear]
  ) =
    connect(ds)(SiteTypeRepo.sitesToType(limit, refresh, before))
  def countSitesToType(
      ds: DataSource,
      refresh: SiteTypeRepo.Refresh,
      before: Option[com.histmersion.domain.DeepTime.SignedYear]
  ): com.histmersion.RowCount =
    connect(ds)(SiteTypeRepo.countSitesToType(refresh, before))
  def setSiteTypes(
      ds: DataSource,
      types: List[(com.histmersion.domain.catalogue.Place.Id, Option[PlaceRepo.SiteType])]
  ): Unit =
    transact(ds)(SiteTypeRepo.setSiteTypes(types))

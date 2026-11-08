package com.histmersion
package domain.catalogue.places

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.Place

/** The sites' refined types as a db suite writes and reads them. */
object SiteTypeFixtures:
  export SiteTypeRepo.Refresh
  export PlaceRepo.SiteType

  def sitesToType(
      ds: DataSource,
      limit: PlaceRepo.Limit,
      refresh: SiteTypeRepo.Refresh,
      before: Option[DeepTime.SignedYear]
  ) =
    connect(ds)(SiteTypeRepo.sitesToType(limit, refresh, before))
  def countSitesToType(
      ds: DataSource,
      refresh: SiteTypeRepo.Refresh,
      before: Option[DeepTime.SignedYear]
  ): RowCount =
    connect(ds)(SiteTypeRepo.countSitesToType(refresh, before))
  def setSiteTypes(
      ds: DataSource,
      types: List[(Place.Id, Option[PlaceRepo.SiteType])]
  ): Unit =
    transact(ds)(SiteTypeRepo.setSiteTypes(types))

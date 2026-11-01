package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource

/** The sites' refined types as a db suite writes and reads them. */
object SiteTypeFixtures:
  def sitesToType(ds: DataSource, limit: Int, refresh: Boolean, before: Option[Long]) =
    connect(ds)(SiteTypeRepo.sitesToType(limit, refresh, before))
  def countSitesToType(ds: DataSource, refresh: Boolean, before: Option[Long]): Int =
    connect(ds)(SiteTypeRepo.countSitesToType(refresh, before))
  def setSiteTypes(ds: DataSource, types: List[(String, Option[String])]): Unit =
    transact(ds)(SiteTypeRepo.setSiteTypes(types))

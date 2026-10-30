package com.histmersion.server.maptiles

import com.histmersion.domain.*

import com.augustnagro.magnum.{connect, transact}

import scala.annotation.tailrec

/** One site of no known date, as the map draws it: its id, point and type. */
final case class UndatedSite(id: String, lat: Double, lon: Double, siteType: Option[String])

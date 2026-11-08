package com.histmersion
package web.map

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.instance.MapStartConfig
import com.histmersion.api.instance.MapStartMode

class MapStartSuite extends munit.FunSuite:
  private val anatolia = MapStartConfig(
    MapStartMode.Wire("last"),
    Latitude(37.0),
    Longitude(30.0),
    MapStartConfig.Zoom(5),
    MapStartConfig.Label("Anatolia")
  )

  test("the view kept in the browser reads back; nonsense is no view"):
    val s = MapStart.encode(LatLon(LatLon.Lat(37.123456), LatLon.Lon(-8.5)), MapSurface.Zoom(6.25))
    assertEquals(s, "37.12346,-8.5,6.25")
    assertEquals(MapStart.decode(s), Some((LatLon(LatLon.Lat(37.12346), LatLon.Lon(-8.5)), MapSurface.Zoom(6.25))))
    List("", "a,b,c", "95,0,3", "0,200,3", "1,2", "1,2,30")
      .map(MapStart.Encoded(_))
      .foreach(x => assertEquals(MapStart.decode(x), None, x))

  test("the map opens where it was left, else at the place; under \"place\" always at the place"):
    assertEquals(
      MapStart.initial(anatolia, Some(MapStart.Encoded("10,20,3"))),
      (LatLon(LatLon.Lat(10), LatLon.Lon(20)), 3.0)
    )
    assertEquals(MapStart.initial(anatolia, None), (LatLon(LatLon.Lat(37), LatLon.Lon(30)), 5.0))
    assertEquals(
      MapStart.initial(anatolia, Some(MapStart.Encoded("junk"))),
      (LatLon(LatLon.Lat(37), LatLon.Lon(30)), 5.0)
    )
    assertEquals(
      MapStart.initial(anatolia.modify(_.mode).setTo(MapStartMode.Wire("place")), Some(MapStart.Encoded("10,20,3"))),
      (LatLon(LatLon.Lat(37), LatLon.Lon(30)), 5.0)
    )

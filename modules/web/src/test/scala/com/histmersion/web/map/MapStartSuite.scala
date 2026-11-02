package com.histmersion.web.map

import com.histmersion.api.instance.MapStartConfig

class MapStartSuite extends munit.FunSuite:
  private val anatolia = MapStartConfig("last", 37.0, 30.0, 5, "Anatolia")

  test("the view kept in the browser reads back; nonsense is no view"):
    val s = MapStart.encode(LatLon(37.123456, -8.5), 6.25)
    assertEquals(s, "37.12346,-8.5,6.25")
    assertEquals(MapStart.decode(s), Some((LatLon(37.12346, -8.5), 6.25)))
    List("", "a,b,c", "95,0,3", "0,200,3", "1,2", "1,2,30").foreach(x => assertEquals(MapStart.decode(x), None, x))

  test("the map opens where it was left, else at the place; under \"place\" always at the place"):
    assertEquals(MapStart.initial(anatolia, Some("10,20,3")), (LatLon(10, 20), 3.0))
    assertEquals(MapStart.initial(anatolia, None), (LatLon(37, 30), 5.0))
    assertEquals(MapStart.initial(anatolia, Some("junk")), (LatLon(37, 30), 5.0))
    assertEquals(MapStart.initial(anatolia.copy(mode = "place"), Some("10,20,3")), (LatLon(37, 30), 5.0))

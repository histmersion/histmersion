package com.histmersion.domain.media.imports

import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}

/** What the imports' store answers on a collection that holds no place and no event — the same on the database and in
  * memory: a target named by id is not there, a batch is empty, nothing is pending, a check is recorded.
  */
abstract class ImportServiceContract extends MediaStoreSuite:

  stest("nothing on the map: a target by id refused, a batch empty, nothing pending, no facts") { s =>
    val i = s.imports
    assertEquals(i.targets("zq", Some(" zq_p "), None, 10, refresh = false, None), Left("No place 'zq_p'"))
    assertEquals(i.targets("zq", None, Some("zq_e"), 10, refresh = false, None), Left("No event 'zq_e'"))
    assertEquals(i.targets("zq", None, None, 10, refresh = true, None), Right(Nil))
    assertEquals(i.pending("zq", None), 0)
    assertEquals(
      i.collectionTargets("zq", places = false, Some("zq_p"), None, 10, false, None, false),
      Left("this import is about events: name one with --event")
    )
    assertEquals(
      i.collectionTargets("zq", places = true, None, Some("zq_e"), 10, false, None, false),
      Left("this import is about places: name one with --place")
    )
    assertEquals(
      i.collectionTargets("zq", places = true, Some("zq_p"), None, 10, false, None, true),
      Left("No place 'zq_p'")
    )
    assertEquals(i.collectionTargets("zq", places = true, None, None, 10, false, None, false), Right(Nil))
    assertEquals(i.collectionPending("zq", places = false, None, pleiadesOnly = false), 0)
    assertEquals(i.placeFacts(List("zq_p")), Map.empty)
    assertEquals(i.mapPlacesNamed(List("zq")), Map.empty)
    assertEquals(i.mapPlacesWithIds(List("pleiades" -> "1")), Map.empty)
    assertEquals(i.pleiadesIds(List("zq_p")), Map.empty)
    assertEquals(i.placeExternalIds(List("zq_p")), Map.empty)
    i.checked("zq", "place", "zq_p", 2, Some("Q1"))
    i.checked("zq", "place", "zq_p", 0, None)
  }

class ImportServiceInMemorySuite extends ImportServiceContract with MediaStoreSuite.InMemory
class ImportServiceDbSuite extends ImportServiceContract with MediaStoreSuite.OnDatabase

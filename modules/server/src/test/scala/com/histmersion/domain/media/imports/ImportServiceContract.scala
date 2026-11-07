package com.histmersion.domain.media.imports

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}

/** What the imports' store answers on a collection that holds no place and no event — the same on the database and in
  * memory: a target named by id is not there, a batch is empty, nothing is pending, a check is recorded.
  */
abstract class ImportServiceContract extends MediaStoreSuite:

  stest("nothing on the map: a target by id refused, a batch empty, nothing pending, no facts") { s =>
    val i = s.imports
    assertEquals(i.targets(com.histmersion.domain.media.imports.ImportService.Provider("zq"), Some(com.histmersion.domain.catalogue.Place.Id(" zq_p ")), None, com.histmersion.domain.media.imports.ImportService.TargetsLimit(10), refresh = com.histmersion.domain.media.imports.ImportService.Refresh(false), None), Left("No place 'zq_p'"))
    assertEquals(i.targets(com.histmersion.domain.media.imports.ImportService.Provider("zq"), None, Some(com.histmersion.domain.catalogue.Event.Id("zq_e")), com.histmersion.domain.media.imports.ImportService.TargetsLimit(10), refresh = com.histmersion.domain.media.imports.ImportService.Refresh(false), None), Left("No event 'zq_e'"))
    assertEquals(i.targets(com.histmersion.domain.media.imports.ImportService.Provider("zq"), None, None, com.histmersion.domain.media.imports.ImportService.TargetsLimit(10), refresh = com.histmersion.domain.media.imports.ImportService.Refresh(true), None), Right(Nil))
    assertEquals(i.pending(com.histmersion.domain.media.imports.ImportService.Provider("zq"), None), 0)
    assertEquals(
      i.collectionTargets(com.histmersion.domain.media.imports.ImportService.CheckName("zq"), places = com.histmersion.domain.media.imports.ImportService.Places(false), Some(com.histmersion.domain.catalogue.Place.Id("zq_p")), None, com.histmersion.domain.media.imports.ImportService.CollectionTargetsLimit(10), com.histmersion.domain.media.imports.ImportService.Refresh(false), None, com.histmersion.domain.media.imports.ImportService.PleiadesOnly(false)),
      Left("this import is about events: name one with --event")
    )
    assertEquals(
      i.collectionTargets(com.histmersion.domain.media.imports.ImportService.CheckName("zq"), places = com.histmersion.domain.media.imports.ImportService.Places(true), None, Some(com.histmersion.domain.catalogue.Event.Id("zq_e")), com.histmersion.domain.media.imports.ImportService.CollectionTargetsLimit(10), com.histmersion.domain.media.imports.ImportService.Refresh(false), None, com.histmersion.domain.media.imports.ImportService.PleiadesOnly(false)),
      Left("this import is about places: name one with --place")
    )
    assertEquals(
      i.collectionTargets(com.histmersion.domain.media.imports.ImportService.CheckName("zq"), places = com.histmersion.domain.media.imports.ImportService.Places(true), Some(com.histmersion.domain.catalogue.Place.Id("zq_p")), None, com.histmersion.domain.media.imports.ImportService.CollectionTargetsLimit(10), com.histmersion.domain.media.imports.ImportService.Refresh(false), None, com.histmersion.domain.media.imports.ImportService.PleiadesOnly(true)),
      Left("No place 'zq_p'")
    )
    assertEquals(i.collectionTargets(com.histmersion.domain.media.imports.ImportService.CheckName("zq"), places = com.histmersion.domain.media.imports.ImportService.Places(true), None, None, com.histmersion.domain.media.imports.ImportService.CollectionTargetsLimit(10), com.histmersion.domain.media.imports.ImportService.Refresh(false), None, com.histmersion.domain.media.imports.ImportService.PleiadesOnly(false)), Right(Nil))
    assertEquals(i.collectionPending(com.histmersion.domain.media.imports.ImportService.CheckName("zq"), places = com.histmersion.domain.media.imports.ImportService.Places(false), None, pleiadesOnly = com.histmersion.domain.media.imports.ImportService.PleiadesOnly(false)), 0)
    assertEquals(i.placeFacts(List(com.histmersion.domain.catalogue.Place.Id("zq_p"))), Map.empty)
    assertEquals(i.mapPlacesNamed(List(com.histmersion.domain.media.imports.ImportService.PlaceKey("zq"))), Map.empty)
    assertEquals(i.mapPlacesWithIds(List("pleiades" -> "1")), Map.empty)
    assertEquals(i.pleiadesIds(List(com.histmersion.domain.catalogue.Place.Id("zq_p"))), Map.empty)
    assertEquals(i.placeExternalIds(List(com.histmersion.domain.catalogue.Place.Id("zq_p"))), Map.empty)
    i.checked(com.histmersion.domain.media.imports.ImportService.Provider("zq"), com.histmersion.domain.media.imports.ImportService.TargetType("place"), com.histmersion.domain.media.imports.ImportService.TargetId("zq_p"), com.histmersion.domain.media.imports.ImportService.FoundCount(2), Some(com.histmersion.domain.catalogue.places.Wikidata.Id("Q1")))
    i.checked(com.histmersion.domain.media.imports.ImportService.Provider("zq"), com.histmersion.domain.media.imports.ImportService.TargetType("place"), com.histmersion.domain.media.imports.ImportService.TargetId("zq_p"), com.histmersion.domain.media.imports.ImportService.FoundCount(0), None)
  }

class ImportServiceInMemorySuite extends ImportServiceContract with MediaStoreSuite.InMemory
class ImportServiceDbSuite extends ImportServiceContract with MediaStoreSuite.OnDatabase

package com.histmersion
package domain.media.imports

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{tolerated, LintRule}

/** What the imports' store answers on a collection that holds no place and no event — the same on the database and in
  * memory: a target named by id is not there, a batch is empty, nothing is pending, a check is recorded.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class ImportServiceContract extends MediaStoreSuite:

  stest("nothing on the map: a target by id refused, a batch empty, nothing pending, no facts") { s =>
    val i = s.imports
    assertEquals(
      i.targets(
        ImportService.Provider("zq"),
        Some(Place.Id.assume(" zq_p ")),
        None,
        ImportService.TargetsLimit(10),
        refresh = ImportService.Refresh(false),
        None
      ).left
        .map(_.sentence),
      Left("no place id 'zq_p'")
    )
    assertEquals(
      i.targets(
        ImportService.Provider("zq"),
        None,
        Some(Event.Id("zq_e")),
        ImportService.TargetsLimit(10),
        refresh = ImportService.Refresh(false),
        None
      ).left
        .map(_.sentence),
      Left("no event id 'zq_e'")
    )
    assertEquals(
      i.targets(
        ImportService.Provider("zq"),
        None,
        None,
        ImportService.TargetsLimit(10),
        refresh = ImportService.Refresh(true),
        None
      ),
      Right(Nil)
    )
    assertEquals(i.pending(ImportService.Provider("zq"), None), 0)
    assertEquals(
      i.collectionTargets(
        ImportService.CheckName("zq"),
        places = ImportService.Places(false),
        Some(Place.Id("zq_p")),
        None,
        ImportService.CollectionTargetsLimit(10),
        ImportService.Refresh(false),
        None,
        ImportService.PleiadesOnly(false)
      ).left
        .map(_.sentence),
      Left("this import is about events: name one with --event")
    )
    assertEquals(
      i.collectionTargets(
        ImportService.CheckName("zq"),
        places = ImportService.Places(true),
        None,
        Some(Event.Id("zq_e")),
        ImportService.CollectionTargetsLimit(10),
        ImportService.Refresh(false),
        None,
        ImportService.PleiadesOnly(false)
      ).left
        .map(_.sentence),
      Left("this import is about places: name one with --place")
    )
    assertEquals(
      i.collectionTargets(
        ImportService.CheckName("zq"),
        places = ImportService.Places(true),
        Some(Place.Id("zq_p")),
        None,
        ImportService.CollectionTargetsLimit(10),
        ImportService.Refresh(false),
        None,
        ImportService.PleiadesOnly(true)
      ).left
        .map(_.sentence),
      Left("no place id 'zq_p'")
    )
    assertEquals(
      i.collectionTargets(
        ImportService.CheckName("zq"),
        places = ImportService.Places(true),
        None,
        None,
        ImportService.CollectionTargetsLimit(10),
        ImportService.Refresh(false),
        None,
        ImportService.PleiadesOnly(false)
      ),
      Right(Nil)
    )
    assertEquals(
      i.collectionPending(
        ImportService.CheckName("zq"),
        places = ImportService.Places(false),
        None,
        pleiadesOnly = ImportService.PleiadesOnly(false)
      ),
      0
    )
    assertEquals(i.placeFacts(List(Place.Id("zq_p"))), Map.empty)
    assertEquals(i.mapPlacesNamed(List(ImportService.PlaceKey("zq"))), Map.empty)
    assertEquals(i.mapPlacesWithIds(List(ExternalIds.Pleiades -> ExternalIds.ExternalId("1"))), Map.empty)
    assertEquals(i.pleiadesIds(List(Place.Id("zq_p"))), Map.empty)
    assertEquals(i.placeExternalIds(List(Place.Id("zq_p"))), Map.empty)
    i.checked(
      ImportService.Provider("zq"),
      ImportService.TargetType("place"),
      ImportService.TargetId("zq_p"),
      ImportService.FoundCount(2),
      Some(Wikidata.Id("Q1"))
    )
    i.checked(
      ImportService.Provider("zq"),
      ImportService.TargetType("place"),
      ImportService.TargetId("zq_p"),
      ImportService.FoundCount(0),
      None
    )
  }

class ImportServiceInMemorySuite extends ImportServiceContract with MediaStoreSuite.InMemory
class ImportServiceDbSuite extends ImportServiceContract with MediaStoreSuite.OnDatabase

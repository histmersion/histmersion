package com.histmersion.domain.media.covers

import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}

/** The sources a run of covers looks at, on a collection that holds no source: none, on the database and in memory. */
abstract class CoverServiceContract extends MediaStoreSuite:

  stest("no source: nothing to illustrate, no cover to ask for") { s =>
    assertEquals(s.covers.illustrationCandidates(None, refresh = true, 10), Nil)
    assertEquals(s.covers.coverCandidates(Some("zq"), refresh = false, 10), Nil)
  }

class CoverServiceInMemorySuite extends CoverServiceContract with MediaStoreSuite.InMemory
class CoverServiceDbSuite extends CoverServiceContract with MediaStoreSuite.OnDatabase

package com.histmersion.domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}

/** The figures' licence fill, on a collection whose figures' sources record no licence: nothing to fill, on the
  * database and in memory.
  */
abstract class FigureServiceContract extends MediaStoreSuite:

  stest("no source records a licence: nothing counted, nothing written") { s =>
    s.source("zqfg2001_x")
    assertEquals(s.figures.fillLicences(apply = com.histmersion.domain.media.figures.FigureService.Apply(false)), FigureLicenceFill(com.histmersion.api.media.FigureLicenceFill.Candidates(0), com.histmersion.api.media.FigureLicenceFill.Updated(0), com.histmersion.api.media.FigureLicenceFill.Applied(false), Nil))
    assertEquals(s.figures.fillLicences(apply = com.histmersion.domain.media.figures.FigureService.Apply(true)), FigureLicenceFill(com.histmersion.api.media.FigureLicenceFill.Candidates(0), com.histmersion.api.media.FigureLicenceFill.Updated(0), com.histmersion.api.media.FigureLicenceFill.Applied(true), Nil))
  }

class FigureServiceInMemorySuite extends FigureServiceContract with MediaStoreSuite.InMemory
class FigureServiceDbSuite extends FigureServiceContract with MediaStoreSuite.OnDatabase

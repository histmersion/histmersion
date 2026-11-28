package com.histmersion
package domain.media.figures

import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.domain.media.{MediaStores, MediaStoreSuite}
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{tolerated, LintRule}

/** The figures' licence fill, on a collection whose figures' sources record no licence: nothing to fill, on the
  * database and in memory.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class FigureServiceContract extends MediaStoreSuite:

  stest("no source records a licence: nothing counted, nothing written") { s =>
    s.source("zqfg2001_x")
    assertEquals(
      s.figures.fillLicences(apply = FigureService.Apply.DryRun),
      FigureLicenceFill(
        FigureLicenceFill.Candidates(0),
        FigureLicenceFill.Updated(0),
        FigureLicenceFill.Applied.Previewed,
        Nil
      )
    )
    assertEquals(
      s.figures.fillLicences(apply = FigureService.Apply.Apply),
      FigureLicenceFill(
        FigureLicenceFill.Candidates(0),
        FigureLicenceFill.Updated(0),
        FigureLicenceFill.Applied.Applied,
        Nil
      )
    )
  }

class FigureServiceInMemorySuite extends FigureServiceContract with MediaStoreSuite.InMemory
class FigureServiceDbSuite extends FigureServiceContract with MediaStoreSuite.OnDatabase

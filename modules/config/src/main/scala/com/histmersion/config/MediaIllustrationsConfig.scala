package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** How a paper's illustration is chosen among its figures (`media.illustrations`). */
final case class MediaIllustrationsConfig(
    minSide: MediaIllustrationsConfig.MinSide,
    minArea: MediaIllustrationsConfig.MinArea,
    maxAspect: MediaIllustrationsConfig.MaxAspect,
    maxRefs: MediaIllustrationsConfig.MaxRefs,
    earlyPages: MediaIllustrationsConfig.EarlyPages,
    pageScanMin: MediaIllustrationsConfig.PageScanMin
) derives ConfigReader

object MediaIllustrationsConfig:
  type MinSide = MinSide.T
  object MinSide extends RefinedType[Int, Pure]
  type MinArea = MinArea.T
  object MinArea extends RefinedType[Int, Pure]
  type MaxAspect = MaxAspect.T
  object MaxAspect extends RefinedType[Double, Pure]
  type MaxRefs = MaxRefs.T
  object MaxRefs extends RefinedType[Int, Pure]
  type EarlyPages = EarlyPages.T
  object EarlyPages extends RefinedType[Int, Pure]
  type PageScanMin = PageScanMin.T
  object PageScanMin extends RefinedType[Int, Pure]

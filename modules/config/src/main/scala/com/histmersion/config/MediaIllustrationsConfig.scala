package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
  object MinSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinArea = MinArea.T
  object MinArea extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxAspect = MaxAspect.T
  object MaxAspect extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRefs = MaxRefs.T
  object MaxRefs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EarlyPages = EarlyPages.T
  object EarlyPages extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageScanMin = PageScanMin.T
  object PageScanMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

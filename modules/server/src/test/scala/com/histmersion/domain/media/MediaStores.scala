package com.histmersion.domain.media

import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.media.covers.CoverService
import com.histmersion.domain.media.figures.FigureService
import com.histmersion.domain.media.imports.ImportService
import com.histmersion.domain.media.links.AssetLinkService

/** Media's services over one store, and how a test seeds what another domain owns there: a source its figures belong
  * to (a row of the library's on the database; nothing in memory, which holds no source).
  */
trait MediaStores:
  def assets: AssetService
  def links: AssetLinkService
  def imports: ImportService
  def covers: CoverService
  def figures: FigureService
  def source(citekey: String): Unit

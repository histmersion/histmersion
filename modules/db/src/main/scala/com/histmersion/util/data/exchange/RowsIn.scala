package com.histmersion.util.data.exchange

import com.histmersion.db.BundleImport

/** One step of an import a kind contributes: what it writes, given the import's writer, at its stage and rank. */
final case class RowsIn(stage: ImportStage, rank: ImportRank, write: BundleImport.Writer => Unit)

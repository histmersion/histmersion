package com.histmersion.util.data.exchange

import com.histmersion.domain.library.sources.referrers.SourceReferrers

/** How an import runs: a dry run writes nothing; `chunkChars` sizes the search chunks of a body it brings in and places
  * a re-anchored attestation in its chunk; `toleranceKm` is how near a finding's point must be to a place to be its;
  * `sourceReferrers` keep what every domain stores of a source right when the import renames one.
  */
final case class ImportOptions(dryRun: Boolean, chunkChars: Int, toleranceKm: Double, sourceReferrers: SourceReferrers)

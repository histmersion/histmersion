package com.histmersion.domain.library.sources

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.values.BundleKind

/** How a merge treats sources: other lines point at them by uid, and the ledger names one by its citekey. */
object SourceMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("library")
  val kind: BundleKind = BundleKind.Source
  override val ledgerLabel: Option[String] = Some("SELECT citekey FROM sources WHERE uid = l.local_uid")
  override val referable: Boolean = true

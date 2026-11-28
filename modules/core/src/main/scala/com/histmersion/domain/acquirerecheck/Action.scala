package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi

enum Action derives CanEqual:
  /** No DOI in its text: nothing to look up. */
  case NoDoi

  /** The corpus has the work with its original: the item is done. */
  case Close(citekey: Citekey)

  /** Try the legal chain — into the metadata-only source `into` when there is one (its enrichment stays). */
  case Fetch(doi: Doi, into: Option[Citekey])

package com.histmersion.util.data.exchange

import java.util.UUID

/** Which rows a bundle read covers: every one, the ones changed after a moment (`--since`), or the ones with the given
  * uids (what an import compares the bundle against).
  */
enum BundleScope:
  case All
  case Since(at: String)
  case Uids(uids: List[UUID])

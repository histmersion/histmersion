package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.util.UUID

/** Which rows a bundle read covers: every one, the ones changed after a moment (`--since`), or the ones with the given
  * uids (what an import compares the bundle against).
  */
enum BundleScope:
  case All
  case Since(at: String)
  case Uids(uids: List[UUID])

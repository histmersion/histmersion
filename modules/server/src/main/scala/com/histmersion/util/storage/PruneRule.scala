package com.histmersion
package util.storage

import com.histmersion.ByteCount

import java.time.Instant

/** Which files a store's prune removes. */
enum PruneRule:
  /** The least recently changed first, until the files left hold at most `keep`. */
  case LeastRecentlyUsed(keep: ByteCount)

  /** Every file last changed before `cutoff`. */
  case OlderThan(cutoff: Instant)

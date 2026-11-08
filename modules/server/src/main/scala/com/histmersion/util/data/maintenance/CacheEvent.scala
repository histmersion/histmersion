package com.histmersion
package util.data.maintenance

/** What makes a cache stale. */
enum CacheEvent derives CanEqual:
  /** A restore replaced the rows it was derived from: it is cleared. */
  case Restored

  /** Its time to live ran out: it is measured again. */
  case Expired

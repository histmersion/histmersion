package com.histmersion.util.data.maintenance

import com.histmersion.util.data.TableName
import com.histmersion.util.storage.Location
import com.histmersion.util.storage.Store

/** Where a cache is held. */
enum CacheHolding:
  /** In the server's memory, lost with the process. */
  case InMemory

  /** In rows of its owner's tables, rebuilt from the others. */
  case InTables(tables: List[TableName])

  /** In files below `key` of the store of `location`. */
  case InStore(location: Location, key: Store.Key)

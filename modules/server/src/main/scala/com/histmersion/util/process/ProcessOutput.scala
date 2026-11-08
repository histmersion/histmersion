package com.histmersion
package util.process

import com.histmersion.util.storage.Store

/** Where a child process's output goes. */
enum ProcessOutput derives CanEqual:
  /** Read by the launcher ([[RunningProcess.output]], or the lines of [[Processes.run]]). */
  case Piped

  /** Appended to a file of a store (a log). */
  case AppendTo(store: Store, key: Store.Key)

  /** Written to a file of a store, replacing what it held. */
  case WriteTo(store: Store, key: Store.Key)

  /** Thrown away. */
  case Discarded

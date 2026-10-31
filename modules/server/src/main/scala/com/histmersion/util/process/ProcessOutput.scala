package com.histmersion.util.process

import com.histmersion.util.storage.{Store, StoreKey}

/** Where a child process's output goes. */
enum ProcessOutput:
  /** Read by the launcher ([[RunningProcess.output]], or the lines of [[Processes.run]]). */
  case Piped

  /** Appended to a file of a store (a log). */
  case AppendTo(store: Store, key: StoreKey)

  /** Written to a file of a store, replacing what it held. */
  case WriteTo(store: Store, key: StoreKey)

  /** Thrown away. */
  case Discarded

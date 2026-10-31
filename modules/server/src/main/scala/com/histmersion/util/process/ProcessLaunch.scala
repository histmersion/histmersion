package com.histmersion.util.process

import com.histmersion.util.storage.Store

/** A child process to start: its command line, the store it runs in (its working directory), the variables added to the
  * environment it inherits, where its output goes and whether its errors go with it.
  */
final case class ProcessLaunch(
    command: List[String],
    workingStore: Option[Store] = None,
    environment: Map[String, String] = Map.empty,
    output: ProcessOutput = ProcessOutput.Piped,
    mergeErrors: Boolean = true
)

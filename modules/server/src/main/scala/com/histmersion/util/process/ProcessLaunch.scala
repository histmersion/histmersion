package com.histmersion
package util.process

import com.histmersion.util.storage.Store
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A child process to start: its command line, the store it runs in (its working directory), the variables added to the
  * environment it inherits, where its output goes and whether its errors go with it.
  */
final case class ProcessLaunch(
    command: List[ProcessLaunch.Command],
    workingStore: Option[Store] = None,
    environment: Map[ProcessLaunch.EnvironmentKey, ProcessLaunch.EnvironmentValue] = Map.empty,
    output: ProcessOutput = ProcessOutput.Piped,
    mergeErrors: ProcessLaunch.MergeErrors = ProcessLaunch.MergeErrors(true)
)

object ProcessLaunch:
  type Command = Command.T
  object Command extends RefinedType[String, Pure]
  type EnvironmentKey = EnvironmentKey.T
  object EnvironmentKey extends RefinedType[String, Pure]
  type EnvironmentValue = EnvironmentValue.T
  object EnvironmentValue extends RefinedType[String, Pure]
  type MergeErrors = MergeErrors.T
  object MergeErrors extends RefinedType[Boolean, Pure]

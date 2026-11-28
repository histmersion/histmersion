package com.histmersion
package util.process

import com.histmersion.util.storage.Store
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** A child process to start: its command line, the store it runs in (its working directory), the variables added to the
  * environment it inherits, where its output goes and whether its errors go with it.
  */
final case class ProcessLaunch(
    command: List[ProcessLaunch.Command],
    workingStore: Option[Store] = None,
    environment: Map[ProcessLaunch.EnvironmentKey, ProcessLaunch.EnvironmentValue] = Map.empty,
    output: ProcessOutput = ProcessOutput.Piped,
    mergeErrors: ProcessLaunch.MergeErrors = ProcessLaunch.MergeErrors.Merged
)

object ProcessLaunch:
  type Command = Command.T
  object Command extends Newtype[String, Pure]
  type EnvironmentKey = EnvironmentKey.T
  object EnvironmentKey extends Newtype[String, Pure]
  type EnvironmentValue = EnvironmentValue.T
  object EnvironmentValue extends Newtype[String, Pure]
  enum MergeErrors derives CanEqual:
    case Merged, Separate
  object MergeErrors extends TwoCases[MergeErrors]:
    extension (mergeErrors: MergeErrors) def mergeErrors: Boolean = mergeErrors == Merged

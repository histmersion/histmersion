package com.histmersion.util.process

import com.histmersion.util.storage.Store

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
  object Command extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentKey = EnvironmentKey.T
  object EnvironmentKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentValue = EnvironmentValue.T
  object EnvironmentValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeErrors = MergeErrors.T
  object MergeErrors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

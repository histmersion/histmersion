package com.histmersion
package pluginresolver

import io.github.iltotore.iron.constraint.any.Pure
import java.nio.file.Path

/** Why the resolver failed, as a sentence for a person: the one failure message of the plugin resolver. */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]:
  def notCoordinates(raw: ModuleCoordinates.Text): Problem = Problem(s"not group:artifact:version: '$raw'")
  def notKey(raw: ModuleKey.Text): Problem = Problem(s"not group:artifact: '$raw'")
  def unknownOption(flag: Resolver.Argument): Problem = Problem(s"unknown option $flag")
  def noModuleList(modules: Path): Problem =
    Problem(s"no module list in the kit ($modules): this kit predates plugin dependencies")

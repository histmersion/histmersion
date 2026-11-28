package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class GenerateResult(
    dir: GenerateResult.Dir,
    runtimes: List[GenerateResult.Runtimes],
    files: GenerateResult.Files,
    written: GenerateResult.Written,
    removed: GenerateResult.Removed,
    definitions: GenerateResult.Definitions
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object GenerateResult:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type Runtimes = Runtimes.T
  object Runtimes extends Newtype[String, Pure]
  type Files = Files.T
  object Files extends Newtype[Int, Pure]
  type Written = Written.T
  object Written extends Newtype[Int, Pure]
  type Removed = Removed.T
  object Removed extends Newtype[Int, Pure]
  type Definitions = Definitions.T
  object Definitions extends Newtype[String, Pure]

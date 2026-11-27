package com.histmersion
package server

import sttp.tapir.*
import sttp.tapir.files.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Server)
object Static:
  type Asset = Asset.T
  object Asset extends Newtype[String, Pure]:
    extension (asset: Asset) def filled(token: String, text: String): Asset = Asset(asset.value.replace(token, text))
  type ReadPath = ReadPath.T
  object ReadPath extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def read(path: ReadPath): Asset =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path.value))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try Asset(scala.io.Source.fromInputStream(is, "UTF-8").mkString)
    finally is.close()

package com.histmersion
package server

import sttp.tapir.*
import sttp.tapir.files.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure

object Static:
  type Asset = Asset.T
  object Asset extends Newtype[String, Pure]:
    extension (asset: Asset) def filled(token: String, text: String): Asset = Asset(asset.value.replace(token, text))
  type ReadPath = ReadPath.T
  object ReadPath extends Newtype[String, Pure]

  def read(path: ReadPath): Asset =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path.value))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try Asset(scala.io.Source.fromInputStream(is, "UTF-8").mkString)
    finally is.close()

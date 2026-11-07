package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.json.circe.*
import com.histmersion.config.*
import com.histmersion.domain.*

object Static:
  object Asset:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Asset = Asset.T
  object Asset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPath = ReadPath.T
  object ReadPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def read(path: com.histmersion.server.Static.ReadPath): Asset =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path.value))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try Asset(scala.io.Source.fromInputStream(is, "UTF-8").mkString)
    finally is.close()

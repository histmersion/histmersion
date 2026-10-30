package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.json.circe.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

object Static:
  def read(path: String): String =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString
    finally is.close()

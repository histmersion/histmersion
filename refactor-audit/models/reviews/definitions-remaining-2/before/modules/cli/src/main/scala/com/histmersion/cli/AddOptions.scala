package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import scala.jdk.CollectionConverters.*

/** What `plugin add` is told. `repos`: repositories named for this install (`--repo`), as URLs, looked up before the
  * configured ones — for the plugin itself and for its dependencies.
  */
final case class AddOptions(
    yes: Boolean = false,
    unsigned: Boolean = false,
    verify: Boolean = false,
    sha256: Option[String] = None,
    repos: List[String] = Nil
)

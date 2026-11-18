package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8

/** The versions of project/Dependencies.scala (`V`) that tests need, as the build writes them into the test resources
  * (`build-versions.properties`): the Scala version of every module (the `scala-<version>` directory under
  * `target/out`), and scalafix-cli with the Scala 2.13 it is published for. Tests read them here instead of naming a
  * version.
  */
final case class BuildVersions(scala: String, scalafix: String, scalafixScala: String)

object BuildVersions:
  val Resource: String = "build-versions.properties"

  /** `key=value` lines into a map; blank lines and `#` comments are skipped. Pure. */
  def parse(lines: List[String]): Map[String, String] =
    lines
      .map(_.trim)
      .filterNot(l => l.isEmpty || l.startsWith("#"))
      .flatMap(l =>
        l.split("=", 2) match
          case Array(k, v) => List(k.trim -> v.trim)
          case _           => Nil
      )
      .toMap

  /** The versions of the build these tests were compiled by. */
  lazy val build: BuildVersions =
    val in = Option(getClass.getClassLoader.getResourceAsStream(Resource))
      .getOrElse(sys.error(s"$Resource is not on the test class path: the build writes it (server Test resources)"))
    val values =
      try parse(scala.io.Source.fromInputStream(in, UTF_8.name).getLines().toList)
      finally in.close()
    def value(key: String): String = values.getOrElse(key, sys.error(s"$Resource has no $key="))
    BuildVersions(value("scala"), value("scalafix"), value("scalafixScala"))

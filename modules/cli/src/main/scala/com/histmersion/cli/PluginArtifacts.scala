package com.histmersion
package cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Histmersion's own artifacts in that repository, at the version of the install this CLI belongs to: the plugin API (a
  * jar, its sources, its POM) and the plugin platform (a POM whose dependencies are the libraries the server provides
  * to plugins).
  */
object PluginArtifacts:
  type Coordinates = Coordinates.T
  object Coordinates extends RefinedType[String, Pure]
  type Path = Path.T
  object Path extends RefinedType[String, Pure]
  type CoordinatesName = CoordinatesName.T
  object CoordinatesName extends RefinedType[String, Pure]
  type PathName = PathName.T
  object PathName extends RefinedType[String, Pure]

  val Api = "histmersion-plugin-api"
  val Platform = "histmersion-plugin-platform"

  /** What a plugin's tests load and run it with, as the server does. */
  val Testkit = "histmersion-plugin-testkit"

  /** `group::name:version`, as scala-cli and `histmersion plugin add` write a Scala 3 artifact. */
  def coordinates(name: CoordinatesName): Coordinates =
    Coordinates(s"${PluginBuildVersions.organization}::$name:${PluginBuildVersions.histmersion}")

  /** A file of the artifact relative to the repository's root:
    * `com/histmersion/histmersion-plugin-api_3/<v>/…<suffix>`.
    */
  def path(name: PathName, suffix: String): Path =
    val artifact = s"${name}_3"
    val version = PluginBuildVersions.histmersion
    Path(s"${PluginBuildVersions.organization.replace('.', '/')}/$artifact/$version/$artifact-$version$suffix")

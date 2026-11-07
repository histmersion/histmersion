package com.histmersion.cli

import io.github.iltotore.iron.*

/** Histmersion's own artifacts in that repository, at the version of the install this CLI belongs to: the plugin API (a
  * jar, its sources, its POM) and the plugin platform (a POM whose dependencies are the libraries the server provides
  * to plugins).
  */
object PluginArtifacts:
  type Coordinates = Coordinates.T
  object Coordinates extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoordinatesName = CoordinatesName.T
  object CoordinatesName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PathName = PathName.T
  object PathName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Api = "histmersion-plugin-api"
  val Platform = "histmersion-plugin-platform"

  /** What a plugin's tests load and run it with, as the server does. */
  val Testkit = "histmersion-plugin-testkit"

  /** `group::name:version`, as scala-cli and `histmersion plugin add` write a Scala 3 artifact. */
  def coordinates(name: com.histmersion.cli.PluginArtifacts.CoordinatesName): com.histmersion.cli.PluginArtifacts.Coordinates =
    Coordinates(s"${PluginBuildVersions.organization}::$name:${PluginBuildVersions.histmersion}")

  /** A file of the artifact relative to the repository's root:
    * `com/histmersion/histmersion-plugin-api_3/<v>/…<suffix>`.
    */
  def path(name: com.histmersion.cli.PluginArtifacts.PathName, suffix: String): com.histmersion.cli.PluginArtifacts.Path =
    val artifact = s"${name}_3"
    val version = PluginBuildVersions.histmersion
    Path(s"${PluginBuildVersions.organization.replace('.', '/')}/$artifact/$version/$artifact-$version$suffix")

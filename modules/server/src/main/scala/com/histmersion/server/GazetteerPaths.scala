package com.histmersion
package server
import com.histmersion.util.problem.Problem

import com.histmersion.config.{AppConfig, UserPaths}

import java.nio.file.{Files, Path}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.agents.AgentTrees
import com.histmersion.config.UserPaths.pathEquality
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** Where the gazetteer's files are, resolved from the config: the pinned extracts (a CACHE, rebuilt or copied, pinned
  * by sha256: the XDG cache directory), the Linked Places drop folder, the manifest of the pins, and `legacy`, where
  * the extracts lived before they moved to the cache directory (`<data dir>/gazetteer`, until 2026-10-17).
  */
@movesTo(FeaturePlace.Server)
final case class GazetteerPaths(extracts: Path, lpf: Path, manifest: Path, legacy: Option[Path] = None):

  /** The manifest's text, or why there is none. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def manifestText(): Either[Problem, GazetteerPaths.ManifestText] =
    if !Files.isRegularFile(manifest) then
      Left(
        Problem.Io.Absent(
          Problem.Io.Held.GazetteerManifest,
          Problem.Io.Path(s"$manifest"),
          Some(Problem.SettingKey("places.sources.manifest"))
        )
      )
    else Right(GazetteerPaths.ManifestText(Files.readString(manifest)))

object GazetteerPaths:
  type ConfiguredPath = ConfiguredPath.T
  object ConfiguredPath extends Newtype[String, Pure]
  type EnvName = EnvName.T
  object EnvName extends Newtype[String, Pure]
  type EnvText = EnvText.T
  object EnvText extends Newtype[String, Pure]
  type OsName = OsName.T
  object OsName extends Newtype[String, Pure]
  type ManifestText = ManifestText.T
  object ManifestText extends Newtype[String, Pure]

  /** The paths in the process environment `env` (the entry point read it once) of the operating system `osName`. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(cfg: AppConfig, env: UserPaths.Lookup, osName: UserPaths.OsName): GazetteerPaths =
    val data = UserPaths
      .dataDir(cfg.runtime.dataDir, env, osName)
      .getOrElse(Path.of(cfg.init.dataDir.value))
      .resolve("gazetteer")
    val cache = UserPaths.cacheDir(env).map(_.resolve("gazetteer")).getOrElse(data)
    def or(configured: String, fallback: Path): Path =
      Option(configured)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(p => Path.of(AgentTrees.expandIn(p, env)))
        .getOrElse(fallback)
    val extracts = or(cfg.places.sources.dir.value, cache)
    GazetteerPaths(
      extracts,
      or(cfg.places.sources.lpfDir.value, data.resolve("lpf")),
      Path.of(cfg.init.dataDir.value).resolve(cfg.places.sources.manifest.value),
      Option.when(data != extracts)(data)
    )

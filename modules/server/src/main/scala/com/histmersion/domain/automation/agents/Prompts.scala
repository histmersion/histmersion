package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.util.storage.{Location, Resources, Store, Stores}
import java.nio.file.Path
import com.histmersion.util.storage.SafePath
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
final class Prompts(overrideDir: Prompts.OverrideDir)(using stores: Stores, resources: Resources):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[agents] def bundledText(rel: SafePath.Candidate): Either[Problem, String] =
    resources.text(Resources.ResourceName(s"agents/${rel.value}")).map(_.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val dir: Option[Path] =
    Option(overrideDir.value).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def load(rel: SafePath.Candidate): Either[Problem, String] =
    val local = dir
      .flatMap { root =>
        val path = root.resolve(rel.value).normalize()
        Option.when(path.startsWith(root))((root, path))
      }
      .flatMap { (root, path) =>
        Store.Key
          .of(root.relativize(path).toString.replace('\\', '/'))
          .toOption
          .map(key => (stores.at(Location.AgentHomes, root), key))
      }
    local.filter((store, key) => store.isFile(key)) match
      case Some((store, key)) =>
        try
          store
            .get(key)
            .toRight(Problem.Io.Wrong(Problem.Io.Path.of(store.where(key)), Problem.Io.Kind.Present))
            .map(_.text().value)
        catch
          case e: java.io.IOException =>
            Left(
              Problem.Io.File(Problem.Io.Access.Read, Problem.Io.Path.of(store.where(key)), External.Reported.of(e))
            )
      case None => bundledText(rel)

object Prompts:
  type OverrideDir = OverrideDir.T
  object OverrideDir extends Newtype[String, Pure]

  def bundled(using Stores, Resources): List[AgentDefinitions.ResourcePath] =
    AgentDefinitions(Prompts(OverrideDir("")), AgentRoster.none).index

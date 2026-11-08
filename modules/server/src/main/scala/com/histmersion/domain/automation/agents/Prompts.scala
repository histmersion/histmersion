package com.histmersion
package domain.automation.agents

import com.histmersion.util.storage.{Location, Resources, Store, Stores}
import java.nio.file.Path
import com.histmersion.util.storage.SafePath
import io.github.iltotore.iron.constraint.any.Pure

final class Prompts(overrideDir: Prompts.OverrideDir)(using stores: Stores, resources: Resources):
  private[agents] def bundledText(rel: SafePath.Candidate): Either[Prompts.Problem, String] = resources.text(Resources.ResourceName(s"agents/${rel.value}")).left.map(p => Prompts.Problem(p.value)).map(_.value)
  private val dir: Option[Path] =
    Option(overrideDir.value).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  def load(rel: SafePath.Candidate): Either[Prompts.Problem, String] =
    val local = dir.flatMap { root =>
      val path = root.resolve(rel.value).normalize()
      Option.when(path.startsWith(root))((root, path))
    }.flatMap { (root, path) =>
      Store.Key.of(root.relativize(path).toString.replace('\\', '/')).toOption.map(key => (stores.at(Location.AgentHomes, root), key))
    }
    local.filter((store, key) => store.isFile(key)) match
      case Some((store, key)) =>
        try store.get(key).toRight(Prompts.Problem(s"prompt override ${store.where(key)} cannot be read: file disappeared")).map(_.text().value)
        catch case e: java.io.IOException => Left(Prompts.Problem(s"prompt override ${store.where(key)} cannot be read: ${e.getMessage}"))
      case None => bundledText(rel)

object Prompts:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type OverrideDir = OverrideDir.T
  object OverrideDir extends Newtype[String, Pure]


  def bundled(using Stores, Resources): List[AgentDefinitions.ResourcePath] = AgentDefinitions(Prompts(OverrideDir("")), AgentRoster.none).index

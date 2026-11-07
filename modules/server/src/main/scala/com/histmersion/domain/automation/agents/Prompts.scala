package com.histmersion.domain.automation.agents

import com.histmersion.util.storage.{Location, Resources, Store, Stores}
import java.nio.file.Path

final class Prompts(overrideDir: Prompts.OverrideDir)(using stores: Stores, resources: Resources):
  private[agents] def bundledText(rel: com.histmersion.util.storage.SafePath.Candidate): Either[com.histmersion.domain.automation.agents.Prompts.Problem, String] = resources.text(s"agents/$rel")
  private val dir: Option[Path] =
    Option(overrideDir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  def load(rel: com.histmersion.util.storage.SafePath.Candidate): Either[com.histmersion.domain.automation.agents.Prompts.Problem, String] =
    val local = dir.flatMap { root =>
      val path = root.resolve(rel).normalize()
      Option.when(path.startsWith(root))((root, path))
    }.flatMap { (root, path) =>
      Store.Key.of(root.relativize(path).toString.replace('\\', '/')).toOption.map(key => (stores.at(Location.AgentHomes, root), key))
    }
    local.filter((store, key) => store.isFile(key)) match
      case Some((store, key)) =>
        try store.get(key).toRight(s"prompt override ${store.where(key)} cannot be read: file disappeared").map(_.text())
        catch case e: java.io.IOException => Left(s"prompt override ${store.where(key)} cannot be read: ${e.getMessage}")
      case None => bundledText(rel)

object Prompts:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverrideDir = OverrideDir.T
  object OverrideDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def bundled(using Stores, Resources): List[com.histmersion.domain.automation.agents.AgentDefinitions.ResourcePath] = AgentDefinitions(Prompts(""), AgentRoster.none).index

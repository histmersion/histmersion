package com.histmersion.domain.automation.agents

import com.histmersion.util.storage.{Location, Resources, Store, Stores}
import java.nio.file.Path

final class Prompts(overrideDir: String)(using stores: Stores, resources: Resources):
  private[agents] def bundledText(rel: com.histmersion.domain.automation.agents.Prompts.BundledTextRel): Either[com.histmersion.domain.automation.agents.Prompts.BundledTextResultPart1, com.histmersion.domain.automation.agents.Prompts.BundledTextResultPart2] = resources.text(s"agents/$rel")
  private val dir: Option[Path] =
    Option(overrideDir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  def load(rel: com.histmersion.domain.automation.agents.Prompts.LoadRel): Either[com.histmersion.domain.automation.agents.Prompts.LoadResultPart1, com.histmersion.domain.automation.agents.Prompts.LoadResultPart2] =
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
  type BundledTextResultPart1 = BundledTextResultPart1.T
  object BundledTextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BundledTextResultPart2 = BundledTextResultPart2.T
  object BundledTextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BundledTextRel = BundledTextRel.T
  object BundledTextRel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadResultPart1 = LoadResultPart1.T
  object LoadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadResultPart2 = LoadResultPart2.T
  object LoadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadRel = LoadRel.T
  object LoadRel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BundledResult = BundledResult.T
  object BundledResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def bundled(using Stores, Resources): List[com.histmersion.domain.automation.agents.Prompts.BundledResult] = AgentDefinitions(Prompts(""), AgentRoster.none).index

package com.histmersion.domain.automation.agents

import com.histmersion.util.storage.{Location, Resources, Store, Stores}
import java.nio.file.Path

final class Prompts(overrideDir: String)(using stores: Stores, resources: Resources):
  private[agents] def bundledText(rel: String): Either[String, String] = resources.text(s"agents/$rel")
  private val dir: Option[Path] =
    Option(overrideDir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  def load(rel: String): Either[String, String] =
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
  def bundled(using Stores, Resources): List[String] = AgentDefinitions(Prompts(""), AgentRoster.none).index

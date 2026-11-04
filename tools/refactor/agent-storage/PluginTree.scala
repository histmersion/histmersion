package com.histmersion.domain.automation.agents

import com.histmersion.util.storage.{Resources, Store, Stores}
import java.nio.charset.StandardCharsets.UTF_8

object PluginTree:
  def tree(roster: AgentRoster)(using Stores, Resources): Either[String, Map[String, String]] =
    val defs = AgentDefinitions(Prompts(""), roster)
    for
      as <- defs.agents
      fs <- defs.fragments
      ss <- defs.skills
    yield AgentGenerator.repoPlugin(AgentGenerator.Defs(as, fs, ss))

  val Owned: List[String] = List("plugin/agents", "plugin/skills")

  def write(store: Store, roster: AgentRoster)(using Stores, Resources): Either[String, Unit] =
    tree(roster).flatMap(t => writeTree(store, t))

  private[agents] def writeTree(store: Store, tree: Map[String, String]): Either[String, Unit] =
    try
      val entries = tree.toList.map { (name, text) =>
        Store.Key.of(name).fold(error => throw IllegalArgumentException(error), identity) -> text
      }
      val wanted = entries.map(_._1.value).toSet
      def prune(key: Store.Key, root: Store.Key): Unit =
        if store.isLink(key) then
          val _ = store.delete(key)
        else if store.isDirectory(key) then
          store.names(key).foreach(name => prune(key.child(name), root))
          if key != root && !wanted.exists(_.startsWith(key.value + "/")) then
            val _ = store.deleteTree(key)
        else if store.isFile(key) && !wanted(key.value) then
          val _ = store.delete(key)
      Owned.foreach { name =>
        val key = Store.Key.of(name).fold(error => throw IllegalArgumentException(error), identity)
        if store.exists(key) || store.isLink(key) then prune(key, key)
      }
      entries.foreach { (key, text) =>
        if store.isLink(key.parent) then
          val _ = store.delete(key.parent)
        store.makeDirectory(key.parent).fold(error => throw java.io.IOException(error.message), identity)
        store.write(key, text.getBytes(UTF_8)).fold(error => throw java.io.IOException(error.message), _ => ())
      }
      scribe.info(s"wrote ${tree.size} file(s) under ${store.where()}")
      Right(())
    catch case error: java.io.IOException => Left(error.getMessage)

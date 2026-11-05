package com.histmersion.domain.automation.agents

import com.histmersion.util.storage.{Resources, Store, Stores}
import java.nio.charset.StandardCharsets.UTF_8

object PluginTree:
  type TreeResultPart1 = TreeResultPart1.T
  object TreeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeResultPart2Part1 = TreeResultPart2Part1.T
  object TreeResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeResultPart2Part2 = TreeResultPart2Part2.T
  object TreeResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Owned = Owned.T
  object Owned extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteResultPart1 = WriteResultPart1.T
  object WriteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreeResultPart1 = WriteTreeResultPart1.T
  object WriteTreeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreeTreePart1 = WriteTreeTreePart1.T
  object WriteTreeTreePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreeTreePart2 = WriteTreeTreePart2.T
  object WriteTreeTreePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def tree(roster: AgentRoster)(using Stores, Resources): Either[com.histmersion.domain.automation.agents.PluginTree.TreeResultPart1, Map[com.histmersion.domain.automation.agents.PluginTree.TreeResultPart2Part1, com.histmersion.domain.automation.agents.PluginTree.TreeResultPart2Part2]] =
    val defs = AgentDefinitions(Prompts(""), roster)
    for
      as <- defs.agents
      fs <- defs.fragments
      ss <- defs.skills
    yield AgentGenerator.repoPlugin(AgentGenerator.Defs(as, fs, ss))

  val Owned: List[com.histmersion.domain.automation.agents.PluginTree.Owned] = List("plugin/agents", "plugin/skills")

  def write(store: Store, roster: AgentRoster)(using Stores, Resources): Either[com.histmersion.domain.automation.agents.PluginTree.WriteResultPart1, Unit] =
    tree(roster).flatMap(t => writeTree(store, t))

  private[agents] def writeTree(store: Store, tree: Map[com.histmersion.domain.automation.agents.PluginTree.WriteTreeTreePart1, com.histmersion.domain.automation.agents.PluginTree.WriteTreeTreePart2]): Either[com.histmersion.domain.automation.agents.PluginTree.WriteTreeResultPart1, Unit] =
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

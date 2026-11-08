package com.histmersion
package domain.automation.agents

import com.histmersion.util.storage.{ByteSource, Resources, SafePath, Store, Stores}
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure

object PluginTree:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Owned = OwnedValue
  type OwnedValue = OwnedValue.T
  object OwnedValue extends Newtype[String, Pure]

  def tree(roster: AgentRoster)(using Stores, Resources): Either[Problem, Map[SafePath.Candidate, String]] =
    val defs = AgentDefinitions(new Prompts(Prompts.OverrideDir("")), roster)
    val files =
      for
        as <- defs.agents
        fs <- defs.fragments
        ss <- defs.skills
      yield AgentGenerator.repoPlugin(AgentGenerator.Defs(as, fs, ss))
    files.left.map(Problem(_)).map(_.map((path, text) => SafePath.Candidate(path) -> text))

  val Owned: List[Owned] = List(OwnedValue("plugin/agents"), OwnedValue("plugin/skills"))

  def write(store: Store, roster: AgentRoster)(using Stores, Resources): Either[Problem, Unit] =
    tree(roster).flatMap(t => writeTree(store, t))

  private[agents] def writeTree(store: Store, tree: Map[SafePath.Candidate, String]): Either[Problem, Unit] =
    try
      val entries = tree.toList.map { (name, text) =>
        Store.Key.of(name.value).fold(error => throw IllegalArgumentException(error.value), identity) -> text
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
        val key = Store.Key.of(name.value).fold(error => throw IllegalArgumentException(error.value), identity)
        if store.exists(key) || store.isLink(key) then prune(key, key)
      }
      entries.foreach { (key, text) =>
        if store.isLink(key.parent) then
          val _ = store.delete(key.parent)
        store.makeDirectory(key.parent).fold(error => throw java.io.IOException(error.message.value), identity)
        store
          .write(key, ByteSource.Content(text.getBytes(UTF_8)))
          .fold(error => throw java.io.IOException(error.message.value), _ => ())
      }
      scribe.info(s"wrote ${tree.size} file(s) under ${store.where()}")
      Right(())
    catch case error: java.io.IOException => Left(Problem(error.getMessage))

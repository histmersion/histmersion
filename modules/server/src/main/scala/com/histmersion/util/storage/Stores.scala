package com.histmersion
package util.storage

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

trait Stores:
  def at(location: Location, root: Path): Store

object Stores:
  def disk: Stores = new Stores:
    def at(location: Location, root: Path): Store = Storage.at(location, root)

  final class InMemory extends Stores:
    private val stores = ConcurrentHashMap[(Location, Path), Store]()
    def at(location: Location, root: Path): Store =
      val path = root.toAbsolutePath.normalize
      stores.computeIfAbsent((location, path), _ => MemoryStore(location, path.toString))

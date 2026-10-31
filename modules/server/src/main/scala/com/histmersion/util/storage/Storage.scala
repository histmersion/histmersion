package com.histmersion.util.storage

import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap

/** The server's files: a [[Store]] for each named [[Location]], and scratch stores that are removed when they close.
  * Where each location lives is the composition root's to say (from the configuration, read again at each call, so a
  * reload that moves a directory is followed).
  */
trait Storage:
  /** The store of `location`, when the configuration places it (the agents' and the plugins' need a data directory). */
  def find(location: Location): Option[Store]

  /** The store of a location the configuration always places (originals, spool, base map, backups, bundles, the
    * gazetteer extracts, the settings file's directory, scratch).
    */
  def store(location: Location): Store =
    find(location).getOrElse(throw IllegalStateException(s"no directory is configured for $location"))

  /** A new temporary store, its directory named after `purpose`. */
  def scratch(purpose: String): Scratch

object Storage:

  /** Stores on disk: `roots` says where each location is now, `temp` where scratch stores are made. */
  def apply(roots: Location => Option[Path], temp: () => Path): Storage = Implementation(roots, temp)

  /** The store of `location` over the directory `root`, for the composition root and the tools that are handed a path
    * (a test's temporary directory, a tool's argument).
    */
  def at(location: Location, root: Path): Store = DiskStore(location, root)

  final private class Implementation(roots: Location => Option[Path], temp: () => Path) extends Storage:
    def find(location: Location): Option[Store] = roots(location).map(DiskStore(location, _))

    def scratch(purpose: String): Scratch =
      val base = temp()
      Files.createDirectories(base)
      val dir = Files.createTempDirectory(base, purpose)
      val s = DiskStore(Location.Scratch, dir)
      new Scratch:
        def store: Store = s
        def close(): Unit =
          val _ = s.deleteTree(StoreKey.Root)

  /** Stores in memory, one per location, shared by everyone holding this instance; scratch stores are fresh. */
  final class InMemory extends Storage:
    private val stores = ConcurrentHashMap[Location, MemoryStore]()

    def find(location: Location): Option[Store] =
      Some(stores.computeIfAbsent(location, l => MemoryStore(l, s"memory:$l")))

    def scratch(purpose: String): Scratch =
      val s = MemoryStore(Location.Scratch, s"memory:scratch:$purpose")
      new Scratch:
        def store: Store = s
        def close(): Unit =
          val _ = s.deleteTree(StoreKey.Root)

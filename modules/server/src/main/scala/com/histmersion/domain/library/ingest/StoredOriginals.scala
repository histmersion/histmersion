package com.histmersion
package domain.library.ingest

import com.histmersion.domain.library.sources.{SourceDeletePlan, SourceOriginals}
import com.histmersion.util.storage.Store
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** The files the server keeps of a source's original (#969), which a delete removes after its commit:
  *
  *   - the originals' `<sha256>.<ext>` (`converter.spool-dir`) — content-addressed, so it stays when another source
  *     still holds the same digest;
  *   - the acquisition spool's `<key>/paper.<ext>` (`acquire.spool-dir`), for the citekey and each old citekey of the
  *     source (a fetched file is kept under the key it arrived under).
  *
  * Calibre's copy is never among them: Calibre is the user's library, and `histmersion calibre` the only thing that
  * writes it.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class StoredOriginals(originals: () => Store, spool: () => Store) extends SourceOriginals:

  /** The files that exist now, each named where it is. A key that is not a valid citekey names nothing. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def find(plan: SourceDeletePlan): List[SourceOriginals.FoundFile] =
    val o = originals()
    val s = spool()
    val stored = plan.sha256
      .filterNot(_ => plan.shaShared.value)
      .toList
      .flatMap(sha =>
        o.list()
          .filter(f => f.isFile && f.key.name.value.startsWith(sha.value + "."))
          .sortBy(_.key.value)
          .map(f => SourceOriginals.FoundFile(o.where(f.key).value))
      )
    val acquired = plan.keys.filter(k => Citekey.valid(k.value)).flatMap { k =>
      Store.Key.of(k.value).toOption.filter(s.isDirectory).toList.flatMap { dir =>
        s.list(dir)
          .filter(_.isFile)
          .sortBy(f => s.where(f.key).value)
          .map(f => SourceOriginals.FoundFile(s.where(f.key).value)) :+
          SourceOriginals.FoundFile(s.where(dir).value)
      }
    }
    (stored ++ acquired).distinct

  /** Remove `files` (files first, then the directories they were in, when empty). Best effort: every failure is logged
    * and returned as `path: reason`, and never undoes the delete it follows.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def remove(files: List[SourceOriginals.RemoveFiles]): List[SourceOriginals.RemovedFile] =
    val o = originals()
    val s = spool()
    val named = files.map(f => f -> keyIn(o, f.value).map(o -> _).orElse(keyIn(s, f.value).map(s -> _)))
    val (dirs, plain) = named.partition((_, at) => at.exists((st, k) => st.isDirectory(k)))
    (plain ++ dirs).flatMap { (f, at) =>
      at.flatMap { (st, k) =>
        // a directory still holding a file stays, as the file system refuses to remove it
        val why =
          if st.isDirectory(k) then
            Option.when(st.names(k).nonEmpty || st.deleteTree(k).isEmpty)(SourceOriginals.RemovedFile(s"$f: $f"))
          else Option.when(!st.delete(k) && st.exists(k))(SourceOriginals.RemovedFile(s"$f: it could not be removed"))
        why.foreach(w => scribe.warn(s"delete: the stored original could not be removed — ${w.value}"))
        why
      }
    }

  /** The key of `where` in `store`, when it is one of its files. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def keyIn(store: Store, where: String): Option[Store.Key] =
    val root = store.where(Store.Key.Root).value.stripSuffix("/") + "/"
    Option
      .when(where.startsWith(root))(where.drop(root.length))
      .flatMap(Store.Key.either(_).toOption)
      .filterNot(_.isRoot)

  /** Every content hash the originals' store holds a file for (`<sha>.<ext>`, one listing) — what the Calibre gaps
    * listing marks as downloadable. Empty when the store cannot be read.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hashes: Set[Sha256] =
    originals()
      .names()
      .map(_.value)
      .filter(_.contains('.'))
      .map(n => n.substring(0, n.indexOf('.')))
      .filter(_.nonEmpty)
      .flatMap(Sha256.option(_))
      .toSet

package com.histmersion.domain.library.acquisition

import com.histmersion.api.text.SafeName
import com.histmersion.util.storage.Store
import com.histmersion.domain.library.Citekey

/** Where a fetched file waits for the converter (`acquire.spool-dir`; empty is the JVM temp directory's
  * `histmersion-acquire`, which is what a container gets): `<citekey>/paper.<ext>`.
  */
final class Spool(store: Store):

  /** Where `key` is, for the ingest's report. */
  def where(key: Store.Key): com.histmersion.domain.library.acquisition.Spool.WhereResult = store.where(key)

  /** `bytes` as the fetched file of `citekey`. The key is built from a caller-supplied string, so it gets BOTH guards:
    * the citekey must parse ([[Citekey]]), and the store's key refuses anything that would leave it.
    */
  def write(citekey: com.histmersion.domain.library.Citekey, extension: com.histmersion.domain.library.acquisition.Spool.WriteExtension, bytes: Array[com.histmersion.domain.library.acquisition.Spool.WriteBytes]): Either[com.histmersion.domain.library.acquisition.Spool.WriteResultPart1, Store.Key] =
    Citekey.parse(citekey).flatMap { _ =>
      Store.Key
        .of(citekey, s"paper.${SafeName.of(extension)}")
        .left
        .map(_ => s"could not spool the file: '$citekey' would escape the spool directory")
        .flatMap(key => store.write(key, bytes).left.map(e => s"could not spool the file: ${e.message}").map(_ => key))
    }

  /** The fetched file at `key`, when it is there. */
  def get(key: Store.Key): Option[com.histmersion.util.storage.Blob] = store.get(key)

object Spool:
  type WhereResult = WhereResult.T
  object WhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteResultPart1 = WriteResultPart1.T
  object WriteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteExtension = WriteExtension.T
  object WriteExtension extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteBytes = WriteBytes.T
  object WriteBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]

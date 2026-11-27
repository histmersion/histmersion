package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem

import com.histmersion.api.text.SafeName
import com.histmersion.util.storage.{ByteSource, Store}
import com.histmersion.util.storage.Blob
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Where a fetched file waits for the converter (`acquire.spool-dir`; empty is the JVM temp directory's
  * `histmersion-acquire`, which is what a container gets): `<citekey>/paper.<ext>`.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class Spool(store: Store):

  /** Where `key` is, for the ingest's report. */
  def where(key: Store.Key): Blob.Location = store.where(key)

  /** `bytes` as the fetched file of `citekey`. The key is built from a caller-supplied string, so it gets BOTH guards:
    * the citekey is a [[Citekey]], and the store's key refuses anything that would leave it.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def write(citekey: Citekey, extension: Spool.Extension, bytes: Spool.Bytes): Either[Problem, Store.Key] =
    Store.Key
      .of(citekey.value, s"paper.${SafeName.of(extension.value)}")
      .flatMap(key => store.write(key, ByteSource.Content(bytes.value)).left.map(_.message).map(_ => key))

  /** The fetched file at `key`, when it is there. */
  def get(key: Store.Key): Option[Blob] = store.get(key)

object Spool:
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]
  type Extension = Extension.T
  object Extension extends Newtype[String, Pure]

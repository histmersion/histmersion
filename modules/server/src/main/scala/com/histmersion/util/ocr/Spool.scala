package com.histmersion.util.ocr

import com.histmersion.util.text.SafeName
import com.histmersion.util.storage.{Store, StoreKey}
import com.histmersion.values.Citekey

/** Where a fetched file waits for the converter (`acquire.spool-dir`; empty is the JVM temp directory's
  * `histmersion-acquire`, which is what a container gets): `<citekey>/paper.<ext>`.
  */
final class Spool(store: Store):

  /** Where `key` is, for the ingest's report. */
  def where(key: StoreKey): String = store.where(key)

  /** `bytes` as the fetched file of `citekey`. The key is built from a caller-supplied string, so it gets BOTH guards:
    * the citekey must parse ([[Citekey]]), and the store's key refuses anything that would leave it.
    */
  def write(citekey: String, extension: String, bytes: Array[Byte]): Either[String, StoreKey] =
    Citekey.parse(citekey).flatMap { _ =>
      StoreKey
        .of(citekey, s"paper.${SafeName.of(extension)}")
        .left
        .map(_ => s"could not spool the file: '$citekey' would escape the spool directory")
        .flatMap(key => store.write(key, bytes).left.map(e => s"could not spool the file: ${e.message}").map(_ => key))
    }

  /** The fetched file at `key`, when it is there. */
  def get(key: StoreKey): Option[com.histmersion.util.storage.Blob] = store.get(key)

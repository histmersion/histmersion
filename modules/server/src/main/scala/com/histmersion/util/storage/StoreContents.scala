package com.histmersion.util.storage

import java.io.InputStream
import java.security.{DigestOutputStream, MessageDigest}

/** A [[ContentStore]] over a store: `<sha256>.<ext>` at its top. */
final class StoreContents(store: Store) extends ContentStore:

  def find(sha256: String): Option[(ByteSource, String)] =
    store
      .list()
      .find(s => s.isFile && s.key.name.startsWith(sha256 + "."))
      .flatMap(s => store.get(s.key))
      .map(b => b.asSource -> b.key.name.drop(sha256.length + 1))

  /** Written beside its name, hashed, and moved into place only when the content IS `sha256`: the store is
    * content-addressed, and whoever reads it trusts the name.
    */
  def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
    if find(sha256).isDefined then
      val _ = in.transferTo(java.io.OutputStream.nullOutputStream())
      Right(())
    else
      scala.util
        .Try {
          StoreKey.of(s"$sha256.${ContentStore.safeExt(ext)}").flatMap { key =>
            store.reserve(key, TempName.Unique).left.map(_.message).flatMap { r =>
              try
                val md = MessageDigest.getInstance("SHA-256")
                val out = DigestOutputStream(r.output(), md)
                try { val _ = in.transferTo(out) }
                finally out.close()
                val actual = ContentStore.hex(md.digest())
                if actual != sha256 then Left(s"its content hashes to $actual")
                else r.commit().left.map(_.message).map(_ => ())
              finally r.close()
            }
          }
        }
        .toEither
        .left
        .map(e => s"cannot store it in ${store.where()}: ${e.getMessage}")
        .flatten

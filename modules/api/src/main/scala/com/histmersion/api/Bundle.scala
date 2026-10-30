package com.histmersion.api

/** The portable bundle (`.hmx`, docs/research-merge-import.md §4): a lossless, logical, identity-carrying export of one
  * collection, built to be imported into another.
  *
  * A bundle is a gzip-compressed tar of
  * {{{
  * manifest.json
  * entities/<kind>.ndjson     one JSON object per line, sorted by uid (or by the owner's uid)
  * blobs/sha256/ab/abcdef…    OCR bodies, article-version bodies, asset bytes, stored originals
  * }}}
  * Every entity line carries its `uid`, its `origin` (the instance that created it), its `rev` (the sha256 of the
  * line's canonical JSON without `rev` and `updatedAt`, see `BundleFormat.stamp`) and its label (citekey, slug, id);
  * other entities are referenced BY UID. Calibre is not part of a bundle (§3a): `calibre_id` is a per-machine pointer
  * and is never exported.
  *
  * The types here are the FILE format. They have circe codecs (one line = one value) but no tapir schemas: no endpoint
  * takes or returns them, except the manifest and the reports at the bottom.
  */
object Bundle:
  val Format = "histmersion-bundle"
  val FormatVersion = 1
  val Extension = ".hmx"
  val ManifestPath = "manifest.json"

  /** Where a blob lives in the tar: content-addressed, fanned out on its first byte. */
  def blobPath(sha256: String): String = s"blobs/sha256/${sha256.take(2)}/$sha256"

  /** The sha256 named by a blob path, when `path` is one. */
  def blobSha(path: String): Option[String] =
    val prefix = "blobs/sha256/"
    Option
      .when(path.startsWith(prefix)) {
        val rest = path.drop(prefix.length)
        rest.drop(3)
      }
      .filter(s =>
        s.length == 64 && s.forall(c => c.isDigit || ('a' to 'f').contains(c)) &&
          path == blobPath(s)
      )

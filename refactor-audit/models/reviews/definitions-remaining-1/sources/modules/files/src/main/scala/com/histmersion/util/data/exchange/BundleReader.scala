package com.histmersion.util.data.exchange

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleEntities

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import com.histmersion.api.exchange.Bundle

/** Reading a `.hmx` bundle off a stream: the manifest, every entity file, and the blobs' names — checking each against
  * the manifest, and collecting EVERY problem rather than stopping at the first (the user fixes them all in one go,
  * docs/research-merge-import.md §0).
  *
  * The blobs are not kept. With `hashBlobs` each one is read through and its sha256 compared with its name (what
  * `bundle verify` does); without, they are skipped, and whoever applies them hashes them as they are consumed (the
  * import's second pass).
  */
object BundleReader:

  final case class Loaded(
      manifest: Option[com.histmersion.api.exchange.Bundle.Manifest],
      entities: BundleEntities,
      /** sha256 → size of every blob entry found. */
      blobs: Map[Loaded.BlobsKey, Loaded.BlobsValue],
      problems: List[Loaded.Problems]
  ):
    def ok: Boolean = manifest.isDefined && problems.isEmpty

  object Loaded:
    type BlobsKey = BlobsKey.T
    object BlobsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BlobsValue = BlobsValue.T
    object BlobsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def read(in: InputStream, hashBlobs: Boolean): Loaded =
    val tar = BundleTar.Reader(in)
    val problems = List.newBuilder[String]
    val payloads = scala.collection.mutable.LinkedHashMap.empty[String, Array[Byte]]
    val blobs = scala.collection.mutable.LinkedHashMap.empty[String, Long]

    // a var: the manifest is the first entry, and is only known once read
    var manifest: Option[com.histmersion.api.exchange.Bundle.Manifest] = None

    @annotation.tailrec
    def loop(first: Boolean): Unit =
      tar.next() match
        case Left(e)     => problems += e
        case Right(None) =>
          if first then problems += "empty bundle: no manifest.json"
        case Right(Some(e)) =>
          if first && e.name != Bundle.ManifestPath then
            problems += s"the first entry is ${e.name}, not ${Bundle.ManifestPath}"
          e.name match
            case Bundle.ManifestPath =>
              BundleFormat.parseManifest(tar.readAll()) match
                case Right(m) => manifest = Some(m)
                case Left(p)  => problems += p
            case p if com.histmersion.api.exchange.Bundle.Files.all.contains(p) =>
              if payloads.contains(p) then problems += s"$p appears twice"
              payloads(p) = tar.readAll()
            case p =>
              Bundle.blobSha(p) match
                case None      => problems += s"$p: not a file a bundle holds"
                case Some(sha) =>
                  if blobs.contains(sha) then problems += s"blob $sha appears twice"
                  blobs(sha) = e.size
                  if hashBlobs then
                    val actual = sha256Of(tar.stream)
                    if actual != sha then problems += s"blob $sha: its content hashes to $actual"
          loop(first = false)

    scala.util
      .Try(loop(first = true))
      .failed
      .foreach(e => problems += s"unreadable bundle: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")

    val (entities, lineProblems) = BundleEntities.decode(payloads.toMap)
    val fileProblems = manifest.toList.flatMap { m =>
      val seen = payloads.toMap.map((p, b) => p -> BundleFormat.fileInfo(p, b))
      BundleFormat.checkFiles(m, seen) ++
        Option.when(m.blobs.count != blobs.size)(
          s"manifest lists ${m.blobs.count} blobs, the bundle holds ${blobs.size}"
        ) ++
        Option.when(m.blobs.bytes != blobs.values.sum)(
          s"manifest lists ${m.blobs.bytes} blob bytes, the bundle holds ${blobs.values.sum}"
        )
    }
    val refs = entities.blobRefs
    val missing = refs.keys.toList.sorted
      .filterNot(blobs.contains)
      .map(sha => s"blob $sha (${refs(sha).size} use(s)) is referenced but not in the bundle")
    val unused = blobs.keys.toList.sorted
      .filterNot(refs.contains)
      .map(sha => s"blob $sha is in the bundle but nothing references it")
    Loaded(manifest, entities, blobs.toMap, problems.result() ++ fileProblems ++ lineProblems ++ missing ++ unused)

  def sha256Of(in: InputStream): String =
    val md = MessageDigest.getInstance("SHA-256")
    val din = DigestInputStream(in, md)
    val buf = new Array[Byte](1 << 16)
    while din.read(buf) >= 0 do ()
    BundleFormat.hex(md.digest())

  /** Verification of a whole bundle: format and schema against this server, checksums, revs, blobs, and — for a full
    * bundle (no `since`) — that every reference resolves inside it.
    */
  def verify(in: InputStream, serverSchema: Option[String]): com.histmersion.api.exchange.Bundle.VerifyResult =
    val l = read(in, hashBlobs = true)
    val compat = l.manifest.toList.flatMap(BundleFormat.compatible(_, serverSchema))
    val dangling =
      if l.manifest.exists(_.since.isEmpty) then
        l.entities.dangling.map(r => s"${r.from}: ${r.kind.wire} ${r.uid} is not in the bundle")
      else Nil
    val problems = compat ++ l.problems ++ dangling
    com.histmersion.api.exchange.Bundle
      .VerifyResult(l.manifest.isDefined && problems.isEmpty, l.manifest, l.blobs.size, problems)

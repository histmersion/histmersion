package com.histmersion
package util.data.exchange

import com.histmersion.util.problem.{External, Problem, Problems}
import com.histmersion.ByteCount
import com.histmersion.api.Sha256
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
      manifest: Option[Bundle.Manifest],
      entities: BundleEntities,
      /** sha256 → size of every blob entry found. */
      blobs: Map[Sha256, ByteCount],
      problems: List[Problem]
  ):
    def ok: Boolean = manifest.isDefined && problems.isEmpty

    /** Why the bundle cannot be planned or imported: every problem, or the missing manifest when nothing else is wrong.
      */
    def unreadable: Problems =
      Problems.from(problems.toVector).getOrElse(entry(Bundle.ManifestPath, Problem.Io.Fault.Missing))

  private def entry(path: String, fault: Problem.Io.Fault): Problem =
    Problem.Io.Entry(Problem.Io.Format.Bundle, Problem.Io.Path(s"$path"), fault)

  def read(in: InputStream, hashBlobs: Boolean): Loaded =
    val tar = BundleTar.Reader(in)
    val problems = List.newBuilder[Problem]
    val payloads = scala.collection.mutable.LinkedHashMap.empty[String, Array[Byte]]
    val blobs = scala.collection.mutable.LinkedHashMap.empty[Sha256, ByteCount]

    // a var: the manifest is the first entry, and is only known once read
    var manifest: Option[Bundle.Manifest] = None

    @annotation.tailrec
    def loop(first: Boolean): Unit =
      tar.next() match
        case Left(e)     => problems += e
        case Right(None) =>
          if first then problems += entry(Bundle.ManifestPath, Problem.Io.Fault.Missing)
        case Right(Some(e)) =>
          val name = e.name.value
          if first && name != Bundle.ManifestPath then
            problems += entry(name, Problem.Io.Fault.Ahead(Problem.Io.Path(s"${Bundle.ManifestPath}")))
          name match
            case Bundle.ManifestPath =>
              BundleFormat.parseManifest(tar.readAll()) match
                case Right(m) => manifest = Some(m)
                case Left(p)  => problems += p
            case p if Bundle.Files.all.contains(p) =>
              if payloads.contains(p) then problems += entry(p, Problem.Io.Fault.Repeated)
              payloads(p) = tar.readAll()
            case p =>
              Bundle.blobSha(p) match
                case None      => problems += entry(p, Problem.Io.Fault.Foreign)
                case Some(sha) =>
                  if blobs.contains(sha) then problems += entry(p, Problem.Io.Fault.Repeated)
                  blobs(sha) = e.size
                  if hashBlobs then
                    val actual = sha256Of(tar.stream)
                    if actual != sha then
                      problems += Problem.Io.Corrupt(
                        Problem.Io.Format.Bundle,
                        Problem.Where(s"$p"),
                        Problem.Io.Damage.Checksum
                      )
          loop(first = false)

    scala.util
      .Try(loop(first = true))
      .failed
      .foreach(e =>
        problems += Problem.Io.Corrupt(
          Problem.Io.Format.Bundle,
          Problem.Where("the tar stream"),
          Problem.Io.Damage.Unreadable(External.Reported.of(e))
        )
      )

    val (entities, lineProblems) = BundleEntities.decode(payloads.toMap)
    val fileProblems = manifest.toList.flatMap { m =>
      val seen = payloads.toMap.map { (p, b) =>
        val path = Bundle.FileInfo.Path(p)
        path -> BundleFormat.fileInfo(path, b)
      }
      val blobBytes = blobs.values.map(_.value).sum
      def differs(measure: Problem.Io.Measure, listed: String, found: Long) =
        entry(BlobsDir, Problem.Io.Fault.Differs(measure, Problem.Raw(s"$listed"), Problem.Raw.of(found)))
      BundleFormat.checkFiles(m, seen) ++
        Option.when(m.blobs.count.value != blobs.size)(
          differs(Problem.Io.Measure.Entries, s"${m.blobs.count}", blobs.size.toLong)
        ) ++
        Option.when(m.blobs.bytes.value != blobBytes)(
          differs(Problem.Io.Measure.Bytes, s"${m.blobs.bytes}", blobBytes)
        )
    }
    val refs = entities.blobRefs
    val missing = refs.keys.toList.sorted
      .filterNot(blobs.contains)
      .map(sha => entry(Bundle.blobPath(sha), Problem.Io.Fault.Dangling(Problem.Count.of(refs(sha).size.toLong))))
    val unused = blobs.keys.toList.sorted
      .filterNot(refs.contains)
      .map(sha => entry(Bundle.blobPath(sha), Problem.Io.Fault.Unreferenced))
    Loaded(manifest, entities, blobs.toMap, problems.result() ++ fileProblems ++ lineProblems ++ missing ++ unused)

  /** Where the blobs are, as the manifest counts them. */
  private val BlobsDir = "blobs/"

  def sha256Of(in: InputStream): Sha256 =
    val md = MessageDigest.getInstance("SHA-256")
    val din = DigestInputStream(in, md)
    val buf = new Array[Byte](1 << 16)
    while din.read(buf) >= 0 do ()
    Sha256(BundleFormat.hex(md.digest()))

  /** Verification of a whole bundle: format and schema against this server, checksums, revs, blobs, and — for a full
    * bundle (no `since`) — that every reference resolves inside it.
    */
  def verify(in: InputStream, serverSchema: Option[Bundle.Manifest.SchemaVersion]): Bundle.VerifyResult =
    val l = read(in, hashBlobs = true)
    val compat = l.manifest.toList.flatMap(BundleFormat.compatible(_, serverSchema))
    val dangling =
      if l.manifest.exists(_.since.isEmpty) then l.entities.dangling.map(_.missing(Problem.NotFound.Scope.Bundle))
      else Nil
    val problems = compat ++ l.problems ++ dangling
    Bundle
      .VerifyResult(
        Bundle.VerifyResult.Ok(l.manifest.isDefined && problems.isEmpty),
        l.manifest,
        Bundle.VerifyResult.BlobsChecked(l.blobs.size),
        problems
      )

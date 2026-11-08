//> using scala 2.13.18
//> using dep org.scalameta::scalameta:4.17.4
//> using dep org.scalameta::semanticdb-shared:4.17.4

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import scala.meta._
import scala.meta.internal.{semanticdb => s}

/** The check behind ShortenQualified and ReExportAliases: every symbol occurrence of a rewritten file must denote the same
  * symbol as before, in the same order.
  *
  *   compare <snapshot-dir> <ledger.tsv> <out.tsv>
  *
  * <snapshot-dir> holds the SemanticDB files of the modules before the rewrite (at the same paths as under target/out) and the
  * sources before it under `src/`. Ignored on both sides: package symbols, occurrences in imports and package clauses, and the
  * ledger's ranges (the qualifiers a rewrite dropped, the regions ReExportAliases replaced, the lines it added). Each
  * difference is written to <out.tsv>: path, kind (changed|stale), offset after, line after, symbol before, symbol after. */
object SymbolOracle {
  final case class Row(path: String, kind: String, origStart: Int, origEnd: Int, newStart: Int, newEnd: Int)
  final case class Occ(sym: String, role: Int, start: Int, end: Int)

  def offsets(text: String): Array[Int] = {
    val b = scala.collection.mutable.ArrayBuffer(0)
    var i = 0
    while (i < text.length) { if (text.charAt(i) == '\n') b += i + 1; i += 1 }
    b.toArray
  }
  def md5(bytes: Array[Byte]): String = java.security.MessageDigest.getInstance("MD5").digest(bytes).map(b => f"${b & 255}%02X").mkString
  /** `scala.Predef.String` is `java.lang.String`; an export forwarder `X().` is the member `X.` it replaces (same owner,
    * name and type: SemanticDB marks every forwarder as a method). */
  def norm(sym: String): String =
    if (sym == "scala/Predef.String#") "java/lang/String#"
    else if (sym.endsWith("().") && !sym.startsWith("local")) sym.stripSuffix("().") + "."
    else sym

  /** Ranges (start, end) of imports and package-clause references. */
  def ignoredRanges(text: String): List[(Int, Int)] =
    dialects.Scala3(text).parse[Source] match {
      case Parsed.Success(tree) => tree.collect {
        case i: Import => (i.pos.start, i.pos.end)
        case p: Pkg => (p.ref.pos.start, p.ref.pos.end)
      }
      case _ => Nil
    }

  def occurrences(doc: s.TextDocument, text: String, drop: List[(Int, Int)]): Vector[Occ] = {
    val lines = offsets(text)
    val ignored = ignoredRanges(text) ++ drop
    doc.occurrences.toVector.flatMap { o =>
      o.range.filter(r => r.startLine < lines.length && r.endLine < lines.length).map { r =>
        val st = lines(r.startLine) + r.startCharacter
        val en = lines(r.endLine) + r.endCharacter
        Occ(norm(o.symbol), o.role.value, st, en)
      }
    }.filterNot(o => o.sym.endsWith("/") || o.sym.isEmpty || ignored.exists { case (a, b) => o.start >= a && o.end <= b && b > a })
      .sortBy(o => (o.start, o.end))
  }

  /** Indices of `b` not matched in a longest common subsequence of `a` and `b` (Myers, O((N+M)D)). */
  def unmatched(a: Vector[(String, Int)], b: Vector[(String, Int)]): (List[Int], List[Int]) = {
    var lo = 0
    while (lo < a.size && lo < b.size && a(lo) == b(lo)) lo += 1
    var ha = a.size; var hb = b.size
    while (ha > lo && hb > lo && a(ha - 1) == b(hb - 1)) { ha -= 1; hb -= 1 }
    val ma = a.slice(lo, ha); val mb = b.slice(lo, hb)
    if (ma.isEmpty && mb.isEmpty) return (Nil, Nil)
    // Myers: shortest edit script, then walk it back to the unmatched indices
    val n = ma.size; val m = mb.size; val max = n + m; val off = max
    val v = Array.fill(2 * max + 2)(0)
    val trace = scala.collection.mutable.ArrayBuffer.empty[Array[Int]]
    var found = false; var d = 0
    while (!found && d <= max) {
      trace += v.clone()
      var k = -d
      while (!found && k <= d) {
        var x = if (k == -d || (k != d && v(off + k - 1) < v(off + k + 1))) v(off + k + 1) else v(off + k - 1) + 1
        var y = x - k
        while (x < n && y < m && ma(x) == mb(y)) { x += 1; y += 1 }
        v(off + k) = x
        if (x >= n && y >= m) found = true
        k += 2
      }
      if (!found) d += 1
    }
    val ua = List.newBuilder[Int]; val ub = List.newBuilder[Int]
    var x = n; var y = m
    var dd = d
    while (dd > 0) {
      val vp = trace(dd)
      val k = x - y
      val prevK = if (k == -dd || (k != dd && vp(off + k - 1) < vp(off + k + 1))) k + 1 else k - 1
      val prevX = vp(off + prevK); val prevY = prevX - prevK
      while (x > prevX && y > prevY) { x -= 1; y -= 1 }
      if (x == prevX) ub += lo + prevY else ua += lo + prevX
      x = prevX; y = prevY
      dd -= 1
    }
    (ua.result().reverse, ub.result().reverse)
  }

  def main(args: Array[String]): Unit = args.toList match {
    case "compare" :: snap :: ledger :: out :: Nil => compare(Paths.get(snap), Paths.get(ledger), Paths.get(out))
    case _ => System.err.println("usage: compare <snapshot-dir> <ledger.tsv> <out.tsv>"); sys.exit(2)
  }

  def compare(snap: Path, ledgerPath: Path, out: Path): Unit = {
    val ledger: Map[String, List[Row]] =
      if (!Files.isRegularFile(ledgerPath)) Map.empty
      else Files.readAllLines(ledgerPath, UTF_8).asScala.toList.filter(_.nonEmpty).map(_.split("\t", -1)).map { c =>
        Row(c(0), c(1), c(2).toInt, c(3).toInt, c(4).toInt, c(5).toInt)
      }.groupBy(_.path)
    val target = Paths.get("target/out")
    val files = { val st = Files.walk(snap); try st.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb")).toList finally st.close() }
    val rows = List.newBuilder[String]
    var compared = 0; var changedFiles = 0
    files.foreach { f =>
      val rel = snap.relativize(f)
      val cur = target.resolve(rel)
      val before = s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents
      before.foreach { bd =>
        val srcBefore = snap.resolve("src").resolve(bd.uri)
        val srcAfter = Paths.get(bd.uri)
        if (Files.isRegularFile(srcBefore) && Files.isRegularFile(srcAfter)) {
          val tb = new String(Files.readAllBytes(srcBefore), UTF_8)
          val afterBytes = Files.readAllBytes(srcAfter)
          val ta = new String(afterBytes, UTF_8)
          val ad = if (Files.isRegularFile(cur)) s.TextDocuments.parseFrom(Files.readAllBytes(cur)).documents.find(_.uri == bd.uri) else None
          ad match {
            case Some(d) if d.md5 == md5(afterBytes) =>
              compared += 1
              val rs = ledger.getOrElse(bd.uri, Nil)
              val ob = occurrences(bd, tb, rs.filter(r => r.kind == "drop" || r.kind == "opaque").map(r => (r.origStart, r.origEnd)))
              val oa = occurrences(d, ta, rs.filter(r => r.kind == "opaque" || r.kind == "import" || r.kind == "pkg").map(r => (r.newStart, r.newEnd)))
              val (ua, ub) = unmatched(ob.map(o => (o.sym, o.role)), oa.map(o => (o.sym, o.role)))
              if (ua.nonEmpty || ub.nonEmpty) {
                changedFiles += 1
                val la = offsets(ta)
                def lineOf(off: Int): Int = { val i = java.util.Arrays.binarySearch(la, off); (if (i >= 0) i else -i - 2) + 1 }
                // pair removed and added occurrences by order, so each row names what a position denoted before and after
                val pairs = ub.zipAll(ua, -1, -1)
                pairs.foreach { case (ib, ia) =>
                  val a = if (ib >= 0) Some(oa(ib)) else None
                  val b = if (ia >= 0) Some(ob(ia)) else None
                  val off = a.map(_.start).getOrElse(-1)
                  rows += List(bd.uri, "changed", off, if (off >= 0) lineOf(off) else -1, b.map(_.sym).getOrElse(""), a.map(_.sym).getOrElse(""),
                    b.map(_.start).getOrElse(-1)).mkString("\t")
                }
              }
            case _ if tb == ta => // unchanged and not recompiled: nothing to compare
            case _ => rows += List(bd.uri, "stale", -1, -1, "", "", -1).mkString("\t")
          }
        }
      }
    }
    val res = rows.result()
    Files.write(out, res.map(_ + "\n").mkString.getBytes(UTF_8))
    println(s"compared $compared documents; ${res.count(_.contains("\tchanged\t"))} changed occurrences in $changedFiles documents; ${res.count(_.contains("\tstale\t"))} stale")
  }
}

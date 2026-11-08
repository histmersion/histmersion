//> using scala 2.13.18
//> using dep org.scalameta::scalameta:4.17.4

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import scala.meta._

/** Step 3a: once every newtype has `CanEqual[T, T]` (com.histmersion.Newtype), Scala 3 refuses `==`/`!=` between a
  * newtype and a value of another type that has a reflexive CanEqual (another newtype, String, Int, ...), even without
  * -language:strictEquality (E172). Such a comparison compared the two raw values before, since an opaque type erases
  * to its base; this repair spells that out, `a.value == b.value` (or `a.value == "x"`), so the program computes
  * what it computed and the place stays visible to the guideline inventory (G49/silentCompare) for a fix by concept.
  *
  *   scala-cli run --server=false -q tools/refactor/StrictEqualityRepair.scala -- <compile.log> <out.tsv>
  *
  * Reads the E172 errors of the log; at each, finds the `==`/`!=` starting there and appends `.value` to every side
  * whose type is not a plain base type (parenthesised when it is not a simple path). Writes one row per edit:
  * path, line, lhs type, rhs type, before, after. Errors it cannot place are written with `unplaced`. */
object StrictEqualityRepair {
  val plain = Set("String", "Int", "Long", "Double", "Float", "Boolean", "Short", "Byte", "Char", "BigDecimal", "BigInt", "Unit", "Null")
  val Err = """.*\[error\] -- \[E172\] Type Error: (\S+\.scala):(\d+):(\d+).*""".r
  val Types = """.*Values of types (.+) and (.+) cannot be compared with == or !=.*""".r

  def isPlain(t: String): Boolean = { val last = t.split('.').last.trim; plain(last) || t.contains("[") || t.contains("|") }
  def simple(t: Term): Boolean = t match {
    case _: Term.Name | _: Term.Select | _: Term.Apply | _: Term.ApplyType | _: Lit => true
    case _ => false
  }

  def main(args: Array[String]): Unit = {
    val lines = Files.readAllLines(Paths.get(args(0)), UTF_8).asScala.toList.map(_.replaceAll("\u001b\\[[0-9;]*m", ""))
    val errs = lines.zipWithIndex.collect { case (Err(p, l, c), i) =>
      val tys = lines.slice(i + 1, i + 12).collectFirst { case Types(a, b) => (a.trim, b.trim) }
      (p, l.toInt, c.toInt, tys)
    }.distinct
    val here = Paths.get("").toAbsolutePath.toString + "/"
    val rows = List.newBuilder[String]
    errs.groupBy(_._1).foreach { case (path, es) =>
      val text = new String(Files.readAllBytes(Paths.get(path)), UTF_8)
      val tree = dialects.Scala3(text).parse[Source].get
      val starts = { val b = scala.collection.mutable.ArrayBuffer(0); text.zipWithIndex.foreach { case (ch, i) => if (ch == '\n') b += i + 1 }; b }
      var edits = List.empty[(Int, Int, String)] // start, end, replacement
      es.foreach { case (_, line, col, tys) =>
        val off = starts(line - 1) + col // the compiler reports 0-based columns
        val hit = tree.collect { case i: Term.ApplyInfix if (i.op.value == "==" || i.op.value == "!=") && i.pos.start == off && i.argClause.values.size == 1 => i }.headOption
        (hit, tys) match {
          case (Some(i), Some((ta, tb))) =>
            val sides = List((i.lhs, ta), (i.argClause.values.head, tb)).filterNot { case (_, t) => isPlain(t) }
            if (sides.isEmpty) rows += List(path.replace(here, ""), line, ta, tb, i.syntax, "unplaced: both plain").mkString("\t")
            sides.foreach { case (t, _) =>
              val src = text.substring(t.pos.start, t.pos.end)
              edits ::= ((t.pos.start, t.pos.end, if (simple(t)) src + ".value" else "(" + src + ").value"))
            }
            if (sides.nonEmpty) {
              val after = List(i.lhs, i.argClause.values.head).map { t =>
                val src = text.substring(t.pos.start, t.pos.end)
                if (sides.exists(_._1 eq t)) (if (simple(t)) src + ".value" else "(" + src + ").value") else src
              }
              rows += List(path.replace(here, ""), line, ta, tb, i.syntax, after.mkString(" " + i.op.value + " ")).mkString("\t")
            }
          case _ => rows += List(path.replace(here, ""), line, tys.map(_._1).getOrElse("?"), tys.map(_._2).getOrElse("?"), "", "unplaced").mkString("\t")
        }
      }
      val sorted = edits.distinct.sortBy(-_._1)
      val out = sorted.foldLeft(text) { case (t, (s, e, r)) => t.substring(0, s) + r + t.substring(e) }
      if (sorted.nonEmpty) Files.write(Paths.get(path), out.getBytes(UTF_8))
    }
    val res = rows.result()
    Files.write(Paths.get(args(1)), res.map(_ + "\n").mkString.getBytes(UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
    println(s"${errs.size} E172 errors, ${res.count(!_.contains("\tunplaced"))} repaired, ${res.count(_.contains("\tunplaced"))} unplaced")
  }
}

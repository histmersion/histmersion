package histdb.core

import org.yaml.snakeyaml.Yaml
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path}

/** Parse a `wiki/sources/<citekey>.md` note's YAML frontmatter into a [[Source]] with its findings. */
object Frontmatter:

  def parseFile(p: Path): Option[Source] =
    val text            = Files.readString(p)
    val fallbackCitekey = p.getFileName.toString.stripSuffix(".md")
    extractYaml(text).map { y =>
      val m   = parse(y)
      val src = toSource(m, fallbackCitekey).copy(ocrMarkdown = fullText(p, m))
      src.copy(edges = edgesOf(src.citekey, m, text))
    }

  private val CkLink = """\[\[([a-z][a-z0-9]*_[a-z0-9_]+)(?:\|[^\]]*)?\]\]""".r
  private val RelTag = """^(refutes|challenges|updates|supersedes)_(.+)$""".r

  /** Typed, directed reference edges out of this note. */
  private def edgesOf(citekey: String, m: Map[String, Any], noteText: String): List[Edge] =
    val superseded = str(m, "superseded_by").map(d => Edge(citekey, d, "superseded_by")).toList
    val rel        = list(m, "topics").collect { case RelTag(kind, dst) => Edge(citekey, dst, kind) }
    val links      = CkLink.findAllMatchIn(noteText).map(_.group(1)).filter(_ != citekey).toSet.toList
      .map(dst => Edge(citekey, dst, "references"))
    (superseded ++ rel ++ links).distinct

  /** Read the converted body `md/<citekey>/paper.md` (path in `md_path`, relative to the repo root =
    * three parents up from `wiki/sources/<ck>.md`). None if absent. */
  private def fullText(notePath: Path, m: Map[String, Any]): Option[String] =
    str(m, "md_path").flatMap { rel =>
      Option(notePath.getParent)
        .flatMap(a => Option(a.getParent))
        .flatMap(a => Option(a.getParent))
        .map(_.resolve(rel))
        .filter(Files.exists(_))
        .map(Files.readString)
        .map(_.filterNot(_.toInt == 0)) // Postgres text rejects NUL bytes (present in some OCR output)
    }

  private def extractYaml(text: String): Option[String] =
    val t = text.stripPrefix("﻿")
    if !t.startsWith("---") then None
    else
      val rest = t.drop(3)
      val end  = rest.indexOf("\n---")
      if end < 0 then None else Some(rest.substring(0, end))

  private def parse(yaml: String): Map[String, Any] =
    Option(new Yaml().load[Any](yaml)) match
      case Some(m: java.util.Map[?, ?]) => m.asScala.toMap.map((k, v) => k.toString -> v)
      case _                            => Map.empty

  // ── null-safe accessors over a decoded YAML map ─────────────────────────────────────────────
  private def raw(m: Map[String, Any], k: String): Option[Any] = m.get(k).flatMap(v => Option(v))
  private def str(m: Map[String, Any], k: String): Option[String] =
    raw(m, k).map(_.toString).filter(s => s.nonEmpty && s != "null")
  private def list(m: Map[String, Any], k: String): List[String] = raw(m, k) match
    case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
    case _                          => Nil
  private def int(m: Map[String, Any], k: String): Option[Int] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.intValue)
    case Some(o)                   => o.toString.toIntOption
    case None                      => None
  private def long(m: Map[String, Any], k: String): Option[Long] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.longValue)
    case Some(o)                   => o.toString.toLongOption
    case None                      => None
  private def double(m: Map[String, Any], k: String): Option[Double] = raw(m, k) match
    case Some(n: java.lang.Number) => Some(n.doubleValue)
    case Some(o)                   => o.toString.toDoubleOption
    case None                      => None
  private def period(m: Map[String, Any]): Option[Period] =
    (long(m, "period_start"), long(m, "period_end")) match
      case (Some(a), Some(b)) => Some(Period(a, b))
      case _                  => None

  private def toSource(m: Map[String, Any], fallbackCitekey: String): Source =
    val citekey = str(m, "citekey").getOrElse(fallbackCitekey)
    Source(
      citekey = citekey,
      title = str(m, "title").getOrElse(fallbackCitekey),
      authors = list(m, "authors"),
      doi = str(m, "doi"),
      year = int(m, "year"),
      venue = str(m, "venue"),
      status = SourceStatus.fromString(str(m, "status").getOrElse("unassessed")),
      topics = list(m, "topics"),
      regions = list(m, "regions"),
      tags = list(m, "tags"),
      period = period(m),
      summary = str(m, "summary"),
      citedByCount = int(m, "cited_by_count"),
      influentialCitations = int(m, "influential_citations"),
      provenance = str(m, "source"),
      calibreId = str(m, "calibre_id"),
      findings = findings(m, citekey)
    )

  private def findings(m: Map[String, Any], citekey: String): List[Finding] =
    raw(m, "findings") match
      case Some(l: java.util.List[?]) =>
        l.asScala.toList.zipWithIndex.collect { case (fm: java.util.Map[?, ?], i) =>
          val f   = fm.asScala.toMap.map((k, v) => k.toString -> v)
          val geo = (double(f, "lon"), double(f, "lat")) match
            case (Some(x), Some(y)) => Some(GeoPoint(x, y))
            case _                  => None
          Finding(
            id = s"$citekey#$i",
            citekey = citekey,
            claim = str(f, "claim").getOrElse(""),
            region = str(f, "region"),
            geo = geo,
            period = period(f),
            periodName = str(f, "period_name"),
            tags = list(f, "tags"),
            confidence = str(f, "confidence")
          )
        }
      case _ => Nil

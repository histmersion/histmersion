package histdb.core

import org.yaml.snakeyaml.Yaml
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path}

/** Parse a `wiki/sources/<citekey>.md` note's YAML frontmatter into a [[Source]] (findings deferred). */
object Frontmatter:

  def parseFile(p: Path): Option[Source] =
    val text            = Files.readString(p)
    val fallbackCitekey = p.getFileName.toString.stripSuffix(".md")
    extractYaml(text).map(y => toSource(parse(y), fallbackCitekey))

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

  private def toSource(m: Map[String, Any], fallbackCitekey: String): Source =
    // `Option(v)` collapses a present-but-null YAML value (e.g. `doi: null`) to None.
    def raw(k: String): Option[Any] = m.get(k).flatMap(v => Option(v))
    def str(k: String): Option[String] =
      raw(k).map(_.toString).filter(s => s.nonEmpty && s != "null")
    def list(k: String): List[String] = raw(k) match
      case Some(l: java.util.List[?]) => l.asScala.toList.flatMap(x => Option(x)).map(_.toString)
      case _                          => Nil
    def int(k: String): Option[Int] = raw(k) match
      case Some(n: java.lang.Number) => Some(n.intValue)
      case Some(o)                   => o.toString.toIntOption
      case None                      => None
    def long(k: String): Option[Long] = raw(k) match
      case Some(n: java.lang.Number) => Some(n.longValue)
      case Some(o)                   => o.toString.toLongOption
      case None                      => None

    val period = (long("period_start"), long("period_end")) match
      case (Some(a), Some(b)) => Some(Period(a, b))
      case _                  => None

    Source(
      citekey = str("citekey").getOrElse(fallbackCitekey),
      title = str("title").getOrElse(fallbackCitekey),
      authors = list("authors"),
      doi = str("doi"),
      year = int("year"),
      venue = str("venue"),
      status = SourceStatus.fromString(str("status").getOrElse("unassessed")),
      topics = list("topics"),
      regions = list("regions"),
      tags = list("tags"),
      period = period,
      summary = str("summary"),
      citedByCount = int("cited_by_count"),
      influentialCitations = int("influential_citations"),
      provenance = str("source"),
      calibreId = str("calibre_id"),
      findings = Nil
    )

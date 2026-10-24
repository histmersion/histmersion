package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor
import scala.jdk.CollectionConverters.*

/** The resolution file of `merge plan --resolutions` (docs/research-merge-import.md §6.3): read it,
  * and write the template `--write-template` starts one from. Pure.
  *
  * {{{
  * # resolutions.yaml — plan 7c41e9a2 · left = this collection, right = the bundle
  * bundle: 0192f6c4-…
  * policy:
  *   editions: separate          # every year-gap question at once: merge | separate
  * resolve:
  *   S-3c1e7a2b: map:guterbock1997_perspectives
  *   A-17b0d2aa:                 # still open
  * }}}
  *
  * An id with no value is still open. Unknown keys are errors, so a typo never passes for a decision.
  * `texts:` holds the merged text of a `file:` decision made on the web review screen (a literal block
  * per issue id), so the file carries it and no separate file is needed. */
object MergeResolutionsFile:

  private def yaml = Yaml(SafeConstructor(LoaderOptions()))

  def parse(text: String): Either[List[String], MergeResolutions] =
    scala.util.Try(Option(yaml.load[AnyRef](text))).toEither.left.map(e => List(s"not YAML: ${e.getMessage}")).flatMap {
      case None => Right(MergeResolutions())
      case Some(m: java.util.Map[?, ?]) =>
        val top = m.asScala.toMap.map((k, v) => String.valueOf(k) -> v.asInstanceOf[AnyRef])
        val errors = List.newBuilder[String]
        top.keySet.diff(Set("bundle", "plan", "policy", "resolve", "texts")).toList.sorted.foreach(k =>
          errors += s"unknown key '$k' (bundle, policy, resolve, texts)")
        val bundle = top.get("bundle").flatMap(Option(_)).map(String.valueOf).flatMap { b =>
          scala.util.Try(java.util.UUID.fromString(b.trim)).toOption.orElse { errors += s"bundle: '$b' is not a bundle id"; None }
        }
        val editions = top.get("policy").flatMap(Option(_)) match
          case None => None
          case Some(p: java.util.Map[?, ?]) =>
            val pm = p.asScala.toMap.map((k, v) => String.valueOf(k) -> v.asInstanceOf[AnyRef])
            pm.keySet.diff(Set("editions")).toList.sorted.foreach(k => errors += s"policy: unknown key '$k' (editions)")
            pm.get("editions").flatMap(Option(_)).map(String.valueOf).flatMap { e =>
              EditionsPolicy.values.find(_.toString.equalsIgnoreCase(e.trim))
                .orElse { errors += s"policy.editions: '$e' is not merge or separate"; None }
            }
          case Some(other) => errors += s"policy: expected a map, found '$other'"; None
        val resolve = top.get("resolve").flatMap(Option(_)) match
          case None => Nil
          case Some(r: java.util.Map[?, ?]) =>
            r.asScala.toList.flatMap { (k, v) =>
              Option(v).map(String.valueOf).map(_.trim).filter(_.nonEmpty).map(d => MergeResolution(String.valueOf(k).trim, d))
            }.sortBy(_.issue)
          case Some(other) => errors += s"resolve: expected a map of issue id → decision, found '$other'"; Nil
        // the merged texts of `file:` decisions made on the review screen, kept in the file itself
        val texts: Map[String, String] = top.get("texts").flatMap(Option(_)) match
          case None => Map.empty
          case Some(t: java.util.Map[?, ?]) =>
            t.asScala.toList.flatMap((k, v) => Option(v).map(x => String.valueOf(k).trim -> String.valueOf(x))).toMap
          case Some(other) => errors += s"texts: expected a map of issue id → text, found '$other'"; Map.empty
        texts.keySet.diff(resolve.map(_.issue).toSet).toList.sorted.foreach(k =>
          errors += s"texts: $k has a text but no decision under resolve")
        val es = errors.result()
        if es.nonEmpty then Left(es)
        else Right(MergeResolutions(resolve.map(r => r.copy(text = texts.get(r.issue).orElse(r.text))), MergePolicy(editions), bundle))
      case Some(other) => Left(List(s"expected a map at the top, found '$other'"))
    }

  /** `ID=decision` of a `--resolve` flag. */
  def flag(s: String): Either[String, MergeResolution] = MergeResolutionsText.flag(s)

  /** The template (written by [[MergeResolutionsText]], shared with the web review screen). */
  def template(p: MergePlan, current: MergeResolutions): String = MergeResolutionsText.template(p, current)

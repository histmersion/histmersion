package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.{External, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given

import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor
import scala.jdk.CollectionConverters.*
import com.histmersion.api.exchange.EditionsPolicy
import com.histmersion.api.exchange.{MergePlan, MergePolicy, MergeResolution, MergeResolutions}
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.ValidUUID
import com.histmersion.lint.{LintRule, tolerated}

/** The resolution file of `merge plan --resolutions` (docs/research-merge-import.md §6.3): read it, and write the
  * template `--write-template` starts one from. Pure.
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
  * An id with no value is still open. Unknown keys are errors, so a typo never passes for a decision. `texts:` holds
  * the merged text of a `file:` decision made on the web review screen (a literal block per issue id), so the file
  * carries it and no separate file is needed.
  */
object MergeResolutionsFile:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** The `bundle:` a resolution file names: a bundle's id. */
  type BundleId = BundleId.T
  object BundleId extends Newtype[String, ValidUUID]

  private def yaml = Yaml(SafeConstructor(LoaderOptions()))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(text: Text): Either[List[Problem], MergeResolutions] =
    scala.util
      .Try(Option(yaml.load[AnyRef](text.value)))
      .toEither
      .left
      .map(e => List(notYaml("the file", Problem.Io.Damage.Unreadable(External.Reported.of(e)))))
      .flatMap {
        case None                         => Right(MergeResolutions())
        case Some(m: java.util.Map[?, ?]) =>
          val top = m.asScala.toMap.map((k, v) => String.valueOf(k) -> v.asInstanceOf[AnyRef])
          val errors = List.newBuilder[Problem]
          top.keySet
            .diff(Set("bundle", "plan", "policy", "resolve", "texts"))
            .toList
            .sorted
            .foreach(k => errors += unknown[MergeResolutions](k, Vector("bundle", "policy", "resolve", "texts")))
          val bundle = top.get("bundle").flatMap(Option(_)).map(String.valueOf).flatMap { b =>
            scala.util.Try(java.util.UUID.fromString(b.trim)).toOption.orElse {
              errors += Problem.Invalid.malformed(BundleId.refused(b.trim)).within(Problem.Field("bundle")); None
            }
          }
          val editions = top.get("policy").flatMap(Option(_)) match
            case None                         => None
            case Some(p: java.util.Map[?, ?]) =>
              val pm = p.asScala.toMap.map((k, v) => String.valueOf(k) -> v.asInstanceOf[AnyRef])
              pm.keySet
                .diff(Set("editions"))
                .toList
                .sorted
                .foreach(k => errors += unknown[MergePolicy](k, Vector("editions"), "policy"))
              pm.get("editions").flatMap(Option(_)).map(String.valueOf).flatMap { e =>
                EditionsPolicy.values
                  .find(_.toString.equalsIgnoreCase(e.trim))
                  .orElse {
                    errors += unknown[EditionsPolicy](e, Vector("merge", "separate"), "policy", "editions"); None
                  }
              }
            case Some(_) => errors += notYaml("policy", Problem.Io.Damage.Undecodable); None
          val resolve = top.get("resolve").flatMap(Option(_)) match
            case None                         => Nil
            case Some(r: java.util.Map[?, ?]) =>
              r.asScala.toList
                .flatMap { (k, v) =>
                  Option(v)
                    .map(String.valueOf)
                    .map(_.trim)
                    .filter(_.nonEmpty)
                    .map(d =>
                      MergeResolution(MergeResolution.Issue(String.valueOf(k).trim), MergeResolution.Decision(d))
                    )
                }
                .sortBy(_.issue)
            case Some(_) => errors += notYaml("resolve", Problem.Io.Damage.Undecodable); Nil
          // the merged texts of `file:` decisions made on the review screen, kept in the file itself
          val texts: Map[MergeResolution.Issue, MergeResolution.Text] = top.get("texts").flatMap(Option(_)) match
            case None                         => Map.empty
            case Some(t: java.util.Map[?, ?]) =>
              t.asScala.toList
                .flatMap((k, v) =>
                  Option(v)
                    .map(x => MergeResolution.Issue(String.valueOf(k).trim) -> MergeResolution.Text(String.valueOf(x)))
                )
                .toMap
            case Some(_) => errors += notYaml("texts", Problem.Io.Damage.Undecodable); Map.empty
          texts.keySet
            .diff(resolve.map(_.issue).toSet)
            .toList
            .sorted
            .foreach(k => errors += Problem.Invalid.Missing(Problem.Field(s"$k"), Vector(Problem.Field("resolve"))))
          val es = errors.result()
          if es.nonEmpty then Left(es)
          else
            Right(
              MergeResolutions(
                resolve.map(r => r.modify(_.text).setTo(texts.get(r.issue).orElse(r.text))),
                MergePolicy(editions),
                bundle
              )
            )
        case Some(_) => Left(List(notYaml("the top", Problem.Io.Damage.Undecodable)))
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def notYaml(at: String, damage: Problem.Io.Damage): Problem =
    Problem.Io.Corrupt(Problem.Io.Format.Yaml, Problem.Where(s"$at"), damage)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unknown[A](key: String, known: Vector[String], at: String*)(using
      hearth.typename.TypeName[A]
  ): Problem =
    Problem.Invalid.Unknown(
      Problem.TypeName.named[A],
      Problem.Raw(s"$key"),
      known.map(k => Problem.Raw(s"$k")),
      at.toVector.map(f => Problem.Field(s"$f"))
    )

  /** `ID=decision` of a `--resolve` flag. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def flag(s: MergeResolutions.Text.Flag): Either[Problem, MergeResolution] =
    MergeResolutions.Text.flag(s)

  /** The template (written by [[MergeResolutionsText]], shared with the web review screen). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def template(p: MergePlan, current: MergeResolutions): MergeResolutions.Text.Template =
    MergeResolutions.Text.template(p, current)

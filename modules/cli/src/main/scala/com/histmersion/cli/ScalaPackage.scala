package com.histmersion
package cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A Scala package name the project's sources are written in. */
type ScalaPackage = ScalaPackage.T

object ScalaPackage extends Newtype[String, Match["[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"]]:
  given Display[T] = Display(_.value)

  type Path = Path.T
  object Path extends Newtype[String, Pure]

  def parse(raw: String): Either[String, ScalaPackage] =
    either(raw).left.map(_ => s"'$raw' is not a package name ([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*)")

  extension (pkg: ScalaPackage)
    /** `org.example` → `org/example`. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def path: Path = Path(pkg.value.replace('.', '/'))

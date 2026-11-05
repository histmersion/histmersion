package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A Scala package name the project's sources are written in. */
type ScalaPackage = ScalaPackage.T

object ScalaPackage extends RefinedType[String, Match["[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"]]:
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PathResult = PathResult.T
  object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(raw: com.histmersion.cli.ScalaPackage.ParseRaw): Either[com.histmersion.cli.ScalaPackage.ParseResultPart1, ScalaPackage] =
    either(raw).left.map(_ => s"'$raw' is not a package name ([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*)")

  extension (pkg: ScalaPackage)
    /** `org.example` → `org/example`. */
    def path: com.histmersion.cli.ScalaPackage.PathResult = pkg.value.replace('.', '/')

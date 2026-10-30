package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A Scala package name the project's sources are written in. */
type ScalaPackage = ScalaPackage.T

object ScalaPackage extends RefinedType[String, Match["[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"]]:
  def parse(raw: String): Either[String, ScalaPackage] =
    either(raw).left.map(_ => s"'$raw' is not a package name ([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*)")

  extension (pkg: ScalaPackage)
    /** `org.example` → `org/example`. */
    def path: String = pkg.value.replace('.', '/')

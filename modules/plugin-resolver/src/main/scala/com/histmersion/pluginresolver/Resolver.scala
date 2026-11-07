package com.histmersion.pluginresolver

import io.github.iltotore.iron.*

object Resolver:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Argument = Argument.T
  object Argument extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = com.histmersion.pluginresolver.Resolver.Problem
  val Error: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem
  type ExitCode = ExitCode.T
  object ExitCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileName = FileName.T
  object FileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given Ordering[T] = Ordering.String.on(_.value)
  type Sha256 = Sha256.T
  object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManifestLine = ManifestLine.T
  object ManifestLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type MavenNameRule = Match["[A-Za-z0-9_.\\-+]+"]

private[pluginresolver] def traverse[A, B](as: List[A])(f: A => Either[_root_.com.histmersion.pluginresolver.Resolver.Problem, B]): Either[_root_.com.histmersion.pluginresolver.Resolver.Problem, List[B]] =
  as.foldRight[Either[_root_.com.histmersion.pluginresolver.Resolver.Problem, List[B]]](Right(Nil))((a, acc) => for b <- f(a); bs <- acc yield b :: bs)

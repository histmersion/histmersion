package com.histmersion
package chimneyiron.client

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Citekey = Citekey.T
object Citekey extends Newtype[String, Pure]

type Title = Title.T
object Title extends Newtype[String, Pure]

type Tag = Tag.T
object Tag extends Newtype[String, Pure]

type Year = Year.T
object Year extends Newtype[Int, Pure]

enum SourceStatusView:
  case Current
  case Contested(by: Citekey)
  case Superseded(by: Citekey)

final case class PeriodView(start: Year, end: Year)

final case class SourceView(
    citekey: Citekey,
    title: Title,
    tags: List[Tag],
    period: Option[PeriodView],
    status: SourceStatusView,
    citedBy: List[Citekey],
    replication: Option[Citekey]
)

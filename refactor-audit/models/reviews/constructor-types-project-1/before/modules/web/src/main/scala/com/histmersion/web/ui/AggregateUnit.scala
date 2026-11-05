package com.histmersion.web.ui

enum AggregateUnit(val param: Option[String]):
  case Sources extends AggregateUnit(None)
  case Findings extends AggregateUnit(Some("findings"))
  case Starts extends AggregateUnit(Some("starts"))

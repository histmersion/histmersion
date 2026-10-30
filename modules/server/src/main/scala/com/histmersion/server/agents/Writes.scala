package com.histmersion.server.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** What a role may change: nothing; a typed answer code writes (`propose` — every embedded step); or directly (an
  * interactive role).
  */
enum Writes(val wire: String):
  case None extends Writes("none")
  case Propose extends Writes("propose")
  case Direct extends Writes("direct")

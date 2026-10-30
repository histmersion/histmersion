package com.histmersion.domain

import com.histmersion.values.MediaRole

/** `P18:image,P242:locator-map` (config) → `List(P18 -> image, P242 -> locator-map)`. An entry that is not
  * `P<digits>:<known role>` is dropped, as is a second entry for the same property.
  */
object MediaRoles:
  def parse(s: String): List[(String, String)] =
    Option(s).toList
      .flatMap(_.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(p, r) if p.matches("P[1-9][0-9]*") && MediaRole.placeRoles.contains(r) => Some(p -> r)
          case _                                                                            => None
      }
      .distinctBy(_._1)

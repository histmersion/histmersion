package com.histmersion
package util.database

import com.augustnagro.magnum.DbCodec

/** `text[]`: the codec a `Param.A` becomes, imported as `util.database.given`. */
given DbCodec[List[String]] = DbCodec[String].biMap(_.split(',').toList, _.mkString(","))

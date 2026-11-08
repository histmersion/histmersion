package com.histmersion
package util.data

import io.github.iltotore.iron.*

/** A table of the database, by its name in the `public` schema. */
type TableName = TableName.T
object TableName extends Newtype[String, Pure]

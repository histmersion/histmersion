package com.histmersion
package upstreams.identifiers

import io.github.iltotore.iron.constraint.any.Pure

/** The MD5 digest a repository states for a file it serves, in hex. */
type Md5 = Md5.T

object Md5 extends Newtype[String, Pure]

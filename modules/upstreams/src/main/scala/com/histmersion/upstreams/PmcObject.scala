package com.histmersion.upstreams

/** One object of NCBI's PMC Article Datasets bucket, by key, with the MD5 the metadata records. */
final case class PmcObject(key: String, md5: Option[String])

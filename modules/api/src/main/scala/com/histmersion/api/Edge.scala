package com.histmersion.api

/** A directed, typed reference between sources: `src` --kind--> `dst`. kind ∈ {references, superseded_by, refutes,
  * challenges, updates, supersedes}.
  */
final case class Edge(src: String, dst: String, kind: String)

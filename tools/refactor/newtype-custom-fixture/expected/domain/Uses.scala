package com.histmersion
package domain

object Uses:
  def keys(ks: List[Citekey]): List[Citekey] = ks.sorted
  def descs(ds: List[Desc]): List[Desc] = ds.sortBy(_.value)
  def mapped(es: List[Entry]): List[Citekey] = es.map(_.key).sortBy(_.value)

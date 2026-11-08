package com.histmersion
package server

import com.histmersion.domain.{Citekey, Slug}

final class SourcesControllers:
  def get(raw: com.histmersion.api.ApiCitekey): Option[Citekey] = Citekey.option(raw.value) // assert: GuidelineInventory
  def show(c: Citekey): String = c.value // assert: GuidelineInventory.G44
  def slug(raw: String): Either[String, Slug] = Slug.either(raw) // assert: GuidelineInventory.G44conversion

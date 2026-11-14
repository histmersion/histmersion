package com.histmersion
package util.problem

/** A type's name reads as an English noun in a sentence: derived from the name, never a package, a dot or a code name.
  */
class NounSuite extends munit.FunSuite:
  import Problem.*
  import Text.{a, aNoun, counted, noun, plural}

  test("a type's own words, qualified by the type it is nested in when its own name is one word"):
    assertEquals(TypeName("com.histmersion.domain.library.Citekey").noun, "citekey")
    assertEquals(TypeName("com.histmersion.domain.library.Source.Status").noun, "source status")
    assertEquals(TypeName("com.histmersion.api.automation.Queue.QueueItem.Status").noun, "queue item status")
    assertEquals(TypeName("com.histmersion.api.automation.Queue.QueueItem.Id").noun, "queue item id")
    assertEquals(TypeName("com.histmersion.api.research.Article.Slug").noun, "article slug")
    assertEquals(TypeName("com.histmersion.api.json.StrictJson.FieldName").noun, "field name")
    assertEquals(TypeName("com.histmersion.api.research.LabelRenames.Label").noun, "label")

  test("a plural name reads in the singular; abbreviations are spelled out"):
    assertEquals(TypeName("com.histmersion.api.research.Article.Edits.Anchor").noun, "edit anchor")
    assertEquals(TypeName("com.histmersion.api.exchange.MergeResolutions").noun, "merge resolution")
    assertEquals(TypeName("com.histmersion.api.ListFilters.Term").noun, "list filter term")
    assertEquals(TypeName("com.histmersion.api.automation.Agents.ModelRef").noun, "model reference")
    assertEquals(TypeName("com.histmersion.api.automation.ModelChains.Allowance.ReservePct").noun, "reserve percentage")
    assertEquals(TypeName("com.histmersion.api.media.Iiif.Size").noun, "IIIF size")
    assertEquals(TypeName("com.histmersion.api.basemap.Focus.Bbox").noun, "focus bounding box")
    assertEquals(TypeName("scala.Int").noun, "whole number")

  test("a Wire or Spec type is named by the type it writes; Kind cases by themselves; requests do not qualify"):
    assertEquals(TypeName("com.histmersion.api.automation.ProviderKind.Wire").noun, "provider kind")
    assertEquals(TypeName("com.histmersion.api.automation.ModelChains.LimitSpec.Window").noun, "limit window")
    assertEquals(TypeName("com.histmersion.api.automation.ModelChains.Allowance.Spec").noun, "model chain allowance")
    assertEquals(TypeName("com.histmersion.api.library.DeleteBlocker.Kind.EventEdge").noun, "event edge")
    assertEquals(TypeName("com.histmersion.api.library.DeleteBlocker.Kind.Job").noun, "job")
    assertEquals(TypeName("com.histmersion.api.library.DeleteBlocker.Kind").noun, "delete blocker kind")
    assertEquals(TypeName("com.histmersion.api.catalogue.Place.Add.Kind").noun, "place kind")
    assertEquals(TypeName("com.histmersion.plugin.Plugin.Info.Id").noun, "plugin id")

  test("a or an by the first sound; a count in the plural"):
    assertEquals(a("article slug"), "an article slug")
    assertEquals(a("queue item id"), "a queue item id")
    assertEquals(a("user name"), "a user name")
    assertEquals(a("IIIF size"), "an IIIF size")
    assertEquals(a("URL"), "a URL")
    assertEquals(TypeName("com.histmersion.api.automation.Agents.ModelRef").aNoun, "a model reference")
    assertEquals(plural("finding"), "findings")
    assertEquals(plural("queue entry"), "queue entries")
    assertEquals(plural("source status"), "source statuses")
    assertEquals(TypeName("com.histmersion.domain.library.Finding").counted(Count(1L)), "1 finding")
    assertEquals(TypeName("com.histmersion.domain.library.Finding").counted(Count(3L)), "3 findings")

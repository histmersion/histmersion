package com.histmersion
package domain.library.acquisition

import com.histmersion.api
import com.histmersion.upstreams
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer
import com.histmersion.plugin.Year
import com.histmersion.plugin.WorkKey
import com.histmersion.api.library.AuthorName
import com.histmersion.plugin.Licence
import com.histmersion.upstreams.ArxivId
import com.histmersion.api.library.Acquire
import com.histmersion.api.library.Refs
import com.histmersion.api.library.Title
import com.histmersion.upstreams.archiveorg.ArchiveItem
import com.histmersion.upstreams.archiveorg.ArchiveFile
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given pluginYearToWire: Transformer[Year, api.catalogue.Year] = y => api.catalogue.Year(y.value.toLong)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given wireYearToPlugin: Transformer[api.catalogue.Year, Year] = y => Year(y.value.toInt)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given pluginAuthorToWire: Transformer[WorkKey.Authors, AuthorName] = a => AuthorName(a.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given wireAuthorToPlugin: Transformer[AuthorName, WorkKey.Authors] = a => WorkKey.Authors(a.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given pluginLicenceToWire: Transformer[Licence, api.library.WorkRef.OaLicence] = l =>
  api.library.WorkRef.OaLicence(l.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given wireLicenceToPlugin: Transformer[api.library.WorkRef.OaLicence, Licence] = l => Licence(l.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given arxivToWire: Transformer[ArxivId, api.library.FollowupRow.Arxiv] = a =>
  api.library.FollowupRow.Arxiv(a.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given citedByToWire: Transformer[FollowupRow.CitedBy, Acquire.CitedByCount] =
  c => Acquire.CitedByCount(c.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given refAuthorToWire: Transformer[RefRow.Authors, AuthorName] =
  a => AuthorName(a.value)
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
private given refRawToWire: Transformer[RefRow.Raw, Refs.ReferenceText] =
  r => Refs.ReferenceText(r.value)

extension (w: WorkRef)
  /** The work as the wire shows it, keyed by the citekey the corpus gives a work (not in the corpus yet). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: api.library.WorkRef =
    w.into[api.library.WorkRef]
      .withFieldComputed(
        _.citekey,
        w =>
          Some(
            Acquire.citekey(
              w.authors.map(a => AuthorName(a.value)),
              w.year.map(y => api.catalogue.Year(y.value.toLong)),
              w.title.map(t => Title(t.value))
            )
          )
      )
      .withFieldConst(_.inDb, api.library.WorkRef.InDb(false))
      .transform

extension (w: api.library.WorkRef)
  /** The work as the upstreams' rules read it. */
  def upstream: WorkRef =
    w.transformInto[WorkRef]

extension (r: FollowupRow)
  def wire: api.library.FollowupRow = r.into[api.library.FollowupRow].enableDefaultValues.transform

extension (r: RefRow) def wire: api.library.RefRow = r.into[api.library.RefRow].enableDefaultValues.transform

extension (i: ArchiveItem) def wire: api.library.ArchiveItem = i.transformInto[api.library.ArchiveItem]

extension (f: ArchiveFile) def wire: api.library.ArchiveFile = f.transformInto[api.library.ArchiveFile]

extension (f: api.library.ArchiveFile) def upstream: ArchiveFile = f.transformInto[ArchiveFile]

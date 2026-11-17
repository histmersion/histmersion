# Legal notice

!!! note "Not legal advice"
    This page explains how the software's licence relates to what you keep in Histmersion. It is
    not legal advice. Copyright, licensing and database rights differ between countries; when in
    doubt, ask your institution's library or legal office.

## The software's licence

Histmersion is free software, licensed under the **GNU Affero General Public License, version 3 or
(at your option) any later version** (SPDX: `AGPL-3.0-or-later`). The full text is in the
{{ repo_file("LICENSE") }} file of the source code; {{ repo_file("NOTICE") }} lists the material by
others that Histmersion ships, each under its own licence. Every part of the program is covered,
the plugin interface (`plugin-api`) and the plugins that come with Histmersion included.

Under the AGPL you may use, study, change and share the software, for any purpose. In short:

- **Running it** — for yourself or for others — needs nothing beyond keeping the licence notices.
- **Sharing a copy** (the program or a changed version of it) means passing it on under the same
  licence, together with its source code.
- **Changing it and letting other people use your changed version over a network** (for example,
  colleagues opening your instance in their browser) means offering those people the source code of
  your changed version. Every Histmersion server shows a **source** link in its About window, and
  `histmersion about` prints it: it points at the code of the exact version that is running. If you
  run a changed version, set `server.source-url` (see [Configuration](reference/configuration.md))
  to the repository that holds your changes, so the link stays true.
- **Plugins** run inside the program, so a plugin you share with others has to be under a licence
  compatible with the AGPL.

Copyright (C) 2026 Mateusz Kubuszok. The software comes with no warranty, to the extent permitted by
law; the licence text has the details.

## Your content is not covered by the software's licence

The licence covers the **program**, not the material you put into it or produce with it.

- **Your articles, notes, summaries and findings** belong to whoever holds their rights under the
  normal rules — usually you (or your employer). Using Histmersion does **not** make them AGPL, and
  you may publish, keep private or license them however you choose.
- **AI-written text** (summaries, findings, article drafts made by a model) is not made AGPL either.
  Whether such text is protected by copyright at all depends on your country and on how much of it
  you wrote or changed yourself.
- **Sources you store** — papers, books, scans, their OCR text — keep their **own** copyright and
  licence. Putting them into Histmersion changes nothing about who owns them or what you may do with
  them.
- **Data imported from outside** (places, periods, events, pictures) keeps the licence of its
  provider — for example CC0, CC BY, ODC-By or ODbL. [Open data](how-to/open-data.md) and
  [Pictures](exploring/media.md) list them, with how to credit each one.

## Paid and restricted sources

You may keep sources you bought, borrowed or reach through your institution in your own
Histmersion, the same way you may keep them on your own disk — **as long as you stay within the
terms under which you got them**. In practice:

- **A personal instance** (the default: it listens only on your own computer) is like your own
  bookshelf. Only you see what is in it.
- **A shared instance** (opened to your network with `HISTMERSION_BIND` and protected with
  `HISTMERSION_API_KEY` — see [Who can reach your server](how-to/access.md)) must be reachable **only by
  people who are themselves entitled to the sources it holds**: for example, members of the same
  institution with the same subscriptions, or people who bought the same books. Do not open an
  instance holding paid or restricted material to the public internet or to people without that
  access.
- **Backups, bundles and exports copy content.** A backup (`histmersion backup`) and a bundle
  (`histmersion bundle export`) carry the full OCR texts and the stored originals; the readable
  export (`histmersion export`) carries summaries, findings, notes and quotations. Sharing one
  shares that material: give it only to people entitled to every source in it, or leave the
  restricted works out.
- **Pictures and datasets** must be credited and reused according to their own licences, wherever
  you show them.

The software's licence does not conflict with any of this: the AGPL governs how the program may be
shared, while each source's own terms govern who may read the source. The source link a shared
instance offers leads to the program's code only — never to the sources, texts or files stored in it.

## See also

- [Responsible use](responsible-use.md) — legal acquisition, respecting each site's terms, and how
  to treat AI-written text.
- [Open-access papers](how-to/open-access-papers.md) and [Books and Calibre](how-to/books-and-calibre.md)
  — how sources come in legally.

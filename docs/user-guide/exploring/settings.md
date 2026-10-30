# Settings and the first-run setup

Histmersion keeps its settings in one plain text file on your computer, the **settings file**
(`histmersion.conf`). Settings in the web page and the first-run setup change that file for you. They
never keep settings anywhere else — not in the database — so you can always open the file in a text
editor and put things right, even when the web page, the database or the server is not running.

## The first-run setup

The first time you open Histmersion, it shows a setup of seven short steps:

1. **Welcome** — what Histmersion is.
2. **Finding open papers** — the places Histmersion asks for a free, legal copy of a paper you add, and
   your e-mail address. Crossref, OpenAlex and Unpaywall ask for an address so that they can write if
   something goes wrong. Histmersion sends it to those three services only, and to nothing else. You can
   leave it empty.
3. **Maps** — how much of the world to keep on this computer for the background map (the whole world, a
   smaller area, or none), with the size of each and the free space on your disk; and **where the map
   opens**: where you last left it, or always at a place you choose.
4. **Background data** — historical boundaries and periods, volcanic eruptions and earthquakes,
   pictures of places, and more place names. Each can be downloaded later as well.
5. **Research agents** (optional) — whether the server runs AI research agents.
6. **Calibre library** (optional) — where your Calibre library is, if you use Calibre.
7. **Ready** — what will be saved and downloaded.

Every step starts with a safe choice made for you, so **Continue** is always fine. Nothing is saved
or downloaded until you press **Finish and start the downloads** on the last step. **Skip the rest, use
the defaults** saves what you chose so far and downloads nothing. **Leave the setup without saving**
leaves it for now; it comes back the next time you open Histmersion.

The setup is shown while the settings file sets nothing and the setup has not been finished or skipped.
Finishing or skipping it writes `setup.completed = true` into the file. You can run it again at any time
from **Settings › Run the setup again**.

## Settings

**Settings** (the top bar's fourth place) has a page for each part:

| page | what you can do there |
|---|---|
| **General** | the theme and the map's motion (kept by this browser); where the map opens (for everyone) |
| **Data and downloads** | the background map, sea depth, places, periods, events and pictures: whether each is there, how big it is, **Update**, **Check for update**, **Remove**; the downloads under way with **Pause**, **Resume** and **Cancel**; how much of the disk Histmersion takes |
| **Where to look for sources** | your contact e-mail, and each open-access finder on or off |
| **AI agents** | whether research agents run, how many at once, their budgets, and where each AI provider is reached |
| **Keys and passwords** | whether each key is set and where it is kept — never the key itself |
| **Calibre and files** | your Calibre library and the server's folders |
| **Backups** | how many backups to keep, **Back up now**, and the backups there are |
| **Plugins** | each plugin on or off, and the plugins added to this install |
| **People and access** | one person for now; sharing comes later |
| **Advanced (all settings)** | every setting there is, with its default and what it does |
| **Credits and licences**, **About Histmersion** | the datasets and their licences; the version, its health and where the data is |

### Changing a setting

Change the values on a page, then press **Save**. **Undo** drops changes you have not saved. Saving
writes the changes into the settings file and the server takes them on at once. A few settings — the
server's address and port, the bundled database, the number of agent runs at once — take effect only
when the server restarts: the page says so and, where the server runs as a service, offers **Restart
the server now**.

Each setting says where its value comes from:

- **The default** — the file does not set it.
- **Set in the settings file** — with **Use the default** to take it out again.
- **Set by an environment variable** — the server's environment wins over the file. Changing the file
  has no effect until that variable is removed.

A value Histmersion could not start with is refused before anything is written, with what is wrong.
If the file was changed elsewhere since you opened the page (in a text editor, or in another window),
saving is refused too: open the page again and make the change.

### Keys and passwords

Histmersion never writes a key or password itself into the settings file from Settings. For a plugin's
key (CORE, Europeana, Smithsonian, a password manager's own password), you say **where the key is
kept**: an environment variable of the server, or an entry of KeePassXC, 1Password or Bitwarden. The
page shows only whether a key is set and where it is kept. Writing the key itself into the file is
possible, but only when you tick that you understand that anyone who can read the file can read it.

Keys that the server reads as the value itself — the server's own key for changes, an AI provider's key —
are kept in the server's environment only. [Credentials and secrets](../how-to/credentials.md) says how
to set one.

### What Settings keeps in the file

When Settings changes the file it keeps everything else in it as it is: your comments, the order, the
blank lines and anything you wrote by hand. The previous version is kept beside it as
`histmersion.conf.bak`. A setting you wrote with a substitution (`${…}`) or in a file the settings file
includes is shown but not changed: Settings says so, and you change it in the file itself.

### From the command line

The same settings, without the web page:

```bash
histmersion config get backups.keep          # one setting: its value, where it comes from, its default
histmersion config get --prefix acquire      # the settings of one part
histmersion config set backups.keep 10       # change it in the file, then the server applies it
histmersion config unset backups.keep        # back to the default
histmersion config restart                   # restart the server, for the settings that need it
```

When the server does not answer, `get`, `set` and `unset` read and change the settings file on this
computer directly (or always, with `--local`).

## A source's or an article's own page

A source or an article opened in a tab of its own has two controls above its text: **Reading width**
and **Page wide** set how wide the text runs, and the round button is the theme. Your browser remembers
both.

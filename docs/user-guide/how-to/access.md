# Who can reach your server

Histmersion decides who it lets in by **where a request comes from**. Its settings list the
networks it accepts, and for each one what a request from it must carry:

- a **trusted** network acts as you, the owner, with no sign-in. On your own computer this is the
  default: you open `http://localhost:8000` and nothing asks for a password;
- an **authenticated** network must send the server's API key (`auth.api-key`) with every request,
  reads as well as changes;
- a request from a network that neither list names is refused.

When several listed networks hold an address, the narrowest one (the longest prefix) decides. So
`127.0.0.0/8` can be trusted inside an authenticated `0.0.0.0/0`.

## Pick one of three setups

### On this computer only (the default)

Nothing to set. The server listens on `127.0.0.1`, and only programs on your computer can reach it.
The browser, the `histmersion` command and your AI assistant all work without a key.

```hocon
histmersion {
  server.host = "127.0.0.1"
  auth.trusted-networks = ["127.0.0.0/8", "::1/128"]
}
```

### On this computer and your tailnet (Tailscale)

[Tailscale](https://tailscale.com) gives each of your devices an address in `100.64.0.0/10` (and
`fd7a:115c:a1e0::/48`). Trust that range, and your phone or laptop reach the server as you do.
Listening on every interface also exposes the server to the local network you are on, so the rest
must authenticate:

```hocon
histmersion {
  server.host = "0.0.0.0"
  auth {
    api-key = "…"                             # or HISTMERSION_API_KEY in the service's environment
    trusted-networks = ["127.0.0.0/8", "::1/128", "100.64.0.0/10", "fd7a:115c:a1e0::/48"]
    authenticated-networks = ["0.0.0.0/0", "::/0"]
    hosts = ["mymac.tail1234.ts.net", "100.101.102.103"]   # the names you type in the browser
  }
}
```

Trusting the tailnet trusts **every device on it**. If you share your tailnet with others, list only
your own devices' addresses (`100.101.102.103/32`) instead of the whole range.

### An institution's or a public server

Trust no network: every request authenticates. Put the server behind a reverse proxy that serves HTTPS,
and tell the server the name it is reached by:

```hocon
histmersion {
  server {
    host = "0.0.0.0"                         # or 127.0.0.1 when the proxy runs on the same machine
    public-url = "https://histmersion.example.edu"
  }
  auth {
    api-key = "…"
    trusted-networks = []
    authenticated-networks = ["0.0.0.0/0", "::/0"]
    hosts = ["histmersion.example.edu"]
  }
}
```

A proxy on the same machine connects from `127.0.0.1`, so **remove loopback from the trusted
networks** behind one. The server also refuses to trust any request that carries a `Forwarded` or
`X-Forwarded-For` header, whatever its address: such a request came through a proxy, and its address
is the proxy's.

Signing in with personal accounts (and with an institution's single sign-on) is planned; until then
everyone who authenticates uses the one API key.

## The server refuses an open setup

The server does not start when the address it listens on can be reached from a network that is
neither trusted nor authenticated with a key. For example, `server.host = "0.0.0.0"` with only
loopback trusted stops with:

```text
Histmersion does not start: server.host = '0.0.0.0' can be reached from 0.0.0.0/0, which neither
auth.trusted-networks nor auth.authenticated-networks lists — bind to 127.0.0.1 (HISTMERSION_BIND),
or list that network as trusted or as authenticated (with auth.api-key set)
```

An address of one family (`0.0.0.0`, `192.168.1.5`) is reached from that whole family; `::` and a
host name from both IPv4 and IPv6.

## What protects a trusted computer

Because trust comes from the address, a web page you visit could try to use it: it can send requests
to `localhost` from your own browser. Two checks stop that, on every setup:

- **The host name.** A request must name the server by one of its own names: `localhost`,
  `127.0.0.1`, `[::1]`, the address in `server.host`, the host of `server.public-url`, or one listed
  in `auth.hosts`. Any other name is answered **421**. A page whose own name was pointed at your
  computer (DNS rebinding) is turned away here.
- **The page a change comes from.** A change (`POST`, `PUT`, `PATCH`, `DELETE`) sent by a browser page
  must come from the server's own page — one of those names, on the server's port — or from an origin
  listed in `auth.origins`. Any other page's change is answered **403**. A program that is no browser
  page (the `histmersion` command, an MCP client) sends no `Origin` or `Referer` and passes: the
  server sets no cookie that another page could ride on.

## What answers without a key

From an authenticated network, without the key, the server answers only:

- `GET /api/health` and `GET /api/version`;
- the web page itself (`/`, `/explore`, `/library…`, …) and its files under `/web/`, so the page can
  ask you for the key.

Everything else — the sources, the settings, the backups, the agents' state and transcripts — answers
**401** until the request carries `Authorization: Bearer <key>`.

## Sending the key

- **The web page** asks for the key once and keeps it in the browser's storage; it is sent with every
  request, never in a link. Opened from a trusted network it never asks.
- **The `histmersion` command** reads it from `HISTMERSION_TOKEN`, or else from the first line of the
  file `cli.token-file` names (keep that file readable by you alone). There is no command-line option:
  another user of the computer could read it in the process list.
- **An MCP client** sends it as a header, for example in `.mcp.json`:
  `{"type": "http", "url": "https://histmersion.example.edu/mcp", "headers": {"Authorization": "Bearer …"}}`.

The networks, `trusted-as`, `hosts` and `origins` are read when the server starts; the key itself is
read again by `histmersion config reload`.

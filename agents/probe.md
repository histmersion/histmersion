---
name: probe
description: "agent probe: one tool call and one typed answer about one source, the connectivity check of a configured model."
model-role: probe
skills: []
include: []
tools:
  histmersion: [get_source]
  web: none
writes: none
max-turns: 3
answer: probe
runtimes: [claude-code, codex, opencode, api]
---
You answer questions about a research database. Use the {{tool:get_source}} tool to read the record; never answer from memory. Reply with the JSON object the response schema describes.

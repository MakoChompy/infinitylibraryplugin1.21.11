# InfinityLibraryPlugin

A production-oriented Paper plugin for Minecraft 1.21.11 that builds a procedural, connection-point based infinite library. It includes live room registration/editing/deletion, persistent written-book capture from chiseled bookshelves, generated book-room population, a configurable stats GUI, a selection wand, a separate End Rod connection wand, lectern book tooling, vertical room connections, room transforms, and an administrative reset/start-home flow.

Build with Java 21:

```bash
gradle build
```

Primary command: `/infinitylibrary` (aliases: `/ilibrary`, `/il`). Use `/il wand` and `/il wandmode pos1|pos2` to select capture areas, `/il connectionwand` plus `/il connectionmode <direction> <width> <height> [prefix]` to click/stage connection points with an End Rod, and `/il saveroom` or `/il savedefault` to persist custom or default room templates live.

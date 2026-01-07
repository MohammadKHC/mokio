# Mokio

**Mokio** is a Kotlin Multiplatform systems library built on top of **Okio**.

Okio is an excellent I/O library, but it misses:
- OS-specific file metadata (inodes, permissions, etc)
- Process management
- Environment variables
- File system watching
- Platform-correct symbolic links
- Threads

Mokio fills these gaps with multiplatform APIs!

## Features

### Extended File Metadata
Access and modify metadata on all platforms, including:
- Creation, access, modification times, and size
- Unix: inode, device ID, permissions, link count, etc..
- Windows: read-only, hidden, system, archive flags

### Processes
Run and manage processes with:
- PID access
- stdin / stdout / stderr via Okio `Sink` / `Source`
- Synchronous lifecycle control

### Environment Variables
Get, set, and unset environment variables.

### Threads
Spawn simple threads with a minimal, multiplatform API.

### Symbolic Links
Create and read symlinks correctly[^1] across Android, Windows, and Unix.

## Status

Mokio is under active development. APIs may change as the library evolves.

[^1]: https://github.com/square/okio/issues/1728
# Vinyl Music

Standalone NeoForge 1.21.1 streaming vinyl mod.

## 0.1.1 alpha stability design

Vinyl Music now follows a deliberately separated architecture:

- the normal mod contains the blocks, items, GUI, networking, playlist logic, album data and track metadata;
- the streaming backend is isolated from NeoForge's normal mod classloader;
- Lavaplayer and its transitive dependencies are bundled as opaque `.jar.packed` files;
- the audio backend is loaded only when a track is actually resolved or played;
- playback uses NeoForge's built-in `SoundInstance#getStream` extension instead of a global SoundEngine mixin.

This keeps the audio stack from interfering with unrelated mods and prevents an audio dependency problem from breaking Vinyl Music during initial mod construction.

## Features

- Blank Vinyl and Custom Vinyl
- URL-based single-track creation
- YouTube, Spotify, SoundCloud and Bandcamp/direct URL resolving
- YouTube/Spotify/SoundCloud playlist import
- YouTube Mix/Radio playlist handling
- Built-in Record Album item
- Configurable album capacity
- Record Press GUI
- Record Player block
- Sequential album playback
- Spatial streamed audio
- Configurable player range and volume

Playlist import is a layer above the normal single-track resolver: each playlist entry is resolved through the same track system used for individual vinyls, then packed into Vinyl Music albums.

No dependency on Music Disc Maker or Additional Additions.

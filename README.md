# Music Disc Maker: Playlist Importer

NeoForge 1.21.1 addon for Music Disc Maker.

## v0.3.0

Paste a supported playlist URL into the **normal Music Disc Maker URL field**. No extra button is required.

Supported automatic playlist imports:

- YouTube playlist pages
- Spotify playlists
- Spotify albums
- SoundCloud sets/playlists

Normal single-track URLs continue to use Music Disc Maker's original behavior.

When Additional Additions is installed, imported tracks are automatically packed into empty Albums (8 tracks per album). Otherwise, the generated custom discs are delivered loose.

The importer caps one import at 64 tracks and performs network/track resolution off the Minecraft server thread.

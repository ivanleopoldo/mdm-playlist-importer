# Music Disc Maker: Playlist Importer

NeoForge 1.21.1 addon for Music Disc Maker.

## v0.4.0

The playlist controls are now visually part of the **Music Disc Maker GUI**.

The Music Disc Maker window is widened with a connected, backed GUI panel containing an **Import Playlist** button. It is no longer a floating button outside the interface.

### Supported playlist links

- YouTube `/playlist?list=...`
- YouTube watch / YouTube Music links containing `list=...`
- YouTube generated Mix/Radio links such as `list=RD...` or `list=RDEM...`
- Spotify playlists
- Spotify albums
- SoundCloud sets/playlists

Normal Music Disc Maker behavior is unchanged. Pasting a YouTube watch URL with a playlist still allows Music Disc Maker to treat it as one song unless the player explicitly presses **Import Playlist**.

When Additional Additions is installed, imported tracks are packed into empty Albums, 8 tracks per Album. Imports are capped at 64 tracks.

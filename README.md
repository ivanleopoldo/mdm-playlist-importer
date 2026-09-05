# Music Disc Maker: Playlist Importer

NeoForge 1.21.1 addon for Music Disc Maker.

## v0.4.1

Fixes the startup crash introduced in v0.4.0.

The addon no longer transforms Music Disc Maker's screen with a Mixin. Instead it uses NeoForge client screen events to draw a connected, backed side panel and add the **Import Playlist** button. This keeps Music Disc Maker's own GUI class untouched.

Supported links:

- YouTube `/playlist?list=...`
- YouTube watch / YouTube Music links containing `list=...`
- YouTube generated Mix/Radio links such as `list=RD...` or `list=RDEM...`
- Spotify playlists
- Spotify albums
- SoundCloud sets/playlists

Normal Music Disc Maker single-track behavior remains unchanged. Press **Import Playlist** only when you want the full playlist/mix.

With Additional Additions installed, imported tracks are packed into empty Albums, 8 tracks per Album. Imports are capped at 64 tracks.


## 0.4.2

- Music Disc Maker Blank Discs now stack to 64 instead of 16.
- The Music Disc Maker input slot can hold a full stack of Blank Discs.
- No MDM menu/block-entity mixin is used; the addon only changes the Blank Disc's max stack component.

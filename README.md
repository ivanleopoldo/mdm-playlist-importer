# Music Disc Maker: Playlist Importer

NeoForge 1.21.1 addon for Music Disc Maker.

Supported links:

- YouTube `/playlist?list=...`
- YouTube watch / YouTube Music links containing `list=...`
- YouTube generated Mix/Radio links such as `list=RD...` or `list=RDEM...`
- Spotify playlists
- Spotify albums
- SoundCloud sets/playlists

Normal Music Disc Maker single-track behavior remains unchanged. Press **Import Playlist** only when you want the full playlist/mix.

## 0.4.3

- Playlist import now consumes Blank Discs directly from the open Music Disc Maker input slot.
- A 20-track playlist consumes 20 Blank Discs and produces 20 individual MDM Custom Music Discs.
- Imported playlist tracks are no longer automatically packed into Additional Additions Albums.
- Generated discs go to the player's inventory; overflow drops normally.
- The addon reads Additional Additions' live `Config.ALBUM.get().capacity()` value instead of hardcoding 8.
- The side panel shows the current Additional Additions album capacity when the mod is installed.
- The playlist button clears the shared MDM URL field after dispatch so MDM's normal single-track fabrication does not also process the playlist URL.

## 0.4.2

- Music Disc Maker Blank Discs stack to 64 instead of 16.
- The Music Disc Maker input slot can hold a full stack of Blank Discs.
- No MDM menu/block-entity mixin is used; the addon only changes the Blank Disc's max stack component.

## 0.4.1

- Replaced the old Music Disc Maker screen Mixin with NeoForge screen events.
- Added the attached **Import Playlist** side panel.

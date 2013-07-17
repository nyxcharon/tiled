Tiled
=========

Tiled is a general purpose tile map editor. It is meant to be used for editing maps of any tile-based game, be it an RPG, a platformer or a Breakout clone.

Tiled is very flexible, for example there are no restrictions on map size, tile size or the number of layers or tiles. Also, it allows arbitrary properties to be set on the map, its layers, the tiles or on the objects. Its map format (TMX) is relatively easy to understand and allows a map to use multiple tilesets while also allowing each tileset to grow or shrink as necessary later.

Goals
--------

The goal is to turn tiled into a full blown map editor and level designer. This map or level can then be exported and used directely in the [EDE](https://github.com/nyxcharon/erebos-ide) for full intergration with Erebos.

TODO List:

- Clean up UI
- Preview Mode
    - Map Preview
    - Live Preview with AI
- AI
    - Define Path
    - Set Text
    - Set Animation
- Export Map
- Code Tab
- Map XML Tab


Misc
------
It was originally written in java, but the [orginal](https://github.com/bjorn/tiled) writer has since moved to using Qt.

*Why continue development on the java version?*

I use the java version of tiled in my own work, and is a very vital part of another project I work on, the [erebos](http://blackbox-software.org/erebos) game engine.


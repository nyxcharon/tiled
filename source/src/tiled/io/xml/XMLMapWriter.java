/*
 *  Tiled Map Editor, (c) 2004-2008
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Adam Turk <aturk@biggeruniverse.com>
 *  Bjorn Lindeijer <bjorn@lindeijer.nl>
 */

package tiled.io.xml;

import java.awt.Color;
import java.awt.Image;
import java.awt.Rectangle;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.zip.GZIPOutputStream;

import tiled.core.*;
import tiled.core.Map;
import tiled.io.ImageHelper;
import tiled.io.MapWriter;
import tiled.io.PluginLogger;
import tiled.mapeditor.selection.SelectionLayer;
import tiled.util.Base64;
import tiled.util.TiledConfiguration;

/**
 * A writer for Tiled's TMX map format.
 */
public class XMLMapWriter implements MapWriter
{
    private static final int LAST_BYTE = 0x000000FF;
    
    private Preferences prefs = TiledConfiguration.node("saving");
    
    public Preferences getPreferences(){
        return prefs;
    }
    
    public void setPreferences(Preferences prefs){
        this.prefs = prefs;
    }
        
    /**
     * Saves a map to an XML file.
     *
     * @param filename the filename of the map file
     */
    public void writeMap(Map map, String filename) throws Exception {
        OutputStream os = new FileOutputStream(filename);

        if (filename.endsWith(".tmx.gz")) {
            os = new GZIPOutputStream(os);
        }

        Writer writer = new OutputStreamWriter(os, Charset.forName("UTF-8"));
        XMLWriter xmlWriter = new XMLWriter(writer);

        xmlWriter.startDocument();
        writeMap(map, xmlWriter, filename);
        xmlWriter.endDocument();

        writer.flush();

        if (os instanceof GZIPOutputStream) {
            ((GZIPOutputStream)os).finish();
        }
        
        os.close();
    }

    /**
     * Saves a tileset to an XML file.
     *
     * @param filename the filename of the tileset file
     */
    public void writeTileset(TileSet set, String filename) throws Exception {
        OutputStream os = new FileOutputStream(filename);
        Writer writer = new OutputStreamWriter(os, Charset.forName("UTF-8"));
        XMLWriter xmlWriter = new XMLWriter(writer);

        xmlWriter.startDocument();
        writeTileset(set, xmlWriter, filename);
        xmlWriter.endDocument();

        writer.flush();
    }


    public void writeMap(Map map, OutputStream out) throws Exception {
        Writer writer = new OutputStreamWriter(out,Charset.forName("UTF-8"));
        XMLWriter xmlWriter = new XMLWriter(writer);

        xmlWriter.startDocument();
        writeMap(map, xmlWriter, "/.");
        xmlWriter.endDocument();

        writer.flush();
    }

    public void writeTileset(TileSet set, OutputStream out) throws Exception {
        Writer writer = new OutputStreamWriter(out, Charset.forName("UTF-8"));
        XMLWriter xmlWriter = new XMLWriter(writer);

        xmlWriter.startDocument();
        writeTileset(set, xmlWriter, "/.");
        xmlWriter.endDocument();

        writer.flush();
    }

    private void writeMap(Map map, XMLWriter w, String wp) throws IOException {
        w.writeDocType("map", null, "http://mapeditor.org/dtd/1.0/map.dtd");
        w.startElement("map");

        w.writeAttribute("version", "1.0");

        switch (map.getOrientation()) {
            case Map.MDO_ORTHO:
                w.writeAttribute("orientation", "orthogonal"); break;
            case Map.MDO_ISO:
                w.writeAttribute("orientation", "isometric"); break;
            case Map.MDO_HEX:
                w.writeAttribute("orientation", "hexagonal"); break;
            case Map.MDO_SHIFTED:
                w.writeAttribute("orientation", "shifted"); break;
        }

        w.writeAttribute("width", map.getWidth());
        w.writeAttribute("height", map.getHeight());
        w.writeAttribute("tilewidth", map.getTileWidth());
        w.writeAttribute("tileheight", map.getTileHeight());
        
        w.writeAttribute("eyeDistance", map.getEyeDistance());
        w.writeAttribute("viewportWidth", map.getViewportWidth());
        w.writeAttribute("viewportHeight", map.getViewportHeight());
        
        writeProperties(map.getProperties(), w);

        int firstgid = 1;
        for (TileSet tileset : map.getTilesets()) {
            tileset.setFirstGid(firstgid);
            writeTilesetReference(tileset, w, wp);
            firstgid += tileset.getMaxTileId() + 1;
        }

        if (prefs.getBoolean("encodeLayerData", true) && prefs.getBoolean("usefulComments", false))
            w.writeComment("Layer data is " + (prefs.getBoolean("layerCompression", true) ? "compressed (GZip)" : "") + " binary data, encoded in Base64");
        Iterator<MapLayer> ml = map.getLayers();
        while (ml.hasNext()) {
            MapLayer layer = ml.next();
            writeMapLayer(layer, w, wp);
        }

        w.endElement();
    }

    private static void writeProperties(Properties props, XMLWriter w) throws
            IOException
    {
        if (!props.isEmpty()) {
            final SortedSet<Object> propertyKeys = new TreeSet<Object>();
            propertyKeys.addAll(props.keySet());
            w.startElement("properties");
            for (Object propertyKey : propertyKeys) {
                final String key = (String) propertyKey;
                final String property = props.getProperty(key);
                w.startElement("property");
                w.writeAttribute("name", key);
                if (property.indexOf('\n') == -1) {
                    w.writeAttribute("value", property);
                } else {
                    // Save multiline values as character data
                    w.writeCDATA(property);
                }
                w.endElement();
            }
            w.endElement();
        }
    }

    /**
     * Writes a reference to an external tileset into a XML document. In the
     * case where the tileset is not stored in an external file, writes the
     * contents of the tileset instead.
     *
     * @param set the tileset to write a reference to
     * @param w   the XML writer to write to
     * @param wp  the working directory of the map
     * @throws java.io.IOException
     */
    private void writeTilesetReference(TileSet set, XMLWriter w, String wp)
        throws IOException {

        String source = set.getSource();

        if (source == null) {
            writeTileset(set, w, wp);
        } else {
            w.startElement("tileset");
            w.writeAttribute("firstgid", set.getFirstGid());
            w.writeAttribute("source", getRelativePath(wp, source));
            if (set.getBaseDir() != null) {
                w.writeAttribute("basedir", set.getBaseDir());
            }
            w.endElement();
        }
    }

    private void writeEmbeddedImage(int id, Image image, XMLWriter w, String imageSource) throws IOException 
    {
        String imageFormatName = prefs.get("imageFormat", "PNG");
        String pixelFormatName = prefs.get("pixelFormat", "A8R8G8B8");
        boolean imageIsBigEndian = prefs.getBoolean("imageIsBigEndian", true);
        
        ImageHelper.ImageFormat imageFormat = ImageHelper.ImageFormat.valueOf(imageFormatName, ImageHelper.ImageFormat.PNG);
        ImageHelper.PixelFormat pixelFormat = ImageHelper.PixelFormat.valueOf(pixelFormatName, ImageHelper.PixelFormat.A8R8G8B8);

        w.startElement("image");
        if(id != -1)
            w.writeAttribute("id", id);
        
        if(imageSource != null)
            w.writeAttribute("source", imageSource);
        
        w.writeAttribute("format", imageFormat.toString().toLowerCase());

        switch(imageFormat){
            default:
            case PNG:
                w.startElement("data");
                w.writeAttribute("encoding", "base64");
                w.writeCDATA(new String(Base64.encode(ImageHelper.imageToPNG(image))));
                w.endElement();
                break;
            case RAW:
                w.writeAttribute("pixelFormat", pixelFormat.toString());
                w.writeAttribute("byteOrder", imageIsBigEndian ? "bigEndian" : "littleEndian");
                w.writeAttribute("width", ImageHelper.getImageWidth(image));
                w.writeAttribute("height", ImageHelper.getImageHeight(image));
                w.startElement("data");
                w.writeAttribute("encoding", "base64");
                w.writeCDATA(new String(Base64.encode(ImageHelper.imageToRAW(image, pixelFormat, imageIsBigEndian))));
                w.endElement();
                break;
        }
        w.endElement();
    }
    
    private void writeTileset(TileSet set, XMLWriter w, String wp)
        throws IOException {

        String tilebmpFile = set.getTilebmpFile();
        String name = set.getName();

        w.startElement("tileset");
        w.writeAttribute("firstgid", set.getFirstGid());

        if (name != null) {
            w.writeAttribute("name", name);
        }

        if (tilebmpFile != null) {
            w.writeAttribute("tilewidth", set.getTileWidth());
            w.writeAttribute("tileheight", set.getTileHeight());

            final int tileSpacing = set.getTileSpacing();
            final int tileMargin = set.getTileMargin();
            if (tileSpacing != 0) {
                w.writeAttribute("spacing", tileSpacing);
            }
            if (tileMargin != 0) {
                w.writeAttribute("margin", tileMargin);
            }
        }

        if (set.getBaseDir() != null) {
            w.writeAttribute("basedir", set.getBaseDir());
        }

        if (tilebmpFile != null) {
            w.startElement("image");
            w.writeAttribute("source", getRelativePath(wp, tilebmpFile));

            Color trans = set.getTransparentColor();
            if (trans != null) {
                w.writeAttribute("trans", Integer.toHexString(
                            trans.getRGB()).substring(2));
            }
            w.endElement();

            // Write tile properties when necessary.
            Iterator<Object> tileIterator = set.iterator();

            while (tileIterator.hasNext()) {
                Tile tile = (Tile) tileIterator.next();
                // todo: move the null check back into the iterator?
                if (tile != null && !tile.getProperties().isEmpty()) {
                    w.startElement("tile");
                    w.writeAttribute("id", tile.getId());
                    writeProperties(tile.getProperties(), w);
                    w.endElement();
                }
            }
        } else {
            // Embedded tileset
            
            // this determines whether or not to encode the image data in base64 and write it directly into the <image> tag (under <data>
            boolean embedImageData = prefs.getBoolean("embedImages", true);
            
            // determines if the tile set has a separate image list (true), or if each <image> appears inside the <tile> it belongs to
            boolean tileSetImages = prefs.getBoolean("tileSetImages", false);
            
            if (tileSetImages) {
                
                // in this section, a tileset in the form of
                // <tileset ...>
                //    <tile id='..'...>
                //       <image>
                //         ....
                // is produced. embedImageData
                Enumeration<String> ids = set.getImageIds();
                while (ids.hasMoreElements()) {
                    String idString = ids.nextElement();
                    int id = Integer.parseInt(idString);
                    Image image = set.getImageById(id);
                    String imagePath = set.getImageSource(id);
                    
                    // if images are not to be embedded, we need the actual source
                    // path for that image. If we can't get hold of that, we'll
                    // need to embed it regardless...
                    if(!embedImageData && imagePath != null){
                        w.startElement("image");
                        w.writeAttribute("source", getRelativePath(wp, imagePath));
                        w.endElement();
                    }else
                        writeEmbeddedImage(id, image, w, set.getImageSource(id));
                }
            }

            // Check to see if there is a need to write tile elements
            Iterator<Object> tileIterator = set.iterator();
            boolean needWrite = !set.isOneForOne();

            if (!tileSetImages) {
                needWrite = true;
            } else {
                // As long as one has properties, they all need to be written.
                // TODO: This shouldn't be necessary
                while (tileIterator.hasNext()) {
                    Tile tile = (Tile)tileIterator.next();
                    if (!tile.getProperties().isEmpty()) {
                        needWrite = true;
                        break;
                    }
                }
            }

            if (needWrite) {
                tileIterator = set.iterator();
                while (tileIterator.hasNext()) {
                    Tile tile = (Tile)tileIterator.next();
                    // todo: move this check back into the iterator?
                    if (tile != null) {
                        writeTile(tile, set, wp, w);
                    }
                }
            }
        }
        w.endElement();
    }

    private static void writeObjectGroup(ObjectGroup o, XMLWriter w, String wp)
        throws IOException
    {
        Iterator<MapObject> itr = o.getObjects();
        while (itr.hasNext()) {
            writeMapObject(itr.next(), w, wp);
        }
    }

    /**
     * Writes this layer to an XMLWriter. This should be done <b>after</b> the
     * first global ids for the tilesets are determined, in order for the right
     * gids to be written to the layer data.
     */
    private void writeMapLayer(MapLayer l, XMLWriter w, String wp) throws IOException {
        boolean encodeLayerData =
                prefs.getBoolean("encodeLayerData", true);
        boolean compressLayerData =
                prefs.getBoolean("layerCompression", true) &&
                        encodeLayerData;

        Rectangle bounds = l.getBounds();

        if (l.getClass() == SelectionLayer.class) {
            w.startElement("selection");
        } else if(l instanceof ObjectGroup){
            w.startElement("objectgroup");
        } else {
            w.startElement("layer");
        }

        w.writeAttribute("name", l.getName());
        w.writeAttribute("width", bounds.width);
        w.writeAttribute("height", bounds.height);
        w.writeAttribute("viewPlaneDistance", l.getViewPlaneDistance());
        w.writeAttribute("viewPlaneInfinitelyFarAway", l.isViewPlaneInfinitelyFarAway());
        
        if (bounds.x != 0) {
            w.writeAttribute("x", bounds.x);
        }
        if (bounds.y != 0) {
            w.writeAttribute("y", bounds.y);
        }

        if (!l.isVisible()) {
            w.writeAttribute("visible", "0");
        }
        if (l.getOpacity() < 1.0f) {
            w.writeAttribute("opacity", l.getOpacity());
        }

        writeProperties(l.getProperties(), w);

        if (l instanceof ObjectGroup){
            writeObjectGroup((ObjectGroup) l, w, wp);
        } else if (l instanceof TileLayer) {
            final TileLayer tl = (TileLayer) l;
            w.writeAttribute("tileWidth", tl.getTileWidth());
            w.writeAttribute("tileHeight", tl.getTileHeight());
            w.startElement("data");
            if (encodeLayerData) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStream out;

                w.writeAttribute("encoding", "base64");

                if (compressLayerData) {
                    w.writeAttribute("compression", "gzip");
                    out = new GZIPOutputStream(baos);
                } else {
                    out = baos;
                }

                for (int y = 0; y < l.getHeight(); y++) {
                    for (int x = 0; x < l.getWidth(); x++) {
                        Tile tile = tl.getTileAt(x + bounds.x,
                                                 y + bounds.y);
                        int gid = 0;

                        if (tile != null) {
                            gid = tile.getGid();
                        }

                        out.write(gid       & LAST_BYTE);
                        out.write(gid >> 8  & LAST_BYTE);
                        out.write(gid >> 16 & LAST_BYTE);
                        out.write(gid >> 24 & LAST_BYTE);
                    }
                }

                if (compressLayerData) {
                    ((GZIPOutputStream)out).finish();
                }

                w.writeCDATA(new String(Base64.encode(baos.toByteArray())));
            } else {
                for (int y = 0; y < l.getHeight(); y++) {
                    for (int x = 0; x < l.getWidth(); x++) {
                        Tile tile = tl.getTileAt(x + bounds.x, y + bounds.y);
                        int gid = 0;

                        if (tile != null) {
                            gid = tile.getGid();
                        }

                        w.startElement("tile");
                        w.writeAttribute("gid", gid);
                        w.endElement();
                    }
                }
            }
            w.endElement();

            boolean tilePropertiesElementStarted = false;

            for (int y = 0; y < l.getHeight(); y++) {
                for (int x = 0; x < l.getWidth(); x++) {
                    Properties tip = tl.getTileInstancePropertiesAt(x, y);

                    if (tip != null && !tip.isEmpty()) {
                        if (!tilePropertiesElementStarted) {
                            w.startElement("tileproperties");
                            tilePropertiesElementStarted = true;
                        }
                        w.startElement("tile");

                        w.writeAttribute("x", x);
                        w.writeAttribute("y", y);

                        writeProperties(tip, w);

                        w.endElement();
                    }
                }
            }

            if (tilePropertiesElementStarted)
                w.endElement();
        }
        w.endElement();
    }

    /**
     * Used to write tile elements for tilesets not based on a tileset image.
     *
     * @param tile the tile instance that should be written
     * @param w the writer to write to
     * @throws IOException when an io error occurs
     */
    private void writeTile(Tile tile, TileSet set, String wp, XMLWriter w) throws IOException {
        w.startElement("tile");
        w.writeAttribute("id", tile.getId());

        //if (groundHeight != getHeight()) {
        //    w.writeAttribute("groundheight", "" + groundHeight);
        //}

        writeProperties(tile.getProperties(), w);

        boolean embedImages = prefs.getBoolean("embedImages", true);
        boolean tileSetImages = prefs.getBoolean("tileSetImages", false);
        Image tileImage = tile.getImage();

        // Write encoded data
        if (tileImage != null) {
            if (embedImages && !tileSetImages) {
                writeEmbeddedImage(-1, tileImage, w, set.getImageSource(tile.getImageId()));
            } else if (embedImages && tileSetImages) {
                w.startElement("image");
                w.writeAttribute("id", tile.getImageId());
                w.endElement();
            } else {
                String imageSource = set.getImageSource(tile.getImageId());
                w.startElement("image");
                if(imageSource != null){
                    w.writeAttribute("source", getRelativePath(wp, imageSource));
                } else {
                    // if we have no source location given, write the images 
                    // to where the map is
                    String prefix = prefs.get("tileImagePrefix", "tile");                
                    String filename = prefix + tile.getId() + ".png";
                    String path = prefs.get("maplocation", "") + filename;
                    w.writeAttribute("source", filename);
                    FileOutputStream fw = new FileOutputStream(new File(path));
                    byte[] data = ImageHelper.imageToPNG(tileImage);
                    fw.write(data, 0, data.length);
                    fw.close();
                }
                w.endElement();
            }
        }

        if (tile instanceof AnimatedTile) {
            writeAnimation(((AnimatedTile)tile).getSprite(), w);
        }

        w.endElement();
    }

    private static void writeAnimation(Sprite s, XMLWriter w) throws IOException {
        w.startElement("animation");
        for (int k = 0; k < s.getTotalKeys(); k++) {
            Sprite.KeyFrame key = s.getKey(k);
            w.startElement("keyframe");
            w.writeAttribute("name", key.getName());
            for (int it = 0; it < key.getTotalFrames(); it++) {
                Tile stile = key.getFrame(it);
                w.startElement("tile");
                w.writeAttribute("gid", stile.getGid());
                w.endElement();
            }
            w.endElement();
        }
        w.endElement();
    }

    private static void writeMapObject(MapObject mapObject, XMLWriter w, String wp)
        throws IOException
    {
        w.startElement("object");
        w.writeAttribute("name", mapObject.getName());

        if (mapObject.getType().length() != 0)
            w.writeAttribute("type", mapObject.getType());

        w.writeAttribute("x", mapObject.getX());
        w.writeAttribute("y", mapObject.getY());

        if (mapObject.getWidth() != 0)
            w.writeAttribute("width", mapObject.getWidth());
        if (mapObject.getHeight() != 0)
            w.writeAttribute("height", mapObject.getHeight());

        writeProperties(mapObject.getProperties(), w);

        if (mapObject.getImageSource().length() > 0) {
            w.startElement("image");
            w.writeAttribute("source",
                    getRelativePath(wp, mapObject.getImageSource()));
            w.endElement();
        }

        w.endElement();
    }

    /**
     * Returns the relative path from one file to the other. The function
     * expects absolute paths, relative paths will be converted to absolute
     * using the working directory.
     *
     * @param from the path of the origin file
     * @param to   the path of the destination file
     * @return     the relative path from origin to destination
     */
    public static String getRelativePath(String from, String to) {
        if(!(new File(to)).isAbsolute())
            return to;
        
        // Make the two paths absolute and unique
        try {
            from = new File(from).getCanonicalPath();
            to = new File(to).getCanonicalPath();
        } catch (IOException e) {
        }

        File fromFile = new File(from);
        File toFile = new File(to);
        Vector<String> fromParents = new Vector<String>();
        Vector<String> toParents = new Vector<String>();

        // Iterate to find both parent lists
        while (fromFile != null) {
            fromParents.add(0, fromFile.getName());
            fromFile = fromFile.getParentFile();
        }
        while (toFile != null) {
            toParents.add(0, toFile.getName());
            toFile = toFile.getParentFile();
        }

        // Iterate while parents are the same
        int shared = 0;
        int maxShared = Math.min(fromParents.size(), toParents.size());
        for (shared = 0; shared < maxShared; shared++) {
            String fromParent = fromParents.get(shared);
            String toParent = toParents.get(shared);
            if (!fromParent.equals(toParent)) {
                break;
            }
        }

        // Append .. for each remaining parent in fromParents
        StringBuffer relPathBuf = new StringBuffer();
        for (int i = shared; i < fromParents.size() - 1; i++) {
            relPathBuf.append(".." + File.separator);
        }

        // Add the remaining part in toParents
        for (int i = shared; i < toParents.size() - 1; i++) {
            relPathBuf.append(toParents.get(i) + File.separator);
        }
        relPathBuf.append(new File(to).getName());
        String relPath = relPathBuf.toString();

        // Turn around the slashes when path is relative
        try {
            String absPath = new File(relPath).getCanonicalPath();

            if (!absPath.equals(relPath)) {
                // Path is not absolute, turn slashes around
                // Assumes: \ does not occur in filenames
                relPath = relPath.replace('\\', '/');
            }
        } catch (IOException e) {
        }

        return relPath;
    }

    /**
     * @see tiled.io.PluggableMapIO#getFilter()
     */
    public String getFilter() throws Exception {
        return "*.tmx,*.tsx,*.tmx.gz";
    }

    public String getPluginPackage() {
        return "Tiled internal TMX reader/writer";
    }

    public String getDescription() {
        return
            "The core Tiled TMX format writer\n" +
            "\n" +
            "Tiled Map Editor, (c) 2004-2008\n" +
            "Adam Turk\n" +
            "Bjorn Lindeijer";
    }

    public String getName() {
        return "Default Tiled XML (TMX) map writer";
    }

    public boolean accept(File pathname) {
        try {
            String path = pathname.getCanonicalPath();
            if (path.endsWith(".tmx") || path.endsWith(".tsx") || path.endsWith(".tmx.gz")) {
                return true;
            }
        } catch (IOException e) {}
        return false;
    }

    public void setLogger(PluginLogger logger) {
    }
}

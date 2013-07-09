
package tiled;

import tiled.command.CommandInterpreter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import tiled.mapeditor.MapEditor;
import tiled.util.TiledConfiguration;

/**
 *
 * @author upachler
 * @author nyxcharon
 * Last Modified 4/29/2013
 */
public class Main {
    /**
     * Starts Tiled.
     *
     * @param args the first argument may be a map file
     */
    public static void main(String[] args) 
    {
        
        boolean themeset=false;
        //Try to set GTK first, otherwise we'll just use the system theme
        //TODO: Add a menu entry to let the user pick the theme
        try 
        {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
            themeset=true;
        } 
        catch (ClassNotFoundException ex) {} 
        catch (InstantiationException ex) {} 
        catch (IllegalAccessException ex) {} 
        catch (UnsupportedLookAndFeelException ex) {}
        
        if(!themeset)
        {
            try 
            {
                UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName());
            } 
           catch (ClassNotFoundException ex) {} 
           catch (InstantiationException ex) {} 
           catch (IllegalAccessException ex) {} 
           catch (UnsupportedLookAndFeelException ex) {}
        }
        
        
        if(args.length > 0)
        {
            if(args[0].equals("-commandmode"))
            {
                CommandInterpreter i = new CommandInterpreter();
                int result = i.interpret(args, 1);
                System.exit(result);
            } 
            else if(args[0].equals("-?") || args[0].equals("-help"))
            {
                printHelpMessage();
            } 
            else 
            {
                File file = new File(args[0]);
                try 
                {
                    String path = file.getCanonicalPath();
                    MapEditor editor = new MapEditor();
                    editor.loadMap(path);
                } 
                catch (IOException ex) 
                {
                }
            }
        }
        else
        {
            MapEditor editor = new MapEditor();
            if (TiledConfiguration.node("io").getBoolean("autoOpenLast", false)) 
            {
                // Load last map if it still exists
                java.util.List<String> recent = TiledConfiguration.getRecentFiles();
                if (!recent.isEmpty()) {
                    String filename = recent.get(0);
                    if (new File(filename).exists()) {
                        editor.loadMap(filename);
                    }
                }
            }        
        }
    }

    private static void printHelpMessage() 
    {
        PrintStream o = System.out;
        o.println();
        o.println("Start tiled with no parameters to just start the editor.\n" +
            "When a parameter is given, it can either be a file name or an \n" +
            "option starting with '-'. These options are available:\n" +
            "\n" +
            "-?\n" +
            "-help\n" +
            "\tdisplays this help message\n" +
            "\n" +
            "-commandmode <command> [parameter] { <command> [parameter] }\n" +
            "\twill start tiled in command interpreter mode. All commands are\n" +
            "\tread from the command line after the -commandmode option.\n" +
            "\tFor a list of available commands, run tiled with\n" +
            "\t'-commandmode help' (which runs the help command)\n");
        o.println();
    }
}

package de.nigjo.wreckfest.evtloop;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

//
//Official tracks list (outdated, 2022-02-20)
//https://steamcommunity.com/sharedfiles/filedetails/?id=1421992660
//
//A simple (text) list of all Tracks:
//https://pingperfect.com/index.php/knowledgebase/318/Wreckfest--List-of-maps.html
//
//Wiki Page with Tracks
//https://wreckfest.fandom.com/wiki/Category:Maps
//
//"Wreckfest Chronicle"
//https://docs.google.com/spreadsheets/d/18-g1-I68g1B-9bumvV1HxUVcuD41DBczCyUGH3rdNiQ/edit#gid=145736514
//-> "Fast List of Stock Track IDs"
/**
 * Create a Wreckfest server config with randomized event loop.
 *
 * Start the server with
 *
 * Wreckfest_x64 -s server_config=randomMaps.cfg
 *
 * @author nigjo
 */
public class RandomMaps
{
  private static final int LOG_LINE_LENGTH = 30;

  static class Track
  {
    static String lastName;
    String name;
    String kind;
    String racetype;
    String areatype;
    String id;

    static Track of(String line)
    {
      String[] data = line.split("\t");
      Track track = new Track();
      track.name = data[0];
      if(track.name.isBlank())
      {
        track.name = lastName;
      }
      else
      {
        lastName = track.name;
      }
      track.racetype = data[1];
      track.areatype = data[2];
      track.kind = track.racetype.isBlank() ? track.areatype : track.racetype;
      track.id = data[3];
      return track;
    }
  }

  /*"bots", "ai_difficulty", */
  static List<String> trackSettingKeys = Arrays.asList(
      "disabled",
      "gamemode", "num_teams", "laps", "time_limit",
      "elimination_interval", "vehicle_damage", "car_class_restriction",
      "car_restriction", "special_vehicles_disabled", "car_reset_disabled",
      "car_reset_delay", "wrong_way_limiter_disabled", "weather");
  static List<String> raceOnlyKeys = Arrays.asList(
      "num_teams", "laps", "elimination_interval",
      "wrong_way_limiter_disabled");
  static List<String> derbyOnlyKeys = Arrays.asList(
      "time_limit");

  private static void log(String pattern, Object... args)
  {
    if(args.length == 0)
    {
      System.err.println(pattern);
    }
    else
    {
      System.err.printf(pattern + "%n", args);
    }
  }

  private static Properties loadProperties(String filename) throws IOException
  {
    Properties settings = new Properties();
    settings.setProperty("RandomMaps.teamModeThreshold", "1");
    settings.setProperty("RandomMaps.deathmatchThreshold", "1");

    if(Files.exists(Path.of(filename)))
    {
      try( InputStream in = Files.newInputStream(Path.of(filename)))
      {
        settings.load(in);
      }
    }
    return settings;
  }

  private static boolean parseArgs(Map<String, Object> config, String... args)
      throws Exception
  {
    config.putIfAbsent("help", "false");
    String option = null;
    for(String arg : args)
    {
      if(option != null)
      {
        config.put(option, arg);
        option = null;
      }
      else if(arg.startsWith("--"))
      {
        option = arg.substring(2);
        if(!config.containsKey(option))
        {
          log("Unknown option \"%s\"", arg);
          return false;
        }
        switch(option)
        {
          case "help":
            System.err.println("java RandomMaps.java [<options>]");
            System.err.println();
            System.err.println("<options>:");
            for(String optionKey : new TreeSet<>(config.keySet()))
            {
              System.err.print("--" + optionKey);
              if(HELP.containsKey(optionKey))
              {
                System.err.print(" ");
                System.err.print(HELP.get(optionKey));
              }
              System.err.println();
            }
            return false;
        }
        if(config.get(option) instanceof Boolean)
        {
          config.put(option, true);
          option = null;
        }
      }
      else
      {
        log("Unknown argument \"%s\"", arg);
        return false;
      }
    }

    return true;
  }

  private static final Map<String, String> HELP = Map.of(
      "help", "this help.",
      "output", "<filename> of the output config file. Defaults to 'RandomMaps.cfg'.",
      "base", "<filename> of the base config file without an event loop."
      + " Is copied to the output before the event loop."
      + " Default is 'RandomMaps_base.cfg'",
      "maps", "<filenam> of a tab separated file with track list."
      + " Default is 'RandomMaps.tsv'.",
      "settings", "<filename> of a file with pre track settings."
      + " Default is 'RandomMaps.properties'",
      "echoSettings", "echos the current 'settings' to stdout."
  );

  private static Map<String, Object> getConfigDefaults()
  {
    Map<String, Object> config = new HashMap<>();
    config.put("output", "RandomMaps.cfg");
    config.put("base", "RandomMaps_base.cfg");
    config.put("maps", "RandomMaps_tracks.tsv");
    config.put("settings", "RandomMaps.properties");
    config.put("echoSettings", Boolean.FALSE);
    return config;
  }

  public static void main(String... args) throws Exception
  {
    Map<String, Object> config = getConfigDefaults();
    if(!parseArgs(config, args))
    {
      return;
    }

    //String filebase = RandomMaps.class.getSimpleName();
    Files.copy(
        Path.of((String)config.get("base")),
        Path.of((String)config.get("output")),
        StandardCopyOption.REPLACE_EXISTING
    );

    List<Track> tracks = loadTracks((String)config.get("maps"));
    log("Tracks found: %d", tracks.size());

    log("Event loop:");
    log("-".repeat(LOG_LINE_LENGTH));

    Properties toolSettings = loadProperties((String)config.get("settings"));

    Map<String, Map<String, String>> tracksettings = createEventLoop(tracks,
        (String)config.get("output"), toolSettings);

    log("-".repeat(LOG_LINE_LENGTH));
    if(Boolean.TRUE.equals(config.get("echoSettings")))
    {
      log("echo properties:");
      String mappedKey = "RandomMaps|base settings|RandomMaps";
      printToolProperties(Map.of(mappedKey,
          new TreeSet<>(toolSettings.stringPropertyNames()).stream()
              .filter(key -> key.startsWith("RandomMaps."))
              .collect(Collectors.toMap(
                  key -> key.substring(key.indexOf('.') + 1),
                  key -> (String)toolSettings.get(key)
              ))
      ));
      printToolProperties(tracksettings);
      log("-".repeat(LOG_LINE_LENGTH));
    }
  }

  private static List<Track> loadTracks(String tracksfile) throws IOException
  {
    var alltracks =
        Files.lines(Path.of(tracksfile), StandardCharsets.UTF_8)
            .filter(line -> !line.startsWith("#"))
            .map(Track::of)
            .collect(Collectors.toList());
    var tracks = alltracks.stream()
        //.filter(t -> !Boolean.parseBoolean(settings.getProperty(t.id + ".disabled")))
        .collect(Collectors.toList());
    return tracks;
  }

  private static void printToolProperties(
      Map<String, Map<String, String>> tracksettings)
  {
    tracksettings.forEach((keyname, data) ->
    {
      String[] info = keyname.split("\\|");
      System.out.println("## \"" + info[0] + "\" - " + info[1]);
      String id = info[2];
      for(Map.Entry<String, String> e : data.entrySet())
      {
        String key = e.getKey();
        if(key.charAt(0) == '#')
        {
          key = '#' + id + '.' + key.substring(1);
        }
        else
        {
          key = id + '.' + key;
        }
        System.out.println(key + "=" + e.getValue());
      }
      System.out.println();
    });
  }

  private static Map<String, Map<String, String>> createEventLoop(
      List<Track> tracks, String outputFile, Properties toolSettings)
      throws IOException
  {
    Properties serverSettings = loadProperties(outputFile);
    serverSettings.putIfAbsent("disabled", "true");

    Map<String, Map<String, String>> tracksettings = new TreeMap<>();
    Random rnd = new Random(System.currentTimeMillis());
    int teamModeThreshold = Integer.parseInt(
        toolSettings.getProperty("RandomMaps.teamModeThreshold", "1"));
    int deathmatchThreshold = Integer.parseInt(
        toolSettings.getProperty("RandomMaps.deathmatchThreshold", "1"));

    var lastMaps = new ArrayList<String>();
    String lastKind = null;
    try( BufferedWriter out = Files.newBufferedWriter(Path.of(outputFile),
        StandardOpenOption.APPEND))
    {
      while(!tracks.isEmpty())
      {
        int scancounter = 5;
        int index;
        Track next;
        do
        {
          index = rnd.nextInt(tracks.size());
          next = tracks.get(index);
          if(!next.kind.equals(lastKind) && !lastMaps.contains(next.name))
          {
            break;
          }
        }
        while(--scancounter > 0);
        tracks.remove(index);

        Properties trackdata = new Properties(serverSettings);
        if(Boolean.parseBoolean(toolSettings.getProperty(next.id + ".disabled")))
        {
          log("-- skipping \"%s\" - %s", next.name, next.kind);
          trackdata.setProperty("disabled", "true");
          if(next.racetype.isEmpty())
          {
            trackdata.setProperty("gamemode", "derby");
          }
          else
          {
            trackdata.setProperty("gamemode", "racing");
          }
          copyMapdataFromGlobal(next, toolSettings, trackdata);
          addTrackProperties(trackdata, tracksettings, next);
          continue;
        }

        lastKind = next.kind;
        if(lastMaps.size() > 4)
        {
          lastMaps.remove(0);
        }
        lastMaps.add(next.name);

        out.newLine();
        out.append("el_add=")
            .append(next.id);
        out.newLine();

        //set "default" mode
        if(next.racetype.isEmpty())
        {
          if(rnd.nextInt(100) < deathmatchThreshold)
          {
            trackdata.setProperty("gamemode", "derby");
          }
          else
          {
            trackdata.setProperty("gamemode", "derby deathmatch");
          }
          trackdata.setProperty("special_vehicles_disabled", "0");
        }
        else
        {
          if(rnd.nextInt(100) < teamModeThreshold)
          {
            trackdata.setProperty("gamemode", "team race");
            //trackdata.setProperty("num_teams", trackdata.getProperty("num_teams"));
          }
          else
          {
            trackdata.setProperty("gamemode", "racing");
          }
          trackdata.setProperty("special_vehicles_disabled",
              trackdata.getProperty("special_vehicles_disabled"));
        }

        copyMapdataFromGlobal(next, toolSettings, trackdata);

        trackdata.remove("disabled");

        for(Object key : trackdata.keySet())
        {
          String name = (String)key;
          out.append("el_").append(name)
              .append("=").append(trackdata.getProperty(name));
          out.newLine();
        }

        addTrackProperties(trackdata, tracksettings, next);
        log("-- \"%s\" - %s (%s)",
            next.name, next.kind, trackdata.getProperty("gamemode"));
      }
    }

    return tracksettings;
  }

  /**
   * @param next Trackdata
   * @param settings current global settings
   * @param trackdata destination settings
   */
  private static void copyMapdataFromGlobal(Track next, Properties settings,
      Properties trackdata)
  {
    String trackId = next.id;
    settings.stringPropertyNames().stream()
        .filter(key -> key.startsWith(trackId + '.'))
        .collect(
            () -> trackdata,
            (p, key) -> p.setProperty(
                key.substring(key.indexOf('.') + 1), settings.getProperty(key)),
            (p1, p2) -> p2.clear());
  }

  private static void addTrackProperties(Properties trackdata,
      Map<String, Map<String, String>> tracksettings, Track next)
  {
    Map<String, String> mapProperties = new LinkedHashMap<>();
    trackSettingKeys.forEach(
        key -> mapProperties.put(
            (trackdata.containsKey(key) ? "" : "#") + key,
            trackdata.getProperty(key))
    );
    switch(trackdata.getProperty("gamemode"))
    {
      case "racing":
      case "team race":
        derbyOnlyKeys.forEach(key -> mapProperties.remove(key));
        derbyOnlyKeys.forEach(key -> mapProperties.remove('#' + key));
        break;
      case "derby deathmatch":
      case "derby":
        raceOnlyKeys.forEach(key -> mapProperties.remove(key));
        raceOnlyKeys.forEach(key -> mapProperties.remove('#' + key));
        break;
      default:
        log("unknown gamemode: %s", trackdata.getProperty("gamemode"));
    }
    tracksettings.put(next.name + "|" + next.kind + "|" + next.id, mapProperties);
  }
}

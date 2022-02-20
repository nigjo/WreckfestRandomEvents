
import java.io.BufferedWriter;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
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

  public static void main(String... args) throws Exception
  {
    String filebase = RandomMaps.class.getSimpleName();
    Files.copy(
        Path.of(filebase + "_base.cfg"),
        Path.of(filebase + ".cfg"),
        StandardCopyOption.REPLACE_EXISTING
    );

    URL resource = RandomMaps.class.getResource(filebase + ".properties");
    System.out.println("resource=" + resource);

    Properties settings = new Properties();
    if(Files.exists(Path.of(filebase + ".properties")))
    {
      try(InputStream in = Files.newInputStream(Path.of(filebase + ".properties")))
      {
        settings.load(in);
      }
    }

    var tracks =
        Files.lines(Path.of(filebase + "_tracks.tsv"), StandardCharsets.UTF_8)
            .filter(line -> !line.startsWith("#"))
            .map(Track::of)
            .filter(t -> !Boolean.parseBoolean(settings.getProperty(t.id + ".disabled")))
            .collect(Collectors.toList());
    System.out.println("Tracks found: " + tracks.size());

    Random rnd = new Random(System.currentTimeMillis());
    int teamModeThreshold = Integer.parseInt(
        settings.getProperty(filebase + ".teamModeThreshold", "1"));
    int deathmatchThreshold = Integer.parseInt(
        settings.getProperty(filebase + ".deathmatchThreshold", "1"));

    System.out.println("Event loop:");
    System.out.println("------------------------------");
    var lastMaps = new ArrayList<String>();
    String lastKind = null;
    try(BufferedWriter out = Files.newBufferedWriter(
        Path.of(filebase + ".cfg"), StandardOpenOption.APPEND))
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

        Properties trackdata = new Properties();
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
        }
        else if(rnd.nextInt(100) < teamModeThreshold)
        {
          trackdata.setProperty("gamemode", "team race");
        }
        else
        {
          trackdata.setProperty("gamemode", "racing");
        }
        String trackId = next.id;
        settings.stringPropertyNames().stream()
            .filter(key -> key.startsWith(trackId + '.'))
            .collect(
                () -> trackdata,
                (p, key) -> p.setProperty(
                    key.substring(key.indexOf('.') + 1), settings.getProperty(key)),
                (p1, p2) -> p2.clear());
        //System.out.println(trackdata);

        trackdata.remove("disabled");

        for(String key : trackdata.stringPropertyNames())
        {
          out.append("el_").append(key)
              .append("=").append(trackdata.getProperty(key));
          out.newLine();
        }

        System.out.println(next.name + " - " + next.kind
            + " (" + trackdata.getProperty("gamemode") + ")");
      }
    }
    System.out.println("------------------------------");
  }
}

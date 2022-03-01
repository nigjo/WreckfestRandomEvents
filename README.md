# WreckfestRandomEvents

A single file java tool to randomize a Wreckfest Server Eventloop

## create randomized server config

 1. Copy this project to a subfolder of your Wreckfest server directory.

 2. Make a copy of your `server_config.cfg` _without_ any event loop in
    the project folder and name it `RandomMaps_base.cfg`.

 3. Start the `start_server.cmd` batch file in this folder. It will
    create a new `RandomMaps.cfg` with a randomized event loop and starts the
    Wreckfest server with this configuration.


## Map configuration

All special map configurations like lap count or car restrictions are defined
via a `RandomMaps.properties` file. Each entry has the format
`<mapid>.<configkey>=<value>`. Lines starting with a hash character ("`#`")
is treated as a comment line. Beside the map configuration the tool has some
own settings keys:

<dl>
 <dt><code>RandomMaps.teamModeThreshold</code></dt>
 <dd>Value in full percent of how many "racing"-Tracks are defined as "team race".
     The default value is 1</dd>
 <dt><code>RandomMaps.deathmatchThreshold</code></dt>
 <dd>Value in full percent of how many "derby deathmatch" (="Deathmatch") Tracks
     are defined as "derby" (="Last man standing").
     The default value is 1</dd>
</dl>

To remove a track from the event loop you do not need to edit the
`RandomMaps_tracks.tsv`. Just add a `<trackid>.disabled=true` to the map
configuration file.

You can create an initial properties file via

    java RandomMaps.java --echoSettings >RandomMaps.properties

**Caution:** Don't run this command to "update" the file. This command will
first clean the file before is is read by the tool! But you can redirect to
another file and rename it afterwards.


## CLI Help

```text
java RandomMaps.java [<options>]

<options>:
--base <filename> of the base config file without an event loop. Is copied to the output before the event loop. Default is 'RandomMaps_base.cfg'
--echoSettings echos the current 'settings' to stdout.
--help this help.
--maps <filenam> of a tab separated file with track list. Default is 'RandomMaps.tsv'.
--output <filename> of the output config file. Defaults to 'RandomMaps.cfg'.
--settings <filename> of a file with pre track settings. Default is 'RandomMaps.properties'
```

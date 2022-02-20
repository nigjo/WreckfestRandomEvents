@echo off
cd /D %~dp0
java RandomMaps.java
cd ..
start /B Wreckfest_x64.exe -s server_config=%~dp0RandomMaps.cfg

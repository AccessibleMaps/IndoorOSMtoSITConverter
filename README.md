# 2SIT
A tool to transform OSM indoor maps mapped with the IndoorOSM schema to the SIT schema

## Usage 
* Set the parameters in config.json to your requirements
* Execute the script. This will generate 2 files:
  1) export/export_xx.osc - this is the osm change file containing information what has changed during the transformation
  2) logs/xx.log - contains logs about the transformation process, e.g. stats or elements that couldn't be transformed
* the next step is to load the osc file into the editor of your choice and check if the transformation was successful. 
Maybe smaller corrections need to be done here.
* If you checked the data for correctness the osc data can be committed directly to osm or be integrated with the data in
your editor

### Config.json
The config file consists out of the following values

|Parameter| Explanation | Example |
|--|--|--|
| username | the username under which the changeset is authored | "username": "testUser" |
| userId | the userId under which the changeset is authored |"userId": "123456987453"  |
| serverUrl | The osm api endpoint to use | "serverUrl": "https://api.openstreetmap.org/api/0.6/" |
| changesetId | the id given by the server for a changeset | "changesetId": "123456789" |
| maxWallWidthInM | the wall thickness threshold for horizontal merging. How far apart can two lines max be to get merged | "maxWallWidthInM": 0.4 |
| maxLevelConnectionNodeOffsetInM | the wall thickness threshold for vertical merging. | "maxLevelConnectionNodeOffsetInM": 0.6 |
| areas | a list of areas to transform | "areas": [_area1_, _area2_] |

An area is a pretty self explanatory JS object:
`{  
  "minLongitude": 49.41689,  
  "maxLongitude": 49.41969,  
  "minLatitude": 8.67180,  
  "maxLatitude": 8.67695  
}`


The `maxWallWidthInM` defines the thickness a wall can have to be recognized as one in indoorOsm, whereas the `maxLevelConnectionNodeOffsetInM` is the threshold how big levelConnections can be slipped to count as above each other. 

## Compilation
### Development
When executing maven compile one needs to add set the settings file explicitly.
```mvn -s settings.xml compile```

See the following for details:
https://stackoverflow.com/questions/44265547/how-to-properly-specify-jcenter-repository-in-maven-config

## Execution
There is a runnable standalone jar in `kp-transformation-von-indoor-karten/out/artifacts/2SIT_jar/2SIT.jar`. Standalone means you can copy it anywhere and it will run with `java -jar 2SIT.jar`, given there is a `config.json` in the same directory. 
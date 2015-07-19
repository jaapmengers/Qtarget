name := """playing-reactive-mongo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(jdbc, anorm, cache, ws)

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies ++= Seq(
  "org.reactivemongo" 		%% 	"play2-reactivemongo" 		% "0.11.0.play23",
  "org.webjars" 			%% 	"webjars-play" 				% "2.3.0",
  "org.webjars" 			%	"bootstrap" 				% "3.1.1-1",
  "org.webjars" 			% 	"bootswatch-united"			% "3.1.1",
  "org.webjars" 			% 	"html5shiv" 				% "3.7.0",
  "org.webjars" 			% 	"respond" 					% "1.4.2",
  "org.julienrf" %% "play-json-variants" % "2.0",
  "com.github.nscala-time" %% "nscala-time" % "1.8.0"
)

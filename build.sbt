scalaVersion := "3.2.1"

lazy val GeneWikiShEx2SPARQL = (project in file("."))
 .settings(
  name := "GeneWikiShEx2SPARQL",
  libraryDependencies ++= Seq(
   "com.monovore"     %% "decline-effect"      % "2.4.0",
   "es.weso"          %% "shex"                % "0.2.29",
   "es.weso"          %% "srdfjena"            % "0.1.122",
   "org.apache.jena"  %  "jena-shex"           % "4.3.2", 
   "org.slf4j"        % "slf4j-api"            % "1.7.3",
   "org.slf4j"        % "slf4j-simple"         % "1.7.3",
   "org.typelevel"    %% "cats-effect"         % "3.4.2",
   "org.http4s"       %% "http4s-circe" % "0.23.12",
   "org.apache.jena" % "jena-arq"             % "4.6.1",
  ),
  run / fork := true
 )



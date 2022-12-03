package es.weso
import com.monovore.decline._
import com.monovore.decline.effect._
import cats.effect._
import cats.implicits._
import es.weso.shex._
import fs2._
import fs2.io.file.{Files, Path => Fs2Path}
import java.nio.file.{Path, Paths}

case class ProcessCommand(
    shexPath: Path,
    outputFolder: Path,
    endpoint: String,
    runQueries: Boolean,
    storeQueries: Boolean
)

object Main extends CommandIOApp(
  name    = "GeneWikiShEx2SPARQL",
  version = "0.0.1",
  header  =
     s"""|Convert GeneWiki ShEx to SPARQL queries
         |
         |Some examples: 
         | GeneWikiShEx2SPARQL --shex shex/GeneWiki.shex --folder sparql
         |   
         |""".stripMargin.trim,
) {

 val shexPath = Opts.option[Path](
      "shex", 
      metavar = "file",
      help="ShEx file"
    ).withDefault(Paths.get("."))

 val outputPath = Opts.option[Path](
      "output", 
      metavar = "folder",
      help="Output folder to generate SPARQL queries"
    ).withDefault(Paths.get("."))

 val endpoint = Opts.option[String](
      "endpoint", 
      metavar = "IRI",
      help="Endpoint. Default = Wikidata"
    ).withDefault("https://query.wikidata.org/sparql")

 val runQueries = Opts.flag("runQueries", help = "Run queries", short="r").orFalse

 val storeQueries = Opts.flag("storeQueries", help = "Store queries", short="s").orFalse

 val showQueries = Opts.flag(long = "showQueries", help="Show queries").orTrue

 val processCommand: Opts[ProcessCommand] = 
  (shexPath, outputPath, endpoint, runQueries, storeQueries
  ).mapN(ProcessCommand.apply)

 def main: Opts[IO[ExitCode]] =
   processCommand.map(run)

 def run(pc: ProcessCommand): IO[ExitCode] = 
    getSchema(pc.shexPath).flatMap(schema => 
        createSPARQLQueries(schema).flatMap(queries => 
          cond(pc.runQueries, runQueries(pc.endpoint, queries)).flatMap(_ => 
          cond(pc.storeQueries, writeOutput(pc.outputFolder, queries))).map(_ => 
            ExitCode.Success)))


 def cond(b: Boolean, action: IO[Unit]): IO[Unit] =
  if (b) action 
  else IO(())           
 
 def getSchema(path: Path): IO[Schema] = Schema.fromFile(path.toString(), "ShExC", None, None)

 def createSPARQLQueries(schema: Schema): IO[List[QueryWrapper]] = 
    IO(schema.shapeList.map(ShapeExprParser(schema).parse(_)))

 def sep = IO.println("----------------")  

 def writeOutput(folder: Path, qs: List[QueryWrapper]): IO[Unit] = 
    qs.
    map(writeQuery(folder, _)).
    sequence.
    void

 def writeQuery(folder: Path, q: QueryWrapper): IO[Unit] = {
    val targetFile = Fs2Path(folder.toString + "/" + q.name + ".sparql")
    IO.println(s"Generating output to file: $targetFile") *> 
    Stream(q.serialize)
     .through(text.utf8.encode)
     .through(Files[IO].writeAll(targetFile)).compile.drain
 }
 
 def runQueries(endpoint: String, qs: List[QueryWrapper]): IO[Unit] =
  qs.map(runQuery(endpoint,_)).sequence.void

 def runQuery(endpoint: String, q: QueryWrapper): IO[Unit] =
  Endpoint(endpoint).runQuery(q) 
    
 
}
package es.weso
import com.monovore.decline._
import com.monovore.decline.effect._
import cats.effect._
import cats.implicits._
import es.weso.shex._

import java.nio.file.Path

case class ProcessCommand(
    shexPath: Path,
    outputFolder: Path
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
    )
 val outputPath = Opts.option[Path](
      "output", 
      metavar = "folder",
      help="Output folder to generate SPARQL queries"
    )

 val processCommand: Opts[ProcessCommand] = (shexPath, outputPath).mapN(ProcessCommand.apply)

 def main: Opts[IO[ExitCode]] =
   processCommand.map(run)

 def run(pc: ProcessCommand): IO[ExitCode] = 
    getSchema(pc.shexPath).flatMap(schema => 
        createSPARQLQueries(schema).flatMap(queries => 
            writeOutput(pc.outputFolder, queries).map(_ => ExitCode.Success)))
 
  def getSchema(path: Path): IO[Schema] = Schema.fromFile(path.toString(), "ShExC", None, None)

  def createSPARQLQueries(schema: Schema): IO[List[Query]] = IO(schema.shapeList.map(cnvShape(_)))

  def cnvShape(se: ShapeExpr): Query = ShapeExprParser.parse(se) 

  def writeOutput(folder: Path, qs: List[Query]): IO[Unit] = IO.println(s"---Writing $qs to $folder ")


}
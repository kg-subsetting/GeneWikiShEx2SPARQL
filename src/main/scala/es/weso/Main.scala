package es.weso
import com.monovore.decline._
import com.monovore.decline.effect._
import cats.effect._
import cats.implicits._
import es.weso.shex._
import io.circe._
import fs2._
import fs2.io.file.{Files, Path => Fs2Path}
import java.nio.file.{Path, Paths}
import javax.management.Query
import org.http4s._
import org.http4s.ember.client._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.circe._
import org.http4s.headers._
import es.weso.utils.named._
import es.weso.utils.decline._

case class ProcessCommand(
    shexPath: Path,
    outputFolder: Path,
    endpoint: String,
    graph: Option[String], 
    outputResults: Option[Path],
    outputFormat: OutputFormat,
    runQueries: Boolean,
    storeQueries: Boolean,
    addLabels: Boolean
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
      help="Output folder"
    ).withDefault(Paths.get("."))

 val endpoint = Opts.option[String](
      "endpoint", 
      metavar = "IRI",
      help="Endpoint. Default = Wikidata"
    ).withDefault("https://query.wikidata.org/sparql")

 val graph = Opts.option[String](
   "graph",
   metavar = "IRI",
   help = "graph name from endpoint. If not specified it takes default graph"
 ).orNone   

 val outputFormat = validatedList("outputFormat", OutputFormat.availableOutputFormats, Some(OutputFormat.JSON))

 val runQueries = Opts.flag("runQueries", help = "Run queries", short="r").orFalse

 val addLabels = Opts.flag("addLabels", help = "Run queries", short="r").orTrue

 val outputResults = Opts.option[Path](
  "outputResults", 
  metavar = "FilePath",
  help = "FilePath to store results of queries"
 ).orNone

 val storeQueries = Opts.flag("storeQueries", help = "Store queries", short="s").orFalse

 val showQueries = Opts.flag(long = "showQueries", help="Show queries").orTrue

 val processCommand: Opts[ProcessCommand] = 
  (shexPath, outputPath, endpoint, graph, outputResults, outputFormat, runQueries, storeQueries, addLabels
  ).mapN(ProcessCommand.apply)

 def main: Opts[IO[ExitCode]] =
    processCommand.map(run)
   
 def run(pc: ProcessCommand): IO[ExitCode] = 
   EmberClientBuilder.default[IO].build.use { client =>
    getSchema(pc.shexPath).flatMap(schema => 
        createSPARQLQueries(schema, pc.graph).flatMap(queriesNoLabels => 
          condM(pc.addLabels, addLabels(queriesNoLabels, client),IO.pure(queriesNoLabels)).flatMap(queries =>
          cond_(pc.runQueries, runQueries(pc.endpoint, queries, pc.outputResults, pc.outputFormat)).flatMap(_ => 
          cond_(pc.storeQueries, writeOutputQueries(pc.outputFolder, queries))).map(_ => 
            ExitCode.Success))))
   }

 def condM[A](b: Boolean, actionTrue: IO[A], actionFalse: IO[A]): IO[A] =
  if (b) actionTrue 
  else actionFalse     

 def cond_(b: Boolean, action: IO[Unit]): IO[Unit] =
  if (b) action 
  else IO(())

 def addLabels(qs: List[QueryWrapper], client: Client[IO]): IO[List[QueryWrapper]] = 
  qs.map(addLabelsQuery(_, client)).sequence

 def addLabelsQuery(q: QueryWrapper, client: Client[IO]): IO[QueryWrapper] =
  getLabelFromWikidata(removeQualifier(q.itemType.qid), client)
  .map(q.withLabel(_)).flatMap(newQuery => 
  newQuery.properties.toList.map { 
    case (v,p) => addLabelsProperty(p, client).map(p1 => (v,p1)) 
  }.sequence.map(ps => 
    newQuery.copy(properties = ps.toMap)
  ))

 def addLabelsProperty(p: Property, client: Client[IO]): IO[Property] = {
  getLabelFromWikidata(p.property, client).map(p.withLabel(_)).flatMap(newP => 
    getLabelFromWikidata(removeQualifier(newP.itemType.qid), client).map(itemTypeLabel => 
      newP.addItemTypeLabel(itemTypeLabel)
  ))
 }

 def removeQualifier(qid: String): String = {
  val regex = "(.*):(.*)".r 
  qid match {
    case regex(alias, localName) => localName
    case _ => qid
  }
 }

 def getLabelFromWikidata(itemId: String, client: Client[IO]): IO[String] =
  val queryStr = 
    s"https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&ids=${itemId}&languages=en&formatversion=2"
  Uri.fromString(queryStr).fold(
    err => IO.println(s"Error converting $queryStr to Uri: $err") *> IO.pure(""),
    uri => client.get(uri) {
    case Status.Successful(r) => r.attemptAs[Json].value.flatMap(_.fold(
      err => IO.println(s"Error obtaining JSON body: $err") *> IO.pure(""), 
      json => { 
       json.hcursor.downField("entities").downField(itemId).downField("labels").downField("en").downField("value").as[String].fold(
        err => IO.println(s"Error parsing label: $err\nJSON: ${json.spaces2}") *> IO.pure(""),
        label => IO.pure(label)
       )
      }
    ))
    case r => r.as[String]
    .map(b => IO.println(s"Request $queryStr failed with status ${r.status.code} and body $b")) *> IO.pure("")
  })

 
 def getSchema(path: Path): IO[Schema] = Schema.fromFile(path.toString(), "ShExC", None, None)

 def createSPARQLQueries(schema: Schema, maybeGraph: Option[String]): IO[List[QueryWrapper]] = 
    IO(schema.shapeList.map(ShapeExprParser(schema, maybeGraph).parse(_)))

 def sep = IO.println("----------------")  

 def writeOutputQueries(folder: Path, qs: List[QueryWrapper]): IO[Unit] = 
    qs.
    map(writeQuery(folder, _)).
    sequence.
    void

 def writeQuery(folder: Path, q: QueryWrapper): IO[Unit] = {
    val targetFile = Fs2Path(folder.toString + "/" + q.name + ".sparql")
    IO.println(s"Generating output to file: $targetFile") *> 
    Stream(q.serialize)
     .through(text.utf8.encode)
     .through(Files[IO].writeAll(targetFile))
     .compile
     .drain
 }
 
 def runQueries(endpoint: String, qs: List[QueryWrapper], outputResults: Option[Path], outputFormat: OutputFormat): IO[Unit] =
  qs.map(runQuery(endpoint,_, outputFormat)).sequence.flatMap(ls => {
    val str = 
      outputFormat.start + 
      ls.mkString(outputFormat.sep) + 
      outputFormat.end
    outputResults match {
    case None => IO.println(str)
    case Some(path) => writeContents(path, str)
  }})

 def runQuery(endpoint: String, q: QueryWrapper, outputFormat: OutputFormat): IO[String] =
  Endpoint(endpoint).runQuery(q).map(_.serialize(outputFormat)) 

 def writeContents(path: Path, contents: String): IO[Unit] = {
    Stream
      .emits(contents)
      .covary[IO]
      .chunkN(4096)
      .map(_.toVector.mkString)
      .through(text.utf8Encode)
      .through(Files[IO].writeAll(path))
      .compile
      .drain  
 }

}
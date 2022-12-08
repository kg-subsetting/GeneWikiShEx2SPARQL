package es.weso
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import io.circe._

sealed abstract class Result {
  import Result._  

  def toJson: Json = this match {
          case qe: QueryError => 
            Json.fromFields(List(
                    ("type", Json.fromString("Error")),
                    ("name", Json.fromString(qe.query.name)),
                    ("label", Json.fromString(qe.query.label)),
                    ("result",Json.fromString(qe.err.getMessage()))
                ))  
           case ns: NoSolution => 
            Json.fromFields(List(
                ("type", Json.fromString("NoSolution")),
                ("name", Json.fromString(ns.query.name)),
                ("label", Json.fromString(ns.query.label)),
                ("time", Json.fromString(showTime(ns.duration)))
            ))
           case ss: SeveralSolutions => 
            Json.fromFields(List(
                ("type", Json.fromString("SeveralSolutions")),
                ("name", Json.fromString(ss.query.name)),
                ("label", Json.fromString(ss.query.label)),
                ("time", Json.fromString(showTime(ss.duration)))
            ))
           case s: Solution => 
            Json.fromFields(List(
                ("type", Json.fromString("Solution")),
                ("name", Json.fromString(s.query.name)),
                ("value", Json.fromString(s.value)),
                ("label", Json.fromString(s.query.label)),
                ("time", Json.fromString(showTime(s.duration))),
                ("links", Json.fromValues(s.links.map(_.toJson)))
            ))
          }

  def toCSV: String = this match {
    case qe: QueryError => 
      s"${qe.query.name},Error ${qe.err.getMessage()},,"
    case ns: NoSolution => 
      s"${ns.query.name},0,,,"
    case ss: SeveralSolutions => 
      s"${ss.query.name},Several solutions,,,"
    case s: Solution => 
      s"${s.query.name},${s.value},,,\n" ++
      s.links.map(_.toCSV).mkString("\n")
  }
        

  def serialize(of: OutputFormat): String = of match {
    case OutputFormat.CSV =>
      this.toCSV

    case OutputFormat.JSON =>  
        this.toJson.spaces2      
        
    case OutputFormat.PlantUML => this match {
            case qe: QueryError => 
                s"""|annotation ${qe.query.name} { 
                    | Error: \"${qe.err.getMessage()}\" 
                    |}""".stripMargin

            case ns: NoSolution => 
                s"""|annotation ${ns.query.name} { 
                    | Total: 0 
                    | time: ${showTime(ns.duration)}
                    |}""".stripMargin

            case ss: SeveralSolutions => 
                s"""|annotation ${ss.query.name} { 
                    | \"${ss.rs.length} solutions\"
                    |}""".stripMargin

            case s: Solution => 
                s"""|annotation ${s.query.name} { 
                    | Total: ${s.value} 
                    | time: ${showTime(s.duration)}
                    |}
                    |
                    |${links2PlantUML(s.query.name, s.links)}
                    |""".stripMargin
        }
    }

  def links2PlantUML(name: String, links: List[LinkInfo]): String = 
    links.map(link2PlantUML(name,_)).mkString("\n")

  def link2PlantUML(name: String, link: LinkInfo): String =
    s"$name \"${link.value}\" --> ${link.property.itemType.name} : ${link.property.label}"
}

object Result {


 case class QueryError(query: QueryWrapper, err: Throwable) extends Result 
 case class NoSolution(query: QueryWrapper, duration: FiniteDuration) extends Result 
 case class Solution(query: QueryWrapper, value: String, links: List[LinkInfo], duration: FiniteDuration) extends Result {

  def withValue(v: String): Solution = this.copy(value = v)

  def addLink(link: LinkInfo): Solution =
    this.copy(links = link +: this.links)

 }

 case class SeveralSolutions(query: QueryWrapper, rs: List[Solution], duration: FiniteDuration) extends Result

 case class LinkInfo(property: Property, value: String) {
   def toJson: Json = Json.fromFields(List(
     ("property", Json.fromString(property.property)),
     ("label", Json.fromString(property.label)),
     ("value", Json.fromString(value)),
     ("itemType", Json.fromString(property.itemType.name)),
     ("itelLabel", Json.fromString(property.itemType.label)),
     ("itemId", Json.fromString(property.itemType.qid))
    ))

   def toCSV: String =
    s",,${property.label}(${property.property}),${property.itemType.name}(${property.itemType.qid}),${value}" 
 }

 private def showTime(duration: FiniteDuration): String =
      s"${duration.toUnit(TimeUnit.SECONDS)}"

  def emptySolution(query: QueryWrapper, duration: FiniteDuration) = Solution(query, "", List(), duration)
}

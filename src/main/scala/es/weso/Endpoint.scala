package es.weso
import org.apache.jena.query._
import es.weso.rdf.nodes.{RDFNode =>_, _}
import cats.effect._
import scala.jdk.CollectionConverters._
import org.apache.jena.rdf.model.RDFNode 
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import Result._

case class Endpoint(iri: String) {

  def runQuery(qw: QueryWrapper): IO[Result] =
    IO.monotonic.flatMap(start => 
    IO(QueryExecutionFactory.sparqlService(iri, qw.query).execSelect()).flatMap(resultSet => 
    IO.monotonic.map(end => resultSet2Result(resultSet, qw, end - start))))
    .handleErrorWith(err => IO.pure(QueryError(qw, err)))

  def resultSet2Result(rs:ResultSet, query: QueryWrapper, duration: FiniteDuration): Result = {
    val vars = rs.getResultVars().asScala.toList
    val solutions = rs.asScala.toList
    solutions match {
        case Nil => mkNoSolution(query, vars, duration) 
        case solution :: Nil => 
            cnvSolution(solution, vars, query, duration)
        case sols => SeveralSolutions(query, sols.map(cnvSolution(_, vars, query, duration)), duration)
    }
  }

  def mkNoSolution(query: QueryWrapper, vars: List[String], duration: FiniteDuration): NoSolution = 
    vars.foldLeft(Result.emptyNoSolution(query, duration)) { case (current, v) => {
      val maybeProperty = query.properties.get(VarName(v))
      maybeProperty match {
          case None => current
          case Some(p) => current.addLink(LinkInfo(p, "0"))  
        } 
    }}

  def cnvSolution(
    solution:QuerySolution, 
    vars: List[String], 
    query: QueryWrapper, 
    duration: FiniteDuration
    ): Solution = 
    vars.foldLeft(Result.emptySolution(query, duration)) { case(current, v) => {
      val maybeProperty = query.properties.get(VarName(v))
      val valueStr = showNode(solution.get(v))
      maybeProperty match {
          case None => current.withValue(valueStr)
          case Some(p) => current.addLink(LinkInfo(p, valueStr))  
        } 
     }
    }

  def showNode(node: RDFNode): String = 
    try { 
        node.asLiteral().getInt().toString 
    } catch {
        case e: Exception => node.toString
    }

 

}
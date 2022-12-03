package es.weso
import org.apache.jena.query._
import es.weso.rdf.nodes.{RDFNode =>_, _}
import cats.effect._
import scala.jdk.CollectionConverters._
import org.apache.jena.rdf.model.RDFNode 

case class Endpoint(iri: String) {

  def runQuery(qw: QueryWrapper): IO[Unit] =
    IO(QueryExecutionFactory.sparqlService(iri, qw.query).execSelect())
    .flatMap(showResultSet(_, qw.name)).handleErrorWith(err => 
        IO.println(s"Error running query: ${qw.name}: ${err.getMessage()}"))

  def showResultSet(rs:ResultSet, name: String): IO[Unit] = {
    val vars = rs.getResultVars().asScala.toList
    val solutions = rs.asScala.toList
    solutions match {
        case Nil => IO.println(s"$name: <No solution>")
        case solution :: Nil => 
            IO.println(s"$name: ${vars.map(v => 
                s"$v => ${showNode(solution.get(v))}").mkString(",")}")
        case _ => IO.println(s"More than one solution?")
    }
  }  

  def showNode(node: RDFNode): String = 
    try { 
        node.asLiteral().getInt().toString 
    } catch {
        case e: Exception => node.toString
    }

 

}
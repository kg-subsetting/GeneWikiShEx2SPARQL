package es.weso
import es.weso.shex._
import es.weso.rdf.nodes._

object ShapeExprParser {

  def parse(se: ShapeExpr): Query = Query(getName(se), ItemType("Q50379781"), List())

  def getName(se: ShapeExpr): String = 
    se.id.map(cnvLabel(_)).getOrElse("")

  def cnvLabel(lbl: ShapeLabel): String = lbl match {
    case i: IRILabel => i.iri.relativizeIRI(IRI("http://example.org/")).toString
    case _ => ""
  }

}
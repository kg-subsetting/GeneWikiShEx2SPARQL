package es.weso
import es.weso.shex.{VarName => _, _}
import es.weso.rdf.nodes._
import es.weso.rdf._

case class ExpectedTripleConstraintParsingTripleExpression(te: TripleExpr, se: ShapeExpr) 
 extends RuntimeException(s"Expected tripleConstraint parsing TripleExpression(te = $te, se = $se)")

case class NotFoundTypeConstraint(se: ShapeExpr) 
 extends RuntimeException(s"Not found typeConstraint in shapeExpression: $se")
 
case class NoShapeGetTripleConstraints(se: ShapeExpr) 
 extends RuntimeException(s"No shape in getTripleConstraints($se)")
case class NoValueExprGetItemType(se: ShapeExpr)
 extends RuntimeException(s"GetItemType with no value Expr. se=$se")
case class GetItemTypeFromShapeExpr(se: ShapeExpr) 
  extends RuntimeException(s"GetItemTypeFromShapeExpr must be NodeConstraint. Obtained: $se")
case class NoValuesGetItemTypeFromNodeConstraint(nc: NodeConstraint) 
 extends RuntimeException(s"getItemTypeFromNodeConstraint: ValueSet is not correct: $nc")  
case class GetItemTypeFromValueSetValue_ExpectedIRIValue(v: ValueSetValue)
 extends RuntimeException(s"getItemTypeFromValueSetValue...expected IRIValue, obtained: $v") 

case class ParsePropertyDecl_NoValueExpr(tc: TripleConstraint)
 extends RuntimeException(s"ParsePropertyDecl: No valueExpr for $tc") 

case class ParsePropertyDecl_NoShapeRef(tc: TripleConstraint)
 extends RuntimeException(s"ParsePropertyDecl: No shapeRef for $tc")

case class ParsePropertyDecl_ShapeNotFound(tc: TripleConstraint, sl: ShapeLabel) 
 extends RuntimeException(s"ParsePropertyDecl: ShapeNotFound($sl) for tripleConstraint: $tc")

case class GetPropertyName_DontMatch(p: IRI) 
 extends RuntimeException(s"GetPropertyName: no match with wdt: $p")

case class ShapeExprParser(schema: Schema, maybeGraph: Option[String]) {

  def parse(se: ShapeExpr): QueryWrapper = 
    QueryWrapper(
      getName(se), 
      "",
      getTypeConstraint(se), 
      getPropertyConstraints(se),
      maybeGraph
    )

  def getName(se: ShapeExpr): String = 
    se.id.map(cnvLabel(_)).getOrElse("")

  def getTripleConstraints(se: ShapeExpr): List[TripleConstraint] = se match {
    case sd: ShapeDecl => getTripleConstraints(sd.shapeExpr)
    case s: Shape => s.expression match {
      case Some(te) => getTripleConstraintsTE(te, se)
      case None => List()
    }
    case _ => throw NoShapeGetTripleConstraints(se)
  }  

  def getTripleConstraintsTE(te: TripleExpr, se: ShapeExpr): List[TripleConstraint] = te match {
    case eo: EachOf => eo.expressions.map(getTripleConstraint(_, se))
    case _ => List()
  }

  def getTripleConstraint(te: TripleExpr, se: ShapeExpr): TripleConstraint = te match {
    case tc: TripleConstraint => tc
    case _ => throw ExpectedTripleConstraintParsingTripleExpression(te, se)
  }

  def getTypeConstraint(se: ShapeExpr): ItemType = getTripleConstraints(se).collectFirst { 
    case tc if (tc.predicate == wdt_p31) => getItemTypeFromValueExpr(tc.valueExpr, se)
  } match {
    case Some(it) => it
    case None => throw NotFoundTypeConstraint(se)
  }

  def getItemTypeFromValueExpr(maybeSe: Option[ShapeExpr], se: ShapeExpr): ItemType = maybeSe match {
    case Some(vse) => getItemTypeFromShapeExpr(vse, se)
    case None => throw NoValueExprGetItemType(se)
  }

  def getItemTypeFromShapeExpr(vse: ShapeExpr, se: ShapeExpr): ItemType = vse match {
    case nc: NodeConstraint => getItemTypeFromNodeConstraint(nc, se)
    case _ => throw GetItemTypeFromShapeExpr(se)
  }

  def getItemTypeFromNodeConstraint(nc: NodeConstraint, se: ShapeExpr): ItemType = nc.values match {
    case Some(v :: Nil) => getItemTypeFromValueSetValue(v, se)
    case _ => throw NoValuesGetItemTypeFromNodeConstraint(nc)
  }

  def getItemTypeFromValueSetValue(v: ValueSetValue, se: ShapeExpr): ItemType = v match {
    case IRIValue(v) => ItemType(prefixMap.qualifyIRI(v), getName(se), "")
    case _ => throw GetItemTypeFromValueSetValue_ExpectedIRIValue(v)
  }

  lazy val wd = IRI("http://www.wikidata.org/entity/")
  lazy val wdt = IRI("http://www.wikidata.org/prop/direct/")
  lazy val rdfs = IRI("http://www.w3.org/2000/01/rdf-schema#")
  lazy val wdt_p31: IRI = wdt + "P31"
  lazy val rdfs_label = rdfs + "label"

  def getPropertyConstraints(se: ShapeExpr): Map[VarName,Property] = 
    getTripleConstraints(se).map(parsePropertyDecl).flatten.map(p => (p.toVarName, p)).toMap

  def parsePropertyDecl(tc: TripleConstraint): Option[Property] = tc.predicate match {
    case `wdt_p31` => None
    case `rdfs_label` => None
    case p => tc.valueExpr match {
      case Some(se) => se match {
        case sr: ShapeRef => schema.getShape(sr.reference).fold(
          err => throw ParsePropertyDecl_ShapeNotFound(tc, sr.reference),
          se => Some(Property(getPropertyName(p), 
                              ItemType(getTypeConstraint(se).qid, getName(se),"")))
        ) 
        case _ => throw ParsePropertyDecl_NoShapeRef(tc)
      }
      case None => throw ParsePropertyDecl_NoValueExpr(tc)
    }
  }

  def getPropertyName(p: IRI): String = {
    val wdtRegex = "wdt:(.*)".r
    prefixMap.qualifyIRI(p) match {
      case wdtRegex(name) => name
      case _ => throw GetPropertyName_DontMatch(p)
    }
  }


  lazy val prefixMap = PrefixMap.empty
    .addPrefix("", IRI("http://example.org/"))
    .addPrefix("wd", wd)
    .addPrefix("wdt", wdt)

  def cnvLabel(lbl: ShapeLabel): String = lbl match {
    case i: IRILabel => cnvIRI(i.iri) 
    case _ => ""
  }

  def cnvIRI(iri: IRI): String = 
    removeLTGT(iri.relativizeIRI(IRI("http://example.org/")).toString)

  def removeLTGT(str: String): String = {
    val regex = "<(.*)>".r
    str match {
    case regex(v) => v
    case _ => str
  }}

}
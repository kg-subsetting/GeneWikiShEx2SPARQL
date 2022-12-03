package es.weso
import org.apache.jena.query._
import es.weso.rdf.nodes._

case class QueryWrapper(
   name: String, 
   itemType: ItemType, 
   properties: List[Property]
   ) {

    lazy val prefixes = """|prefix wd:       <http://www.wikidata.org/entity/> 
                      |prefix wdt:      <http://www.wikidata.org/prop/direct/> 
                      |""".stripMargin

    lazy val heading = s"(count(?$name) as ?count_$name) $propertiesVars"

    lazy val propertiesVars = properties.map(_.toVarName).mkString(" ")

    lazy val typePart = s"?$name wdt:P31 ${itemType.qid} ."

    lazy val propertiesPart = properties.map(p => 
        s"""|{ SELECT (count(?y) as ${p.toVarName}) {
            |   ?x wdt:P31 ${itemType.qid} .
            |   ?x wdt:${p.property} ?y .
            |   ?y wdt:P31 ${p.itemType.qid} .
            | }}
            |""".stripMargin
        ).mkString("\n")

    lazy val groupByPart = 
        if (propertiesVars.isEmpty) ""
        else s"GROUP BY $propertiesVars"

    lazy val queryString = 
        s"""|$prefixes
            |
            |SELECT $heading WHERE {
            | $typePart
            | 
            | $propertiesPart
            |
            |} $groupByPart
            |""".stripMargin
    
    lazy val query: Query = {
      /* println("-----------")  
      println(s"Query: \n$queryString")  
      println("--end query---------")   */
      QueryFactory.create(queryString)
    }

    def serialize: String = query.toString()

}
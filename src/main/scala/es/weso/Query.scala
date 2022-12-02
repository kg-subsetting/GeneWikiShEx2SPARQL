package es.weso

case class Query(
   name: String, 
   itemType: ItemType, 
   properties: List[Property]
   ) {


    def serialize: String = s"Query $name"
}
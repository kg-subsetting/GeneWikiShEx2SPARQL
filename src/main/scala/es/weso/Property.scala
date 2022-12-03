package es.weso

case class Property(property: String, itemType: ItemType) {
    lazy val toVarName = s"?count_${property}_${itemType.name}"
}
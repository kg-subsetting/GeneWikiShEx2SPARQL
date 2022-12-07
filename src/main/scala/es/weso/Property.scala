package es.weso

case class Property(
    property: String, 
    itemType: ItemType,
    label: String = ""
    ) {
def withLabel(label: String) = 
    this.copy(label = label)

def addItemTypeLabel(label: String) = 
    this.copy(itemType = this.itemType.withLabel(label))

lazy val toVarName: VarName = 
    VarName(s"count_${property}_${itemType.name}")

}
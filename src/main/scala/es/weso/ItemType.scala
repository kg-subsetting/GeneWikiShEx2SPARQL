package es.weso

case class ItemType(qid: String, name: String, label: String) {
    def withLabel(label: String) = this.copy(label = label)
}
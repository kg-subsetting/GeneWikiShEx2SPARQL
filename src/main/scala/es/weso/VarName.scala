package es.weso

case class VarName(name: String) extends AnyVal {
    override def toString: String = s"?$name"
}

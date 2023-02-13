import Shape

class Container {
    var shape: Shape? = null

    fun totalArea(): Double = shape?.area() ?: 0.0
}
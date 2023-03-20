import Shape

class Circle(val radius: Double) : Shape() {
    override fun area() = Math.PI * radius * radius
}
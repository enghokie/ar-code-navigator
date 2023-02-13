import Shape

class Rectangle(val width: Double, val height: Double) : Shape() {
    override fun area() = width * height
}

class Square(sideLength: Double) : Rectangle(sideLength, sideLength)
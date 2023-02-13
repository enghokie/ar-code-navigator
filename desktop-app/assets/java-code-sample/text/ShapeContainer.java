import Shape

class ShapeContainer {
    private List<Shape> shapes = new ArrayList<>();

    void addShape(Shape shape) {
        shapes.add(shape);
    }

    double getTotalArea() {
        double total = 0;
        for (Shape shape : shapes) {
            total += shape.getArea();
        }
        return total;
    }

    double getTotalPerimeter() {
        double total = 0;
        for (Shape shape : shapes) {
            total += shape.getPerimeter();
        }
        return total;
    }
}
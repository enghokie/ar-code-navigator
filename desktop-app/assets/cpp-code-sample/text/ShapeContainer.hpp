#include "Shape.hpp"

class Container {
private:
  Shape* shapes[100];
  int count;
public:
  Container() : count(0) {}
  void Add(Shape* shape) {
    shapes[count++] = shape;
  }
  float TotalArea() {
    float area = 0;
    for (int i = 0; i < count; i++)
      area += shapes[i]->Area();
    return area;
  }
};
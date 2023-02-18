#include "Shape.hpp"

class Circle : public Shape {
private:
  float radius;
public:
  Circle(float r) : radius(r) {}
  float Area() { return 3.14 * radius * radius; }
};
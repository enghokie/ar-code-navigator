#include "Shape.hpp"

class Triangle : public Shape {
private:
  float base, height;
public:
  Triangle(float b, float h) : base(b), height(h) {}
  float Area() { return 0.5 * base * height; }
};
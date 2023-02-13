#include "Shape.hpp"

class Rectangle : public Shape {
private:
  float length, width;
public:
  Rectangle(float l, float w) : length(l), width(w) {}
  float Area() { return length * width; }
};
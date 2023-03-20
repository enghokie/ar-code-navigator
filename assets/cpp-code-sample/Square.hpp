#include "Rectangle.hpp"

class Square : public Rectangle {
public:
  Square(float side) : Rectangle(side, side) {}
};
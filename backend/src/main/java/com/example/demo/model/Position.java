package com.example.demo.model;

public class Position {
    public double x;
    public double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // Método de "Súper Utilidad": calcula la distancia a otra posición.
    // Lo usaremos para colisiones y para que la IA del zombie te persiga.
    public double distanceTo(Position other) {
        return Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));
    }

    // Getters y Setters
    public double getX() { return x; }

    // Acceso rápido (para compatibilidad con código existente)
    public void addToX(double dx) { this.x += dx; }

    public void addToY(double dy) { this.y += dy; }

    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    // Para depurar más fácil por consola
    @Override
    public String toString() {
        return "Pos[x=" + (int)x + ", y=" + (int)y + "]";
    }
}
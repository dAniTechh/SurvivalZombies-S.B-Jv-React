package com.example.demo.model;

public class CajaMagica {
    private String id;
    private double x;
    private double y;
    private int coste = 950;
    private boolean activa = false;
    private boolean animando = false;

    public CajaMagica() {}

    public CajaMagica(String id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public int getCoste() { return coste; }
    public void setCoste(int coste) { this.coste = coste; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public boolean isAnimando() { return animando; }
    public void setAnimando(boolean animando) { this.animando = animando; }
}
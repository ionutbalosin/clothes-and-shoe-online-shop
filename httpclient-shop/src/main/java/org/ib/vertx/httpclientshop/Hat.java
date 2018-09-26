package org.ib.vertx.hatserviceprovider;

public class Hat {
    private String name, price;

    @Override
    public String toString() {
        return "Hat{" +
                ", name='" + name + '\'' +
                ", price='" + price + '\'' +
                '}';
    }

    public Hat(String name, String price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}

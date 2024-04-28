package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.messaging.request.EnterOrderRq;

public class OrderFactory {

    private static OrderFactory instance;

    private OrderFactory() {}

    public static OrderFactory getInstance() {
        if (instance == null) {
            instance = new OrderFactory();
        }
        return instance;
    }
}

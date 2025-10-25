package io.canvasmc.canvas;

public enum NetworkIoModel {

    IO_URING("io_uring"),
    KQUEUE("kqueue"),
    EPOLL("epoll"),
    NIO("nio");

    private final String name;

    NetworkIoModel(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static NetworkIoModel fromProperty() {
        return Config.INSTANCE.networking.networkIoModel;
    }
}

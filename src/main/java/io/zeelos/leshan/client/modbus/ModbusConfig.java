package io.zeelos.leshan.client.modbus;

import java.util.List;
import java.util.Map;

class ModbusConfig {

    String connection;
    int slave;

    TcpSettings tcpSettings;
    SerialSettings serialSettings;
    AsciiSettings asciiSettings;

    Map<String, List<Map<String, Resource>>> objects;

    static class TcpSettings {
        String node;
        int port;
        boolean keepalive;
    }

    static class SerialSettings {
        String deviceName;
        int baudRate;
        int dataBits;
        int stopBits;
        int parity;
    }

    static class AsciiSettings {
        String deviceName;
        int baudRate;
        int parity;
    }

    static class Resource {
        String type;
        int startAddress;
        int quantity;
        long pollMillis;
    }
}

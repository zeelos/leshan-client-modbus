/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *     Sierra Wireless, - initial API and implementation
 *     Bosch Software Innovations GmbH, - initial API and implementation
 *******************************************************************************/

package io.zeelos.leshan.client.modbus;

import com.google.gson.Gson;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.serial.SerialParameters;
import com.intelligt.modbus.jlibmodbus.serial.SerialPort;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import io.zeelos.leshan.client.modbus.utils.Utils;
import org.apache.commons.cli.*;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.leshan.LwM2m;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.util.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.eclipse.leshan.LwM2mId.SECURITY;
import static org.eclipse.leshan.LwM2mId.SERVER;
import static org.eclipse.leshan.client.object.Security.*;

/**
 * Leshan ModBus Protocol Adapter demo
 *
 * @author zeelos.io - https://zeelos.io
 */
public class LeshanModbusClient {

    private static final Logger log = LoggerFactory.getLogger(LeshanModbusClient.class);

    private final static String[] modelPaths = new String[]{"3303.xml", "26241.xml"};

    private final static String DEFAULT_ENDPOINT = "LeshanModbusClient";
    private final static String USAGE = "java -jar leshan-client-modbus.jar [OPTION]";

    private static final String TCP = "tcp";
    private static final String RTU = "rtu";
    private static final String ASCII = "ascii";

    private final static String DEFAULT_DEMO_CONFIG_FILENAME = "modbus.json";

    public static void main(final String[] args) {

        // Define options for command line tools
        Options options = new Options();

        options.addOption("h", "help", false, "Display help information.");
        options.addOption("n", true, String.format(
                "Set the endpoint name of the Client.\nDefault: the local hostname or '%s' if any.", DEFAULT_ENDPOINT));
        options.addOption("b", false, "If present use bootstrap.");
        options.addOption("lh", true, "Set the local CoAP address of the Client.\n  Default: any local address.");
        options.addOption("lp", true,
                "Set the local CoAP port of the Client.\n  Default: A valid port value is between 0 and 65535.");
        options.addOption("slh", true, "Set the secure local CoAP address of the Client.\nDefault: any local address.");
        options.addOption("slp", true,
                "Set the secure local CoAP port of the Client.\nDefault: A valid port value is between 0 and 65535.");
        options.addOption("u", true, String.format("Set the LWM2M or Bootstrap server URL.\nDefault: localhost:%d.",
                LwM2m.DEFAULT_COAP_PORT));
        options.addOption("i", true,
                "Set the LWM2M or Bootstrap server PSK identity in ascii.\nUse none secure mode if not set.");
        options.addOption("p", true,
                "Set the LWM2M or Bootstrap server Pre-Shared-Key in hexa.\nUse none secure mode if not set.");
        options.addOption("m", "modelsfolder", true, "A folder which contains object models in OMA DDF(.xml) format.");
        options.addOption("t", "modbus objects", true, "The modbus json configuration file describing the modbus bindings");

        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);

        // Parse arguments
        CommandLine cl;
        try {
            cl = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("Parsing failed.  Reason: " + e.getMessage());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Print help
        if (cl.hasOption("help")) {
            formatter.printHelp(USAGE, options);
            return;
        }

        // Abort if unexpected options
        if (cl.getArgs().length > 0) {
            System.err.println("Unexpected option or arguments : " + cl.getArgList());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Abort if we have not identity and key for psk.
        if ((cl.hasOption("i") && !cl.hasOption("p")) || !cl.hasOption("i") && cl.hasOption("p")) {
            System.err.println("You should precise identity and Pre-Shared-Key if you want to connect in PSK");
            formatter.printHelp(USAGE, options);
            return;
        }

        // Get endpoint name
        String endpoint;
        if (cl.hasOption("n")) {
            endpoint = cl.getOptionValue("n");
        } else {
            /*
            try {
                endpoint = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                endpoint = DEFAULT_ENDPOINT;
            }
            */
            // set UUID as the endpoint name to allow multiple instances to coexist
            // if the modbus is running in the same machine.
            endpoint = UUID.randomUUID().toString().replaceAll("-", "");
        }

        // Get server URI
        String serverURI;
        if (cl.hasOption("u")) {
            if (cl.hasOption("i"))
                serverURI = "coaps://" + cl.getOptionValue("u");
            else
                serverURI = "coap://" + cl.getOptionValue("u");
        } else {
            if (cl.hasOption("i"))
                serverURI = "coaps://localhost:" + LwM2m.DEFAULT_COAP_SECURE_PORT;
            else
                serverURI = "coap://localhost:" + LwM2m.DEFAULT_COAP_PORT;
        }

        // get security info
        byte[] pskIdentity = null;
        byte[] pskKey = null;
        if (cl.hasOption("i") && cl.hasOption("p")) {
            pskIdentity = cl.getOptionValue("i").getBytes();
            pskKey = Hex.decodeHex(cl.getOptionValue("p").toCharArray());
        }

        // get local address
        String localAddress = null;
        int localPort = 0;
        if (cl.hasOption("lh")) {
            localAddress = cl.getOptionValue("lh");
        }
        if (cl.hasOption("lp")) {
            localPort = Integer.parseInt(cl.getOptionValue("lp"));
        }

        // get secure local address
        String secureLocalAddress = null;
        int secureLocalPort = 0;
        if (cl.hasOption("slh")) {
            secureLocalAddress = cl.getOptionValue("slh");
        }
        if (cl.hasOption("slp")) {
            secureLocalPort = Integer.parseInt(cl.getOptionValue("slp"));
        }

        // Get models folder
        String modelsFolderPath = cl.getOptionValue("m");
        // The modbus configuration
        String modbusConfigFilename = cl.getOptionValue("t");

        try {
            // load modbus config
            Gson gson = new Gson();

            Reader reader;
            if (modbusConfigFilename == null) {
                log.info("Loading default demo Modbus configuration.");
                reader = new InputStreamReader(ClassLoader.getSystemClassLoader().getResourceAsStream(DEFAULT_DEMO_CONFIG_FILENAME));
            } else {
                log.info("Loading Modbus configuration from '{}'", modbusConfigFilename);
                reader = new FileReader(modbusConfigFilename);
            }

            ModbusConfig modbusConfig = gson.
                    fromJson(reader, ModbusConfig.class);
            ModbusMaster master = createAndStartModbus(modbusConfig);

            createAndStartClient(endpoint, localAddress, localPort, secureLocalAddress, secureLocalPort, cl.hasOption("b"),
                    serverURI, pskIdentity, pskKey, modelsFolderPath, modbusConfig, master);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void createAndStartClient(String endpoint, String localAddress, int localPort,
                                             String secureLocalAddress, int secureLocalPort, boolean needBootstrap, String serverURI, byte[] pskIdentity,
                                             byte[] pskKey, String modelsFolderPath, ModbusConfig modbusConfig, ModbusMaster master) throws Exception {

        // Initialize model
        List<ObjectModel> models = ObjectLoader.loadDefault();
        if (modelsFolderPath != null) {
            models.addAll(ObjectLoader.loadObjectsFromDir(new File(modelsFolderPath)));
        } else {
            models.addAll(ObjectLoader.loadDdfResources("/models/", modelPaths));
        }

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(models));
        if (needBootstrap) {
            if (pskIdentity == null)
                initializer.setInstancesForObject(SECURITY, noSecBootstap(serverURI));
            else
                initializer.setInstancesForObject(SECURITY, pskBootstrap(serverURI, pskIdentity, pskKey));
        } else {
            if (pskIdentity == null) {
                initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123));
                initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
            } else {
                initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, pskIdentity, pskKey));
                initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
            }
        }

        List<Integer> supportedObjectIds = new ArrayList<>();

        supportedObjectIds.add(SECURITY);
        supportedObjectIds.add(SERVER);

        // try to setup modbus sensors from loaded config
        modbusConfig.objects.forEach((key, objects) -> {
            int objectId = Integer.parseInt(key);

            ModbusSensor[] modbusInstances = new ModbusSensor[objects.size()];

            for (int i = 0; i < objects.size(); i++) {
                modbusInstances[i] = new ModbusSensor(modbusConfig.slave, master, objects.get(i));
            }

            initializer.setInstancesForObject(objectId, modbusInstances);

            supportedObjectIds.add(objectId);
        });

        List<LwM2mObjectEnabler> enablers = initializer.create(Utils.toIntArray(supportedObjectIds));

        // Create client
        LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
        builder.setLocalAddress(localAddress, localPort);
        builder.setLocalSecureAddress(secureLocalAddress, secureLocalPort);
        builder.setObjects(enablers);
        builder.setCoapConfig(NetworkConfig.getStandard());
        final LeshanClient client = builder.build();

        // Start the client
        client.start();

        // De-register on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.destroy(true); // send de-registration request before destroy
        }));
    }

    private static ModbusMaster createAndStartModbus(ModbusConfig config) throws Exception {
        ModbusMaster master;

        // enable debug mode
        Modbus.setLogLevel(Modbus.LogLevel.LEVEL_DEBUG);

        switch (config.connection) {
            case TCP: {
                TcpParameters tp = new TcpParameters();

                // parse config
                String host = InetAddress.getByName(config.tcpSettings.node).getHostAddress();
                int port = config.tcpSettings.port;
                boolean keepAlive = config.tcpSettings.keepalive;

                tp.setHost(InetAddress.getByName(host));
                tp.setPort(port);
                tp.setKeepAlive(keepAlive);

                master = ModbusMasterFactory.createModbusMasterTCP(tp);
                log.info("Starting ModbusConfig Master TCP with settings: [host:'{}', port:{}, keepalive:{}]", host, port, keepAlive);

                break;
            }
            case RTU: {
                SerialParameters sp = new SerialParameters();

                // parse config
                String device_name = config.serialSettings.deviceName;
                SerialPort.BaudRate baud_rate = SerialPort.BaudRate.getBaudRate(config.serialSettings.baudRate);
                int data_bits = config.serialSettings.dataBits;
                int stop_bits = config.serialSettings.stopBits;
                SerialPort.Parity parity = SerialPort.Parity.getParity(config.serialSettings.parity);

                sp.setDevice(device_name);
                sp.setBaudRate(baud_rate);
                sp.setDataBits(data_bits);
                sp.setStopBits(stop_bits);
                sp.setParity(parity);

                master = ModbusMasterFactory.createModbusMasterRTU(sp);
                log.info("Starting ModbusMaster RTU with settings: [deviceName:'{}', baudRate:{}, dataBits:{}, stopBits:{}, parity:{}]",
                        device_name, baud_rate, data_bits, stop_bits, parity);

                break;
            }
            case ASCII: {
                SerialParameters sp = new SerialParameters();

                String device_name = config.asciiSettings.deviceName;
                SerialPort.BaudRate baud_rate = SerialPort.BaudRate.getBaudRate(config.asciiSettings.baudRate);
                SerialPort.Parity parity = SerialPort.Parity.getParity(config.asciiSettings.parity);

                sp.setDevice(device_name);
                sp.setBaudRate(baud_rate);
                sp.setParity(parity);

                master = ModbusMasterFactory.createModbusMasterASCII(sp);
                log.info("Starting ModbusMaster ASCII with settings: [deviceName:'{}', baudRate:{}, parity:{}]",
                        device_name, baud_rate, parity);

                break;
            }
            default:
                throw new IllegalStateException("no 'connection' information found in modbus configuration file!");
        }

        master.setResponseTimeout(1000);

        // try to connect
        master.connect();

        return master;
    }
}

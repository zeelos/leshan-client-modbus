package io.zeelos.leshan.client.modbus;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import io.zeelos.leshan.client.modbus.utils.Utils;
import org.eclipse.leshan.client.request.ServerIdentity;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ModbusSensor extends BaseInstanceEnabler {

    private static final Logger log = LoggerFactory.getLogger(ModbusSensor.class);

    public static final String HOLDING_REGISTER = "holding-register";
    public static final String INPUT_REGISTER = "input-register";
    public static final String COIL = "coil";
    public static final String DISCRETE_INPUT = "discrete-input";

    private Map<String, ModbusConfig.Resource> config;

    private int slave;
    private ModbusMaster master;

    public ModbusSensor() {
    }

    public ModbusSensor(int slave, ModbusMaster master, Map<String, ModbusConfig.Resource> config) {
        this.config = config;
        this.slave = slave;
        this.master = master;
    }

    @Override
    public ReadResponse read(ServerIdentity identity, int resourceid) {
        log.debug("Read on Device Resource " + resourceid);

        ModbusConfig.Resource resource = config.get(String.valueOf(resourceid));

        try {
            switch (resource.type) {
                case HOLDING_REGISTER:
                case INPUT_REGISTER: {
                    int[] response = readRegister(resource.type, slave, resource.startAddress, resource.quantity);

                    return resource.quantity == 0 ?
                            ReadResponse.success(resourceid, response[0]) :
                            ReadResponse.success(resourceid, Utils.asMapInteger(response), ResourceModel.Type.INTEGER);
                }
                case COIL:
                case DISCRETE_INPUT: {
                    boolean[] response = readBoolean(resource.type, slave, resource.startAddress, resource.quantity);

                    return resource.quantity == 0 ?
                            ReadResponse.success(resourceid, response[0]) :
                            ReadResponse.success(resourceid, Utils.asMapBoolean(response), ResourceModel.Type.BOOLEAN);
                }

                default:
                    return super.read(identity, resourceid);
            }
        } catch (Exception e) {
            log.error("an error occurred during read()", e);

            return ReadResponse.internalServerError(e.getMessage());
        }
    }

    @Override
    public WriteResponse write(ServerIdentity identity, int resourceid, LwM2mResource value) {
        log.debug("Write on Device Resource " + resourceid + " value " + value);

        ModbusConfig.Resource resource = config.get(String.valueOf(resourceid));

        try {
            switch (resource.type) {
                case HOLDING_REGISTER:
                    if (value.isMultiInstances()) {
                        master.writeMultipleRegisters(slave, resource.startAddress, Utils.asArrInteger(value.getValues()));
                    } else {
                        master.writeSingleRegister(slave, resource.startAddress, (int) (long) value.getValue());
                    }

                    fireResourcesChange(resourceid);

                    return WriteResponse.success();

                case COIL:
                    if (value.isMultiInstances()) {
                        master.writeMultipleCoils(slave, resource.startAddress, Utils.asArrBoolean(value.getValues()));
                    } else {
                        master.writeSingleCoil(slave, resource.startAddress, (boolean) value.getValue());
                    }

                    fireResourcesChange(resourceid);

                    return WriteResponse.success();

                default:
                    return super.write(identity, resourceid, value);
            }
        } catch (Exception e) {
            log.error("error occured during write()", e);

            return WriteResponse.internalServerError(e.getMessage());
        }
    }

    //-------ModBus-------
    private int[] readRegister(String type, int slave, int startAddress, int quantity) throws Exception {
        // revert to 1 if no existence
        quantity = (quantity == 0 ? 1 : quantity);

        if (type.equals(HOLDING_REGISTER)) {
            return master.readHoldingRegisters(slave, startAddress, quantity);
        } else if (type.equals(INPUT_REGISTER)) {
            return master.readInputRegisters(slave, startAddress, quantity);
        }

        throw new IllegalStateException("readRegister() - unknown type requested");
    }

    private boolean[] readBoolean(String type, int slave, int startAddress, int quantity) throws Exception {
        // revert to 1 if no existance
        quantity = (quantity == 0 ? 1 : quantity);

        if (type.equals(COIL)) {
            return master.readCoils(slave, startAddress, quantity);
        } else if (type.equals(DISCRETE_INPUT)) {
            return master.readDiscreteInputs(slave, startAddress, quantity);
        }

        throw new IllegalStateException("readBoolean() - unknown type requested");
    }
}

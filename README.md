# leshan-client-modbus

An example client that does a protocol translation between [Modbus](https://en.wikipedia.org/wiki/Modbus) and [Lightweight M2M](https://en.wikipedia.org/wiki/OMA_LWM2M). The client is drived by a configuration file that describes the Modbus/LWM2M Mapping and it can be made to suite specific requirements. Underlying it uses the active [jlibmodbus](https://github.com/kochedykov/jlibmodbus) open source library for the Modbus communication. 

> NOTE: Currently the state of the project is "in-preview" mode, don't expect to be production grade. We have only tested the Modbus TCP mode and it seems it works correctly. Much more testing is needed and feedback from the community will be greatly appreciated.

## Setup

1. Download a Modbus simulator and start it. We used the open source [ModbusPal simulator](https://sourceforge.net/projects/modbuspal/files/) but it should work with your favourite one.

	![ModbusPal](http://image.ibb.co/efwdTS/modbuspal.png)

2. Create a `modbus.json` configuration that describes the LWM2M object id to Modbus translation (start from [example configuration](https://github.com/zeelos/leshan-client-modbus/blob/master/src/main/resources/modbus.json)): Here is a snapshot for Modbus TCP mapping:

	``` javascript
	{
	  "connection": "tcp",
	  "slave": 1,
	  "tcpSettings": {
	    "node": "localhost",
	    "port": 5002,
	    "keepalive": true
	  },
	  "objects": {
	    "26241": [
	      {
	        "26251": {
	          "type": "holding-register",
	          "startAddress": 0,
	          "pollMillis": 1000
	        },
	        "26253": {
	          "type": "coil",
	          "startAddress": 0
	        },
	        "26255": {
	          "type": "holding-register",
	          "startAddress": 0,
	          "quantity": 10
	        }
	      },
	      {
	        "26251": {
	          "type": "holding-register",
	          "startAddress": 1
	        },
	        "26253": {
	          "type": "coil",
	          "startAddress": 1
	        }
	      }
	    ],
	    "3303": [
	      {
	        "5700": {
	          "type": "holding-register",
	          "startAddress": 0
	        }
	      }
	    ]
	  }
	}
	```

	Here we used both a custom developed [ObjectID;26241](https://github.com/zeelos/leshan-client-modbus/blob/master/src/main/resources/models/26241.xml) to map the whole Modbus spectrum functionality, as well as an existing [ObjectID;3303](http://www.openmobilealliance.org/tech/profiles/lwm2m/3303.xml) where we map a specific resource (5700) to a Modbus holding register. More information on LWM2M Object id's can be found on [OMA page](http://www.openmobilealliance.org/wp/OMNA/LwM2M/LwM2MRegistry.html).

3. Start the client passing both the configuration and the remote LWM2M server hostname:
	> Note that we bind the configuration file inside docker and pass the appropriate parameter.

		docker run -it --name leshan-client-modbus --mount type=bind,source="$(pwd)"/modbus.json,target=/modbus.json --rm -e JAVA_OPTS="-Xmx32M -Xms32M" zeelos/leshan-client-modbus:0.1-SNAPSHOT -t /modbus.json -u <leshan-server-hostname>

4. Visit the [Leshan](https://www.eclipse.org/leshan/) web interface and notice the sensor been registered and the exposed object id's available to use. Execute `Read/Write` operations on resources and notice any changes being reflected back to the Modbus simulator:

	![leshan_server_modbus](http://image.ibb.co/dEj1F7/leshan_server_modbus.png)
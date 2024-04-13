enum class Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST,
    UP,
    DOWN
}

data class Model(val name: String, val type: String) {
    companion object {
        fun fromString(model: String): Model {
            val (name, type) = model.split(";").map { it.trim() }
            return Model(name, type)
        }
    }
}

data class Net(val name: String, val source: String, val distination: String) {
    companion object {
        fun fromString(net: String): Net {
            val (name, connections) = net.split(";").map { it.trim() }
            val (source, distination) = connections.split(" ")
            return Net(name, source, distination)
        }
    }
}

data class Netlist(
        val inputs: List<Direction>,
        val outputs: List<Direction>,
        val models: List<Model>,
        val nets: List<Net>
) {
    companion object {
        fun fromString(netlist: String): Netlist {
            var isNetlist = false
            var isInputs = false
            var isOutputs = false
            var isModels = false
            var isNets = false
            var isEnd = false
            val inputs = mutableListOf<Direction>()
            val outputs = mutableListOf<Direction>()
            val models = mutableListOf<Model>()
            val nets = mutableListOf<Net>()
            netlist.lineSequence().forEach {
                if (!isNetlist || isEnd) {
                    if (it == "NETLIST") {
                        isNetlist = true
                    }
                    return@forEach
                } else if (it == "\$INPUTS") {
                    isInputs = true
                    return@forEach
                } else if (it == "\$OUTPUTS") {
                    isInputs = false
                    isOutputs = true
                    return@forEach
                } else if (it == "\$MODELS") {
                    isOutputs = false
                    isModels = true
                    return@forEach
                } else if (it == "\$NETS") {
                    isModels = false
                    isNets = true
                    return@forEach
                } else if (it == "\$END") {
                    isNets = false
                    isEnd = true
                    return@forEach
                } else if (isInputs) {
                    inputs.add(Direction.valueOf(it))
                } else if (isOutputs) {
                    outputs.add(Direction.valueOf(it))
                } else if (isModels) {
                    models.add(Model.fromString(it))
                } else if (isNets) {
                    nets.add(Net.fromString(it))
                }
            }
            return Netlist(inputs, outputs, models, nets)
        }
    }
}

enum class Voltage(val value: Int) {
    HIGH(14),
    UNDEFINED(8),
    LOW(2)
}

interface Element {
    fun getOutputs(inputs: Map<String, Voltage>): Map<String, Voltage>
}

class AndGate(val input1: String, val input2: String, val output: String) : Element {
    override fun getOutputs(inputs: Map<String, Voltage>): Map<String, Voltage> {
        val input1Voltage = inputs[input1] ?: Voltage.UNDEFINED
        val input2Voltage = inputs[input2] ?: Voltage.UNDEFINED
        val outputVoltage =
                if (input1Voltage == Voltage.HIGH && input2Voltage == Voltage.HIGH) {
                    Voltage.HIGH
                } else if (input1Voltage == Voltage.UNDEFINED || input2Voltage == Voltage.UNDEFINED
                ) {
                    Voltage.UNDEFINED
                } else {
                    Voltage.LOW
                }
        return mapOf(output to outputVoltage)
    }
}

class XorGate(val input1: String, val input2: String, val output: String) : Element {
    override fun getOutputs(inputs: Map<String, Voltage>): Map<String, Voltage> {
        val input1Voltage = inputs[input1] ?: Voltage.UNDEFINED
        val input2Voltage = inputs[input2] ?: Voltage.UNDEFINED
        val outputVoltage =
                if (input1Voltage == Voltage.HIGH && input2Voltage == Voltage.LOW) {
                    Voltage.HIGH
                } else if (input1Voltage == Voltage.LOW && input2Voltage == Voltage.HIGH) {
                    Voltage.HIGH
                } else if (input1Voltage == Voltage.UNDEFINED || input2Voltage == Voltage.UNDEFINED
                ) {
                    Voltage.UNDEFINED
                } else {
                    Voltage.LOW
                }
        return mapOf(output to outputVoltage)
    }
}

class OrGate(val input1: String, val input2: String, val output: String) : Element {
    override fun getOutputs(inputs: Map<String, Voltage>): Map<String, Voltage> {
        val input1Voltage = inputs[input1] ?: Voltage.UNDEFINED
        val input2Voltage = inputs[input2] ?: Voltage.UNDEFINED
        val outputVoltage =
                if (input1Voltage == Voltage.HIGH || input2Voltage == Voltage.HIGH) {
                    Voltage.HIGH
                } else if (input1Voltage == Voltage.UNDEFINED || input2Voltage == Voltage.UNDEFINED
                ) {
                    Voltage.UNDEFINED
                } else {
                    Voltage.LOW
                }
        return mapOf(output to outputVoltage)
    }
}

class NorGate(val input1: String, val input2: String, val output: String) : Element {
    override fun getOutputs(inputs: Map<String, Voltage>): Map<String, Voltage> {
        val input1Voltage = inputs[input1] ?: Voltage.UNDEFINED
        val input2Voltage = inputs[input2] ?: Voltage.UNDEFINED
        val outputVoltage =
                if (input1Voltage == Voltage.LOW && input2Voltage == Voltage.LOW) {
                    Voltage.HIGH
                } else if (input1Voltage == Voltage.LOW && input2Voltage == Voltage.UNDEFINED) {
                    Voltage.UNDEFINED
                } else if (input1Voltage == Voltage.UNDEFINED && input2Voltage == Voltage.LOW) {
                    Voltage.UNDEFINED
                } else {
                    Voltage.LOW
                }
        return mapOf(output to outputVoltage)
    }
}

class MinecraftFpgaBlock(netlist: Netlist, limits: Int) : Element {
    val netlist: Netlist
    val elements: Map<String, Element>
    val limits: Int
    var pins = mutableMapOf<String, Voltage>()

    init {
        this.netlist = netlist
        this.elements = generateElements()
        this.limits = limits
        netlist.nets.forEach {
            pins[it.source] = Voltage.UNDEFINED
            pins[it.distination] = Voltage.UNDEFINED
        }
    }

    protected fun generateElements(): Map<String, Element> {
        val elements = mutableMapOf<String, Element>()
        netlist.models.forEach {
            val (name, type) = it
            when (type) {
                "AND" -> {
                    elements[name] = AndGate("${name}.0", "${name}.1", "${name}.2")
                }
                "XOR" -> {
                    elements[name] = XorGate("${name}.0", "${name}.1", "${name}.2")
                }
                "OR" -> {
                    elements[name] = OrGate("${name}.0", "${name}.1", "${name}.2")
                }
                "NOR" -> {
                    elements[name] = NorGate("${name}.0", "${name}.1", "${name}.2")
                }
            }
        }
        return elements
    }

    override fun getOutputs(inputs: Map<String, Voltage>): Map<String, Voltage> {
        inputs.forEach { pins[it.key] = it.value }
        // while (netlist.outputs.any { pins[it.toString()] == Voltage.UNDEFINED }) {
        repeat(limits) {
            elements.forEach { it.value.getOutputs(pins).forEach { pins[it.key] = it.value } }
            netlist.nets.forEach { pins[it.distination] = pins[it.source]!! }
            println("Pins: $pins")
        }
        return pins.filter { netlist.outputs.map { it.toString() }.contains(it.key) }
    }
}

fun testAndGate() {
    val doller = "$"
    val netlist =
            """
            NETLIST
            ${doller}INPUTS
            NORTH
            SOUTH
            ${doller}OUTPUTS
            UP
            ${doller}MODELS
            AND1; AND
            ${doller}NETS
            NET1; NORTH AND1.0
            NET2; SOUTH AND1.1
            NET3; AND1.2 UP
            ${doller}END
            """.trimIndent()
    val minecraftFpgaBlock = MinecraftFpgaBlock(Netlist.fromString(netlist), 5)
    listOf(
                    mapOf("NORTH" to Voltage.LOW, "SOUTH" to Voltage.LOW),
                    mapOf("NORTH" to Voltage.LOW, "SOUTH" to Voltage.HIGH),
                    mapOf("NORTH" to Voltage.HIGH, "SOUTH" to Voltage.LOW),
                    mapOf("NORTH" to Voltage.HIGH, "SOUTH" to Voltage.HIGH)
            )
            .forEach {
                println("\nTest And Gate")
                println("Inputs: $it")
                val outputs = minecraftFpgaBlock.getOutputs(it)
                println("Output: $outputs")
            }
}

fun testFullAdder() {
    val doller = "$"
    val netlist =
            """
            NETLIST
            ${doller}INPUTS
            NORTH
            SOUTH
            WEST
            ${doller}OUTPUTS
            EAST
            UP
            ${doller}MODELS
            XOR1; XOR
            XOR2; XOR
            AND1; AND
            AND2; AND
            OR1; OR
            ${doller}NETS
            NET1; NORTH XOR1.0
            NET2; SOUTH XOR1.1
            NET3; XOR1.2 XOR2.0
            NET4; WEST XOR2.1
            NET5; XOR2.2 EAST
            NET6; XOR1.2 AND1.0
            NET7; WEST AND1.1
            NET8; NORTH AND2.0
            NET9; SOUTH AND2.1
            NET10; AND1.2 OR1.0
            NET11; AND2.2 OR1.1
            NET12; OR1.2 UP
            ${doller}END
            """.trimIndent()
    val minecraftFpgaBlock = MinecraftFpgaBlock(Netlist.fromString(netlist), 5)
    listOf(
                    mapOf("NORTH" to Voltage.LOW, "SOUTH" to Voltage.LOW, "WEST" to Voltage.LOW),
                    mapOf("NORTH" to Voltage.LOW, "SOUTH" to Voltage.LOW, "WEST" to Voltage.HIGH),
                    mapOf("NORTH" to Voltage.LOW, "SOUTH" to Voltage.HIGH, "WEST" to Voltage.LOW),
                    mapOf("NORTH" to Voltage.LOW, "SOUTH" to Voltage.HIGH, "WEST" to Voltage.HIGH),
                    mapOf("NORTH" to Voltage.HIGH, "SOUTH" to Voltage.LOW, "WEST" to Voltage.LOW),
                    mapOf("NORTH" to Voltage.HIGH, "SOUTH" to Voltage.LOW, "WEST" to Voltage.HIGH),
                    mapOf("NORTH" to Voltage.HIGH, "SOUTH" to Voltage.HIGH, "WEST" to Voltage.LOW),
                    mapOf("NORTH" to Voltage.HIGH, "SOUTH" to Voltage.HIGH, "WEST" to Voltage.HIGH)
            )
            .forEach {
                println("\nTest Full Adder")
                println("Inputs: $it")
                val outputs = minecraftFpgaBlock.getOutputs(it)
                println("Output: $outputs")
            }
}

fun testRsff() {
    val doller = "$"
    val netlist =
            """
            NETLIST
            ${doller}INPUTS
            SOUTH
            WEST
            ${doller}OUTPUTS
            NORTH
            EAST
            ${doller}MODELS
            NOR1; NOR
            NOR2; NOR
            ${doller}NETS
            NET1; SOUTH NOR1.0
            NET2; WEST NOR2.1
            NET3; NOR1.2 NOR2.0
            NET4; NOR2.2 NOR1.1
            NET5; NOR1.2 NORTH
            NET6; NOR2.2 EAST
            """.trimIndent()
    val minecraftFpgaBlock = MinecraftFpgaBlock(Netlist.fromString(netlist), 5)
    listOf(
                    mapOf("SOUTH" to Voltage.HIGH, "WEST" to Voltage.LOW),
                    mapOf("SOUTH" to Voltage.LOW, "WEST" to Voltage.LOW),
                    mapOf("SOUTH" to Voltage.LOW, "WEST" to Voltage.HIGH),
                    mapOf("SOUTH" to Voltage.LOW, "WEST" to Voltage.LOW),
            )
            .forEach {
                println("\nTest RS-FF")
                println("Inputs: $it")
                val outputs = minecraftFpgaBlock.getOutputs(it)
                println("Output: $outputs")
            }
}

fun main() {
    testAndGate()
    testFullAdder()
    testRsff()
}

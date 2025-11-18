package ByteCode;

public class Debug {
    // Métod principal para desmontar tod o Chunk
    public static void disassembleChunk(Chunk chunk, String name) {
        System.out.println("== " + name + " ==");

        // Percorre todas as instruções
        for (int offset = 0; offset < chunk.code.size();) {
            // disassembleInstruction retorna o novo offset (pula os operandos se houver)
            offset = disassembleInstruction(chunk, offset);
        }
    }

    // Desmonta uma única instrução
    public static int disassembleInstruction(Chunk chunk, int offset) {
        System.out.printf("%04d ", offset); // Imprime o índice (ex: 0000, 0001)

        // Imprime a linha do código fonte (ou | se for a mesma da anterior)
        if (offset > 0 && chunk.lines.get(offset).equals(chunk.lines.get(offset - 1))) {
            System.out.print("   | ");
        } else {
            System.out.printf("%4d ", chunk.lines.get(offset));
        }

        int instruction = chunk.code.get(offset);
        if (instruction >= OpCode.values().length) {
            System.out.println("Opcode desconhecido " + instruction);
            return offset + 1;
        }
        OpCode op = OpCode.values()[instruction];

        return switch (op) {
            case OP_RETURN, OP_POP, OP_NIL, OP_TRUE, OP_FALSE, OP_NEGATE, OP_ADD, OP_SUBTRACT, OP_MULTIPLY, OP_DIVIDE,
                 OP_NOT, OP_EQUAL, OP_GREATER, OP_LESS, OP_PRINT, OP_INPUT -> simpleInstruction(op, offset);
            case OP_CONSTANT, OP_DEFINE_GLOBAL, OP_GET_GLOBAL, OP_SET_GLOBAL -> constantInstruction(op, chunk, offset);
            case OP_JUMP, OP_JUMP_IF_FALSE -> jumpInstruction(op, 1, chunk, offset);
            case OP_LOOP -> jumpInstruction(op, -1, chunk, offset);
            default -> {
                System.out.println("Opcode desconhecido " + op);
                yield offset + 1;
            }
        };
    }

    // Instruções simples (apenas 1 byte, sem argumentos)
    private static int simpleInstruction(OpCode op, int offset) {
        System.out.println(op);
        return offset + 1;
    }

    // Instruções com constantes (Opcode + Índice da Constante)
    private static int constantInstruction(OpCode op, Chunk chunk, int offset) {
        int constantIndex = chunk.code.get(offset + 1);
        System.out.printf("%-16s %4d '", op, constantIndex);
        System.out.print(chunk.constants.get(constantIndex));
        System.out.println("'");
        return offset + 2;
    }

    // Instruções de pulo (Opcode + 2 bytes de offset)
    private static int jumpInstruction(OpCode op, int sign, Chunk chunk, int offset) {
        int jump = (chunk.code.get(offset + 1) << 8) | chunk.code.get(offset + 2);
        System.out.printf("%-16s %4d -> %d\n", op, offset,
                offset + 3 + sign * jump);
        return offset + 3;
    }
}
package ByteCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.Scanner;

public class VM {

    private Chunk chunk;
    private int ip; // Instruction Pointer
    private Stack<Object> stack;
    private Map<String, Object> globals;
    private final Scanner consoleInput;

    public VM() {
        this.stack = new Stack<>();
        this.globals = new HashMap<>();
        this.consoleInput = new Scanner(System.in);
    }

    // Metodo principal de execução
    public boolean interpret(Chunk chunk) {
        this.chunk = chunk;
        this.ip = 0;

        try {
            while (true) {
                if (ip >= chunk.code.size()) return true;

                int instruction = chunk.code.get(ip++);
                OpCode op = OpCode.values()[instruction];

                switch (op) {
                    case OP_RETURN: {
                        System.out.println("VM: Execução finalizada.");
                        return true;
                    }

                    // CORREÇÃO: Implementação do OP_CONSTANT que faltava em versões antigas
                    case OP_CONSTANT: {
                        int constIndex = chunk.code.get(ip++);
                        Object constant = chunk.constants.get(constIndex);
                        stack.push(constant);
                        break;
                    }

                    case OP_POP: {
                        if (!stack.isEmpty()) stack.pop();
                        break;
                    }

                    // --- Tipos Literais ---
                    case OP_NIL:   stack.push(null); break;
                    case OP_TRUE:  stack.push(true); break;
                    case OP_FALSE: stack.push(false); break;

                    // --- Aritmética Unária ---
                    case OP_NEGATE: {
                        Object value = stack.pop();
                        if (value instanceof Double) stack.push(-(Double) value);
                        else if (value instanceof Integer) stack.push(-(Integer) value);
                        else runtimeError("Operando deve ser um número.");
                        break;
                    }

                    // --- Aritmética Binária ---
                    case OP_ADD:      binaryOp("+"); break;
                    case OP_SUBTRACT: binaryOp("-"); break;
                    case OP_MULTIPLY: binaryOp("*"); break;
                    case OP_DIVIDE:   binaryOp("/"); break;

                    // --- Lógica e Comparação ---
                    case OP_NOT: stack.push(!isTruthy(stack.pop())); break;
                    case OP_EQUAL: {
                        Object b = stack.pop();
                        Object a = stack.pop();
                        stack.push(isEqual(a, b));
                        break;
                    }
                    case OP_GREATER: binaryOp(">"); break;
                    case OP_LESS:    binaryOp("<"); break;

                    // --- Variáveis ---
                    case OP_DEFINE_GLOBAL: {
                        int constIndex = chunk.code.get(ip++);
                        String name = (String) chunk.constants.get(constIndex);
                        globals.put(name, stack.pop());
                        break;
                    }
                    case OP_GET_GLOBAL: {
                        int constIndex = chunk.code.get(ip++);
                        String name = (String) chunk.constants.get(constIndex);
                        if (!globals.containsKey(name)) {
                            runtimeError("Variável indefinida '" + name + "'.");
                            return false;
                        }
                        stack.push(globals.get(name));
                        break;
                    }
                    case OP_SET_GLOBAL: {
                        int constIndex = chunk.code.get(ip++);
                        String name = (String) chunk.constants.get(constIndex);
                        if (!globals.containsKey(name)) {
                            runtimeError("Variável indefinida '" + name + "'.");
                            return false;
                        }
                        // OP_SET mantém o valor na pilha para permitir atribuições encadeadas (a = b = 1)
                        globals.put(name, stack.peek());
                        break;
                    }

                    // --- Entrada e Saída ---
                    case OP_PRINT: {
                        System.out.println(stringify(stack.pop()));
                        break;
                    }

                    // CORREÇÃO: Implementação do OP_INPUT (LEIA)
                    case OP_INPUT: {
                        System.out.print("> "); // Prompt
                        String line = consoleInput.nextLine();
                        Object val;
                        // Tenta converter para Inteiro ou Double, senão String
                        try {
                            val = Integer.parseInt(line);
                        } catch (NumberFormatException e1) {
                            try {
                                val = Double.parseDouble(line);
                            } catch (NumberFormatException e2) {
                                val = line;
                            }
                        }
                        stack.push(val);
                        break;
                    }

                    // --- Controle de Fluxo ---
                    case OP_JUMP_IF_FALSE: {
                        int offset = readShort();
                        if (!isTruthy(stack.peek())) {
                            ip += offset;
                        }
                        break;
                    }
                    case OP_JUMP: {
                        int offset = readShort();
                        ip += offset;
                        break;
                    }
                    case OP_LOOP: {
                        int offset = readShort();
                        ip -= offset;
                        break;
                    }

                    default:
                        runtimeError("Opcode desconhecido: " + op);
                        return false;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro fatal na VM: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // --- Auxiliares ---

    private void binaryOp(String op) {
        Object b = stack.pop();
        Object a = stack.pop();

        if (op.equals("+") && (a instanceof String || b instanceof String)) {
            stack.push(stringify(a) + stringify(b));
            return;
        }

        if (!(a instanceof Number) || !(b instanceof Number)) {
            runtimeError("Operandos inválidos para " + op);
            return;
        }

        // Opera com Double se houver algum float, senão Integer
        if (a instanceof Double || b instanceof Double) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            switch (op) {
                case "+": stack.push(da + db); break;
                case "-": stack.push(da - db); break;
                case "*": stack.push(da * db); break;
                case "/": stack.push(da / db); break;
                case ">": stack.push(da > db); break;
                case "<": stack.push(da < db); break;
            }
        } else {
            int ia = (int) a;
            int ib = (int) b;
            switch (op) {
                case "+": stack.push(ia + ib); break;
                case "-": stack.push(ia - ib); break;
                case "*": stack.push(ia * ib); break;
                case "/": stack.push(ia / ib); break;
                case ">": stack.push(ia > ib); break;
                case "<": stack.push(ia < ib); break;
            }
        }
    }

    private int readShort() {
        int high = chunk.code.get(ip++);
        int low = chunk.code.get(ip++);
        return (high << 8) | low;
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nulo";
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) return text.substring(0, text.length() - 2);
        }
        if (object instanceof Boolean) return (boolean) object ? "verdadeiro" : "falso";
        return object.toString();
    }

    private void runtimeError(String message) {
        int line = chunk.lines.get(ip - 1);
        System.err.println(message + " [linha " + line + "]");
    }
}